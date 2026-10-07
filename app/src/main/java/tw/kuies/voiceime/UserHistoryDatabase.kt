package tw.kuies.voiceime

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import java.util.UUID

@Entity(
    tableName = "clipboard_history",
    indices = [Index(value = ["createdAt"])]
)
internal data class ClipboardHistoryRow(
    @PrimaryKey val id: String,
    val text: String,
    val createdAt: Long,
    val pinned: Boolean
) {
    fun toEntity() = ClipboardHistoryEntity(id, text, createdAt, pinned)

    companion object {
        fun from(entity: ClipboardHistoryEntity) = ClipboardHistoryRow(
            entity.id,
            entity.text,
            entity.createdAt,
            entity.pinned
        )
    }
}

@Entity(
    tableName = "voice_history",
    indices = [Index(value = ["createdAt"])]
)
internal data class VoiceHistoryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val rawText: String,
    val finalText: String
) {
    fun toEntity() = VoiceHistoryEntity(id, createdAt, rawText, finalText)
}

@Dao
internal interface ClipboardHistoryDao {
    @Query("SELECT * FROM clipboard_history ORDER BY createdAt DESC, rowid DESC")
    fun getNewestFirst(): List<ClipboardHistoryRow>

    @Query("SELECT * FROM clipboard_history WHERE text = :text ORDER BY createdAt DESC, rowid DESC LIMIT 1")
    fun findByText(text: String): ClipboardHistoryRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(row: ClipboardHistoryRow)

    @Update
    fun update(row: ClipboardHistoryRow)

    @Query("UPDATE clipboard_history SET pinned = :pinned WHERE id = :id")
    fun setPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM clipboard_history WHERE id = :id")
    fun deleteById(id: String)

    @Query("DELETE FROM clipboard_history WHERE pinned = 0")
    fun clearUnpinned()

    @Query(
        "DELETE FROM clipboard_history WHERE pinned = 0 AND id NOT IN " +
            "(SELECT id FROM clipboard_history WHERE pinned = 0 " +
            "ORDER BY createdAt DESC, rowid DESC LIMIT :maxItems)"
    )
    fun trimUnpinned(maxItems: Int)
}

@Dao
internal interface VoiceHistoryDao {
    @Query("SELECT * FROM voice_history ORDER BY createdAt DESC, id DESC")
    fun getNewestFirst(): List<VoiceHistoryRow>

    @Insert
    fun insert(row: VoiceHistoryRow): Long

    @Query("DELETE FROM voice_history WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM voice_history")
    fun clearAll()

    @Query(
        "DELETE FROM voice_history WHERE id NOT IN " +
            "(SELECT id FROM voice_history ORDER BY createdAt DESC, id DESC LIMIT :maxItems)"
    )
    fun trim(maxItems: Int)
}

@Database(
    entities = [ClipboardHistoryRow::class, VoiceHistoryRow::class],
    version = 1,
    exportSchema = false
)
internal abstract class UserHistoryDatabase : RoomDatabase() {
    abstract fun clipboardHistoryDao(): ClipboardHistoryDao
    abstract fun voiceHistoryDao(): VoiceHistoryDao

    companion object {
        const val DATABASE_NAME = "kuiesvox_history.db"

        @Volatile
        private var instance: UserHistoryDatabase? = null

        fun get(context: Context): UserHistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                UserHistoryDatabase::class.java,
                DATABASE_NAME
            ).build().also { instance = it }
        }
    }
}

internal class ClipboardHistoryManager(
    private val dao: ClipboardHistoryDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    fun list(): List<ClipboardHistoryEntity> = dao.getNewestFirst().map(ClipboardHistoryRow::toEntity)

    fun record(text: String, isSensitiveEditor: Boolean): Boolean {
        if (!ClipboardHistoryPolicy.canStore(text, isSensitiveEditor)) return false

        val timestamp = now()
        val existing = dao.findByText(text)
        if (existing == null) {
            dao.insertOrReplace(ClipboardHistoryRow(newId(), text, timestamp, pinned = false))
        } else {
            dao.update(existing.copy(createdAt = timestamp))
        }
        dao.trimUnpinned(ClipboardHistoryPolicy.MAX_UNPINNED_ITEMS)
        return true
    }

    fun setPinned(id: String, pinned: Boolean) {
        dao.setPinned(id, pinned)
        dao.trimUnpinned(ClipboardHistoryPolicy.MAX_UNPINNED_ITEMS)
    }

    fun delete(id: String) = dao.deleteById(id)

    fun clearUnpinned() = dao.clearUnpinned()
}

