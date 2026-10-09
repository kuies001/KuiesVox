package tw.kuies.voiceime

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(SavedSnippetSort.RECENT) }
    var showCategories by remember { mutableStateOf(false) }
    var createSnippet by remember { mutableStateOf(false) }
    var editingSnippet by remember { mutableStateOf<SavedSnippet?>(null) }
    var pendingSnippetId by remember { mutableStateOf<String?>(null) }
    var snippetToDelete by remember { mutableStateOf<SavedSnippet?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    LaunchedEffect(launchRequestId) {
        when (launchAction) {
            SavedSnippetManagerAction.NEW -> createSnippet = true
            SavedSnippetManagerAction.EDIT -> pendingSnippetId = launchSnippetId
            SavedSnippetManagerAction.CATEGORIES -> showCategories = true
            null -> Unit
        }
    }
    LaunchedEffect(pendingSnippetId, library.snippets) {
        val id = pendingSnippetId ?: return@LaunchedEffect
        val snippet = library.snippets.firstOrNull { it.id == id } ?: return@LaunchedEffect
        editingSnippet = snippet
        pendingSnippetId = null
    }

    if (showCategories) {
        SavedSnippetCategoriesScreen(
            modifier = modifier,
            categories = library.categories,
            onCreateCategory = onCreateCategory,
            onRenameCategory = onRenameCategory,
            onDeleteCategory = onDeleteCategory,
            onBack = { showCategories = false }
        )
    } else {
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
                TextButton(onClick = { showCategories = true }) { Text("分類管理") }
            }
            Surface(
                color = colors.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "短語只儲存在本機。請勿收藏密碼、OTP 或 API Key；密碼與 PIN 欄位不會顯示短語。",
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
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryFilterButton(
                    name = "全部",
                    selected = selectedCategoryId == null,
                    onClick = { selectedCategoryId = null }
                )
                library.categories.forEach { category ->
                    CategoryFilterButton(
                        name = category.name,
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id }
                    )
                }
                TextButton(onClick = {
                    sort = if (sort == SavedSnippetSort.RECENT) SavedSnippetSort.TITLE else SavedSnippetSort.RECENT
                }) {
                    Text(if (sort == SavedSnippetSort.RECENT) "最近使用" else "名稱排序")
                }
            }
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            val visibleSnippets = SavedSnippetOrdering.filterAndSort(
                snippets = library.snippets,
                search = query,
                categoryId = selectedCategoryId,
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
    }

    if (createSnippet || editingSnippet != null) {
        SavedSnippetEditorDialog(
            snippet = editingSnippet,
            categories = library.categories,
            onDismiss = {
                createSnippet = false
                editingSnippet = null
            },
            onSave = { title, content, categoryId ->
                onSaveSnippet(editingSnippet?.id, title, content, categoryId)
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
            text = { Text("所有快捷短語都會刪除，分類會保留。此操作無法復原。") },
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
private fun SavedSnippetCategoriesScreen(
    modifier: Modifier,
    categories: List<SavedSnippetCategory>,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onBack: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<SavedSnippetCategory?>(null) }
    var categoryToDelete by remember { mutableStateOf<SavedSnippetCategory?>(null) }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回短語") }
            Text("分類管理", style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
            Spacer(Modifier.weight(1f))
            Button(onClick = { showCreateDialog = true }) { Text("新增分類") }
        }
        Text("刪除分類會保留短語，並移至「一般」。", color = colors.onSurfaceVariant)
        categories.forEach { category ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(category.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { editingCategory = category }) { Text("修改") }
                    TextButton(
                        onClick = { categoryToDelete = category },
                        enabled = category.id != SavedSnippetCategoryDefaults.GENERAL_ID
                    ) { Text("刪除") }
                }
            }
        }
    }
    if (showCreateDialog || editingCategory != null) {
        CategoryNameDialog(
            title = if (editingCategory == null) "新增分類" else "修改分類",
            initialName = editingCategory?.name.orEmpty(),
            onDismiss = {
                showCreateDialog = false
                editingCategory = null
            },
            onSave = { name ->
                val category = editingCategory
                if (category == null) onCreateCategory(name) else onRenameCategory(category.id, name)
                showCreateDialog = false
                editingCategory = null
            }
        )
    }
    categoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("刪除分類？") },
            text = { Text("分類中的短語會保留並移至「一般」。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCategory(category.id)
                    categoryToDelete = null
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { categoryToDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun SavedSnippetEditorDialog(
    snippet: SavedSnippet?,
    categories: List<SavedSnippetCategory>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember(snippet?.id) { mutableStateOf(snippet?.title.orEmpty()) }
    var content by remember(snippet?.id) { mutableStateOf(snippet?.content.orEmpty()) }
    var categoryId by remember(snippet?.id) {
        mutableStateOf(snippet?.categoryId ?: SavedSnippetCategoryDefaults.GENERAL_ID)
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
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
                Box {
                    OutlinedButton(onClick = { categoryMenuExpanded = true }) {
                        Text(selectedCategory?.name ?: "一般")
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    categoryId = category.id
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
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
                onClick = { onSave(title, content, selectedCategory?.id ?: categoryId) },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CategoryNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分類名稱") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CategoryFilterButton(name: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(name) }
    } else {
        OutlinedButton(onClick = onClick) { Text(name) }
    }
}
