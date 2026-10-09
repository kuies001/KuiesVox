package tw.kuies.voiceime

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedSnippetDatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(UserHistoryDatabase.DATABASE_NAME)
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(UserHistoryDatabase.DATABASE_NAME)
    }

    @Test
    fun versionOneHistoryIsPreservedAndSnippetTablesAreAdded() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(UserHistoryDatabase.DATABASE_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `clipboard_history` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE INDEX `index_clipboard_history_createdAt` ON `clipboard_history` (`createdAt`)")
                    db.execSQL(
                        "CREATE TABLE `voice_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, `rawText` TEXT NOT NULL, `finalText` TEXT NOT NULL)"
                    )
                    db.execSQL("CREATE INDEX `index_voice_history_createdAt` ON `voice_history` (`createdAt`)")
                    db.execSQL(
                        "INSERT INTO `clipboard_history` VALUES ('old-clip', 'kept', 10, 1)"
                    )
                    db.execSQL(
                        "INSERT INTO `voice_history` (`createdAt`, `rawText`, `finalText`) " +
                            "VALUES (11, 'raw', 'final')"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase.close()

        val database = Room.databaseBuilder(
            context,
            UserHistoryDatabase::class.java,
            UserHistoryDatabase.DATABASE_NAME
        ).addMigrations(UserHistoryDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals("old-clip", database.clipboardHistoryDao().getNewestFirst().single().id)
            assertEquals("raw", database.voiceHistoryDao().getNewestFirst().single().rawText)
            assertEquals(4, database.savedSnippetDao().getCategories().size)
            assertTrue(database.savedSnippetDao().getSnippets().isEmpty())
            database.savedSnippetDao().insertSnippet(
                SavedSnippetRow(
                    id = "migrated-db-check",
                    title = "移轉後可用",
                    content = "資料仍在",
                    categoryId = SavedSnippetCategoryDefaults.GENERAL_ID,
                    pinned = false,
                    createdAt = 12,
                    updatedAt = 12,
                    lastUsedAt = null
                )
            )
            assertEquals(1, database.savedSnippetDao().getSnippets().size)
        } finally {
            database.close()
        }
    }
}