internal class VoiceHistoryManager(
    private val dao: VoiceHistoryDao,
    private val now: () -> Long = System::currentTimeMillis
) {
    fun list(): List<VoiceHistoryEntity> = dao.getNewestFirst().map(VoiceHistoryRow::toEntity)

    fun record(
        rawText: String,
        finalText: String,
        successfulCommit: Boolean,
        cancelled: Boolean,
        isSensitiveEditor: Boolean
    ): Boolean {
        if (!VoiceHistoryPolicy.canStore(
                rawText,
                finalText,
                successfulCommit,
                cancelled,
                isSensitiveEditor
            )
        ) return false

        dao.insert(VoiceHistoryRow(createdAt = now(), rawText = rawText, finalText = finalText))
        dao.trim(VoiceHistoryPolicy.MAX_ITEMS)
        return true
    }

    fun delete(id: Long) = dao.deleteById(id)

    fun clearAll() = dao.clearAll()
}

internal object UserHistoryRepository {
    fun loadClipboardAsync(context: Context, callback: (Result<List<ClipboardHistoryEntity>>) -> Unit) {
        AppStorageExecutor.submit({
            ClipboardHistoryManager(UserHistoryDatabase.get(context).clipboardHistoryDao()).list()
        }, callback)
    }

    fun recordClipboardAsync(
        context: Context,
        text: String,
        isSensitiveEditor: Boolean,
        callback: (Result<Boolean>) -> Unit = {}
    ) {
        if (!ClipboardHistoryPolicy.canStore(text, isSensitiveEditor)) {
            callback(Result.success(false))
            return
        }
        AppStorageExecutor.submit({
            ClipboardHistoryManager(UserHistoryDatabase.get(context).clipboardHistoryDao())
                .record(text, isSensitiveEditor)
        }, callback)
    }

    fun setClipboardPinnedAsync(
        context: Context,
        id: String,
        pinned: Boolean,
        callback: (Result<Unit>) -> Unit = {}
    ) = AppStorageExecutor.submit({
        ClipboardHistoryManager(UserHistoryDatabase.get(context).clipboardHistoryDao())
            .setPinned(id, pinned)
    }, callback)

    fun deleteClipboardAsync(
        context: Context,
        id: String,
        callback: (Result<Unit>) -> Unit = {}
    ) = AppStorageExecutor.submit({
        ClipboardHistoryManager(UserHistoryDatabase.get(context).clipboardHistoryDao()).delete(id)
    }, callback)

    fun clearUnpinnedClipboardAsync(
        context: Context,
        callback: (Result<Unit>) -> Unit = {}
    ) = AppStorageExecutor.submit({
        ClipboardHistoryManager(UserHistoryDatabase.get(context).clipboardHistoryDao()).clearUnpinned()
    }, callback)

    fun loadVoiceAsync(context: Context, callback: (Result<List<VoiceHistoryEntity>>) -> Unit) {
        AppStorageExecutor.submit({
            VoiceHistoryManager(UserHistoryDatabase.get(context).voiceHistoryDao()).list()
        }, callback)
    }

    fun recordVoiceAsync(
        context: Context,
        rawText: String,
        finalText: String,
        successfulCommit: Boolean,
        cancelled: Boolean,
        isSensitiveEditor: Boolean,
        callback: (Result<Boolean>) -> Unit = {}
    ) {
        if (!VoiceHistoryPolicy.canStore(
                rawText,
                finalText,
                successfulCommit,
                cancelled,
                isSensitiveEditor
            )
        ) {
            callback(Result.success(false))
            return
        }
        AppStorageExecutor.submit({
            VoiceHistoryManager(UserHistoryDatabase.get(context).voiceHistoryDao())
                .record(rawText, finalText, successfulCommit, cancelled, isSensitiveEditor)
        }, callback)
    }

    fun deleteVoiceAsync(
        context: Context,
        id: Long,
        callback: (Result<Unit>) -> Unit = {}
    ) = AppStorageExecutor.submit({
        VoiceHistoryManager(UserHistoryDatabase.get(context).voiceHistoryDao()).delete(id)
    }, callback)

    fun clearVoiceAsync(context: Context, callback: (Result<Unit>) -> Unit = {}) =
        AppStorageExecutor.submit({
            VoiceHistoryManager(UserHistoryDatabase.get(context).voiceHistoryDao()).clearAll()
        }, callback)
}
