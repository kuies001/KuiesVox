package tw.kuies.voiceime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SavedSnippetsScreen(
    modifier: Modifier = Modifier,
    library: SavedSnippetLibrary,
    status: String,
    launchRequestId: Long?,
    launchAction: SavedSnippetManagerAction?,
    launchSnippetId: String?,
    onSaveSnippet: (String?, String, String, String) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    onDeleteSnippet: (String) -> Unit,
    onClearSnippets: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SavedSnippetSort.ALL) }
    var createSnippet by remember { mutableStateOf(false) }
    var editingSnippet by remember { mutableStateOf<SavedSnippet?>(null) }
    var pendingSnippetId by remember { mutableStateOf<String?>(null) }
    var snippetToDelete by remember { mutableStateOf<SavedSnippet?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    LaunchedEffect(launchRequestId) {
        when (launchAction) {
            SavedSnippetManagerAction.NEW -> createSnippet = true
            SavedSnippetManagerAction.EDIT -> pendingSnippetId = launchSnippetId
            null -> Unit
        }
    }
    LaunchedEffect(pendingSnippetId, library.snippets) {
        val id = pendingSnippetId ?: return@LaunchedEffect
        val snippet = library.snippets.firstOrNull { it.id == id } ?: return@LaunchedEffect
        editingSnippet = snippet
        pendingSnippetId = null
    }

    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("快捷短語", style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
            Spacer(Modifier.weight(1f))
        }
        Surface(
            color = colors.surfaceVariant,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                "短語只儲存在本機，請勿儲存機敏性資料。\n此功能建議儲存地址、Email 等較難以語音辨識的資料。",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text("搜尋短語") },
                singleLine = true
            )
            Button(onClick = { createSnippet = true }) { Text("新增") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SnippetSortButton(
                name = "全部",
                selected = sort == SavedSnippetSort.ALL,
                onClick = { sort = SavedSnippetSort.ALL }
            )
            SnippetSortButton(
                name = "最近使用",
                selected = sort == SavedSnippetSort.RECENT,
                onClick = { sort = SavedSnippetSort.RECENT }
            )
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        val visibleSnippets = SavedSnippetOrdering.filterAndSort(
            snippets = library.snippets,
            search = query,
            sort = sort
        )
        if (visibleSnippets.isEmpty() && status.isBlank()) {
            Text("沒有符合條件的短語。", color = colors.onSurfaceVariant)
        }
        visibleSnippets.forEach { snippet ->
            SavedSnippetSettingsCard(
                snippet = snippet,
                onTogglePinned = { onSetPinned(snippet.id, !snippet.pinned) },
                onEdit = { editingSnippet = snippet },
                onDelete = { snippetToDelete = snippet }
            )
        }
        if (library.snippets.isNotEmpty()) {
            TextButton(onClick = { confirmClearAll = true }, modifier = Modifier.align(Alignment.End)) {
                Text("清除全部短語")
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (createSnippet || editingSnippet != null) {
        SavedSnippetEditorDialog(
            snippet = editingSnippet,
            onDismiss = {
                createSnippet = false
                editingSnippet = null
            },
            onSave = { title, content ->
                onSaveSnippet(
                    editingSnippet?.id,
                    title,
                    content,
                    editingSnippet?.categoryId ?: SavedSnippetCategoryDefaults.GENERAL_ID
                )
                createSnippet = false
                editingSnippet = null
            }
        )
    }
    snippetToDelete?.let { snippet ->
        AlertDialog(
            onDismissRequest = { snippetToDelete = null },
            title = { Text("刪除短語？") },
            text = { Text("確定刪除「${snippet.title.take(40)}」嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSnippet(snippet.id)
                    snippetToDelete = null
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { snippetToDelete = null }) { Text("取消") } }
        )
    }
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清除全部短語？") },
            text = { Text("所有快捷短語都會刪除。此操作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearSnippets()
                    confirmClearAll = false
                }) { Text("清除全部") }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SavedSnippetSettingsCard(
    snippet: SavedSnippet,
    onTogglePinned: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(snippet.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                snippet.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onTogglePinned) { Text(if (snippet.pinned) "取消收藏" else "收藏") }
                TextButton(onClick = onEdit) { Text("編輯") }
                TextButton(onClick = onDelete) { Text("刪除") }
            }
        }
    }
}

@Composable
private fun SavedSnippetEditorDialog(
    snippet: SavedSnippet?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember(snippet?.id) { mutableStateOf(snippet?.title.orEmpty()) }
    var content by remember(snippet?.id) { mutableStateOf(snippet?.content.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (snippet == null) "新增快捷短語" else "編輯快捷短語") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("標題") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("要插入的完整文字") },
                    minLines = 4,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, content) },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SnippetSortButton(name: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(name) }
    } else {
        TextButton(onClick = onClick) { Text(name) }
    }
}
