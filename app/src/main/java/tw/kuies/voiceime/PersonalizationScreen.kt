package tw.kuies.voiceime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class EditorDialog {
    NONE,
    ADD_GLOSSARY,
    BATCH_GLOSSARY,
    ADD_RULE,
    BATCH_RULES
}

/** 一個分頁的批次選取狀態：選取內容 + 是否處於選取模式。 */
private data class BulkSelectionUi(
    val selection: PersonalizationSelection = PersonalizationSelection(),
    val selecting: Boolean = false
)

/**
 * 資料重新載入後的選取狀態：已刪除的 ID 會被丟掉；若原本選取的項目全部消失
 * （代表批次刪除成功）就離開選取模式。刪除失敗時資料未變，選取會完整保留。
 */
private fun BulkSelectionUi.afterDataChange(existingIds: List<String>): BulkSelectionUi {
    if (!selecting) return this
    val next = selection.afterDataChange(existingIds) ?: return BulkSelectionUi()
    return copy(selection = next)
}

@Composable
internal fun PersonalizationScreen(
    modifier: Modifier = Modifier,
    glossaryTerms: List<PersonalGlossaryTerm>,
    glossaryLoaded: Boolean,
    glossaryStatus: String,
    correctionRules: List<TextCorrectionRule>,
    correctionRulesLoaded: Boolean,
    correctionStatus: String,
    onAddGlossaryTerms: (List<String>) -> Unit,
    onSetGlossaryTermEnabled: (PersonalGlossaryTerm, Boolean) -> Unit,
    onSetGlossaryTermContextPhrases: (PersonalGlossaryTerm, List<String>) -> Unit,
    onDeleteSelectedGlossaryTerms: (Set<String>) -> Unit,
    onAddCorrectionRules: (List<Pair<String, String>>) -> Unit,
    onSetCorrectionRuleEnabled: (TextCorrectionRule, Boolean) -> Unit,
    onDeleteSelectedCorrectionRules: (Set<String>) -> Unit,
    glossaryDeleting: Boolean = false,
    correctionDeleting: Boolean = false,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(EditorDialog.NONE) }
    var glossarySearch by remember { mutableStateOf("") }
    var correctionSearch by remember { mutableStateOf("") }
    var glossaryInput by remember { mutableStateOf("") }
    var glossaryBatchInput by remember { mutableStateOf("") }
    var contextEntry by remember { mutableStateOf<PersonalGlossaryTerm?>(null) }
    var contextPhrasesInput by remember { mutableStateOf("") }
    var correctionSourceInput by remember { mutableStateOf("") }
    var correctionReplacementInput by remember { mutableStateOf("") }
    var correctionBatchInput by remember { mutableStateOf("") }
    var glossaryBulk by remember { mutableStateOf(BulkSelectionUi()) }
    var correctionBulk by remember { mutableStateOf(BulkSelectionUi()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val activeBulk = if (selectedTab == 0) glossaryBulk else correctionBulk
    val updateActiveBulk: (BulkSelectionUi) -> Unit = { updated ->
        if (selectedTab == 0) glossaryBulk = updated else correctionBulk = updated
    }

    // 刪除成功（選取項目全部消失）就離開選取模式；失敗時資料未變，選取保留以便重試。
    LaunchedEffect(glossaryTerms) {
        glossaryBulk = glossaryBulk.afterDataChange(glossaryTerms.map { it.id })
    }
    LaunchedEffect(correctionRules) {
        correctionBulk = correctionBulk.afterDataChange(correctionRules.map { it.id })
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("個人化", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    updateActiveBulk(
                        if (activeBulk.selecting) {
                            BulkSelectionUi()
                        } else {
                            activeBulk.copy(selecting = true)
                        }
                    )
                }
            ) { Text(if (activeBulk.selecting) "取消" else "刪除") }
        }
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    // 切換分頁一律退出選取模式並清空選取，選取 ID 不會跨分頁沿用。
                    glossaryBulk = BulkSelectionUi()
                    correctionBulk = BulkSelectionUi()
                    selectedTab = 0
                },
                text = { Text("常用詞") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    glossaryBulk = BulkSelectionUi()
                    correctionBulk = BulkSelectionUi()
                    selectedTab = 1
                },
                text = { Text("修正规則") }
            )
        }

        if (selectedTab == 0) {
            GlossaryTab(
                terms = glossaryTerms,
                loaded = glossaryLoaded,
                status = glossaryStatus,
                search = glossarySearch,
                onSearchChange = { glossarySearch = it },
                onAdd = { dialog = EditorDialog.ADD_GLOSSARY },
                onBatchImport = { dialog = EditorDialog.BATCH_GLOSSARY },
                onToggle = onSetGlossaryTermEnabled,
                onEditContext = { entry ->
                    contextEntry = entry
                    contextPhrasesInput = entry.commonPhrases.joinToString("\n")
                },
                selection = glossaryBulk.selection,
                selecting = glossaryBulk.selecting,
                deleting = glossaryDeleting,
                onSelectionChange = { glossaryBulk = glossaryBulk.copy(selection = it) },
                onRequestDelete = { confirmDelete = true }
            )
        } else {
            CorrectionRulesTab(
                rules = correctionRules,
                loaded = correctionRulesLoaded,
                status = correctionStatus,
                search = correctionSearch,
                onSearchChange = { correctionSearch = it },
                onAdd = { dialog = EditorDialog.ADD_RULE },
                onBatchImport = { dialog = EditorDialog.BATCH_RULES },
                onToggle = onSetCorrectionRuleEnabled,
                selection = correctionBulk.selection,
                selecting = correctionBulk.selecting,
                deleting = correctionDeleting,
                onSelectionChange = { correctionBulk = correctionBulk.copy(selection = it) },
                onRequestDelete = { confirmDelete = true }
            )
        }
    }

    when (dialog) {
        EditorDialog.ADD_GLOSSARY -> AlertDialog(
            onDismissRequest = { dialog = EditorDialog.NONE },
            title = { Text("新增詞彙") },
            text = {
                OutlinedTextField(
                    value = glossaryInput,
                    onValueChange = { glossaryInput = it },
                    label = { Text("詞彙") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (glossaryInput.isNotBlank()) {
                            onAddGlossaryTerms(listOf(glossaryInput))
                            glossaryInput = ""
                            dialog = EditorDialog.NONE
                        }
                    },
                    enabled = glossaryLoaded && glossaryInput.isNotBlank()
                ) { Text("新增") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = EditorDialog.NONE }) { Text("取消") }
            }
        )

        EditorDialog.BATCH_GLOSSARY -> AlertDialog(
            onDismissRequest = { dialog = EditorDialog.NONE },
            title = { Text("批次匯入常用詞") },
            text = {
                OutlinedTextField(
                    value = glossaryBatchInput,
                    onValueChange = { glossaryBatchInput = it },
                    label = { Text("每行一個詞彙") },
                    minLines = 5,
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddGlossaryTerms(glossaryBatchInput.lineSequence().toList())
                        glossaryBatchInput = ""
                        dialog = EditorDialog.NONE
                    },
                    enabled = glossaryLoaded && glossaryBatchInput.isNotBlank()
                ) { Text("匯入") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = EditorDialog.NONE }) { Text("取消") }
            }
        )

        EditorDialog.ADD_RULE -> AlertDialog(
            onDismissRequest = { dialog = EditorDialog.NONE },
            title = { Text("新增修正规則") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = correctionSourceInput,
                        onValueChange = { correctionSourceInput = it },
                        label = { Text("原始文字") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = correctionReplacementInput,
                        onValueChange = { correctionReplacementInput = it },
                        label = { Text("替換文字（可留空）") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddCorrectionRules(listOf(correctionSourceInput to correctionReplacementInput))
                        correctionSourceInput = ""
                        correctionReplacementInput = ""
                        dialog = EditorDialog.NONE
                    },
                    enabled = correctionRulesLoaded && correctionSourceInput.isNotBlank()
                ) { Text("新增") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = EditorDialog.NONE }) { Text("取消") }
            }
        )

        EditorDialog.BATCH_RULES -> AlertDialog(
            onDismissRequest = { dialog = EditorDialog.NONE },
            title = { Text("批次匯入修正规則") },
            text = {
                OutlinedTextField(
                    value = correctionBatchInput,
                    onValueChange = { correctionBatchInput = it },
                    label = { Text("每行格式：原始文字 => 替換文字") },
                    minLines = 5,
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddCorrectionRules(TextCorrectionRules.parseBatch(correctionBatchInput))
                        correctionBatchInput = ""
                        dialog = EditorDialog.NONE
                    },
                    enabled = correctionRulesLoaded && correctionBatchInput.isNotBlank()
                ) { Text("匯入") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = EditorDialog.NONE }) { Text("取消") }
            }
        )

        EditorDialog.NONE -> Unit
    }

    if (confirmDelete) {
        val selectedCount = activeBulk.selection.count
        val targetLabel = if (selectedTab == 0) "筆常用詞" else "條修正规則"
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("確認刪除") },
            text = { Text("確定要刪除選取的 $selectedCount $targetLabel 嗎？刪除後無法復原。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        val ids = activeBulk.selection.selectedIds
                        if (selectedTab == 0) {
                            onDeleteSelectedGlossaryTerms(ids)
                        } else {
                            onDeleteSelectedCorrectionRules(ids)
                        }
                    }
                ) { Text("確定刪除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }

    contextEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { contextEntry = null },
            title = { Text("「${entry.term}」的常見語句") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("選填；每行一個語句，最多 5 行。只作辨識語境參考，不會強迫替換其他句子中的詞。")
                    OutlinedTextField(
                        value = contextPhrasesInput,
                        onValueChange = { contextPhrasesInput = it },
                        label = { Text("常見語句") },
                        minLines = 3,
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetGlossaryTermContextPhrases(
                            entry,
                            contextPhrasesInput.lineSequence().toList()
                        )
                        contextEntry = null
                    },
                    enabled = glossaryLoaded
                ) { Text("儲存") }
            },
            dismissButton = {
                TextButton(onClick = { contextEntry = null }) { Text("取消") }
            }
        )
    }
}

/** 選取模式工具列：全選（三態）與已選取筆數。 */
@Composable
private fun SelectionToolbar(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    partiallySelected: Boolean,
    onToggleSelectAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TriStateCheckbox(
            state = when {
                allVisibleSelected -> ToggleableState.On
                partiallySelected -> ToggleableState.Indeterminate
                else -> ToggleableState.Off
            },
            onClick = onToggleSelectAll
        )
        Text("全選")
        Spacer(Modifier.weight(1f))
        Text("已選取 $selectedCount 筆", style = MaterialTheme.typography.bodyMedium)
    }
}

/** 選取模式下方的批次刪除按鈕；未選取任何項目時停用。 */
@Composable
private fun BulkDeleteBar(count: Int, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) { Text("刪除已選取（$count）") }
}

/** 選取模式中的資料列：整列可點擊切換勾選，勾選框尺寸與工具列一致。 */
@Composable
private fun SelectableRow(
    selecting: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selecting) {
                    Modifier.toggleable(
                        value = selected,
                        role = Role.Checkbox,
                        onValueChange = { onToggle() }
                    )
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        }
        content()
    }
}

/**
 * 常用詞與修正规則共用的清單骨架：標題、搜尋、新增／批次匯入、選取工具列、
 * 資料列、批次刪除列與空狀態都由這裡提供，兩個分頁只描述自己的列內容。
 */
@Composable
private fun <T> PersonalizationTab(
    title: String,
    unit: String,
    loadingText: String,
    emptyText: String,
    noMatchText: String,
    addLabel: String,
    search: String,
    onSearchChange: (String) -> Unit,
    loaded: Boolean,
    status: String,
    allItems: List<T>,
    visibleItems: List<T>,
    enabledCount: Int,
    idOf: (T) -> String,
    selection: PersonalizationSelection,
    selecting: Boolean,
    deleting: Boolean,
    onSelectionChange: (PersonalizationSelection) -> Unit,
    onAdd: () -> Unit,
    onBatchImport: () -> Unit,
    onRequestDelete: () -> Unit,
    row: @Composable RowScope.(T) -> Unit
) {
    val visibleIds = visibleItems.map(idOf)
    val allVisibleSelected = selection.isAllVisibleSelected(visibleIds)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            label = { Text("搜尋") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAdd,
                enabled = loaded && !selecting,
                modifier = Modifier.weight(1f)
            ) { Text(addLabel, maxLines = 1) }
            Button(
                onClick = onBatchImport,
                enabled = loaded && !selecting,
                modifier = Modifier.weight(1f)
            ) { Text("批次匯入", maxLines = 1) }
        }
        Spacer(Modifier.height(4.dp))
        if (selecting) {
            SelectionToolbar(
                selectedCount = selection.count,
                allVisibleSelected = allVisibleSelected,
                partiallySelected = selection.hasPartialVisibleSelection(visibleIds),
                onToggleSelectAll = {
                    onSelectionChange(
                        if (allVisibleSelected) {
                            selection.deselectVisible(visibleIds)
                        } else {
                            selection.selectVisible(visibleIds)
                        }
                    )
                }
            )
        } else {
            Text("總數 ${allItems.size} $unit · 啟用 $enabledCount $unit")
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (!loaded) {
                item { Text(loadingText, modifier = Modifier.padding(12.dp)) }
            } else if (visibleItems.isEmpty()) {
                item {
                    Text(
                        if (allItems.isEmpty()) emptyText else noMatchText,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                items(visibleItems, key = { idOf(it) }) { item ->
                    val id = idOf(item)
                    SelectableRow(
                        selecting = selecting,
                        selected = selection.isSelected(id),
                        onToggle = { onSelectionChange(selection.toggle(id)) }
                    ) {
                        row(item)
                    }
                    HorizontalDivider()
                }
            }
        }
        if (selecting) {
            BulkDeleteBar(
                count = selection.count,
                enabled = selection.count > 0 && !deleting,
                onClick = onRequestDelete
            )
        }
    }
}

@Composable
private fun GlossaryTab(
    terms: List<PersonalGlossaryTerm>,
    loaded: Boolean,
    status: String,
    search: String,
    onSearchChange: (String) -> Unit,
    onAdd: () -> Unit,
    onBatchImport: () -> Unit,
    onToggle: (PersonalGlossaryTerm, Boolean) -> Unit,
    onEditContext: (PersonalGlossaryTerm) -> Unit,
    selection: PersonalizationSelection,
    selecting: Boolean,
    deleting: Boolean,
    onSelectionChange: (PersonalizationSelection) -> Unit,
    onRequestDelete: () -> Unit
) {
    val filteredTerms = remember(terms, search) {
        terms.filter { it.term.contains(search.trim(), ignoreCase = true) }
    }
    PersonalizationTab(
        title = "常用詞",
        unit = "個",
        loadingText = "正在讀取詞庫…",
        emptyText = "詞庫目前是空的。",
        noMatchText = "沒有符合搜尋條件的詞彙。",
        addLabel = "新增詞彙",
        search = search,
        onSearchChange = onSearchChange,
        loaded = loaded,
        status = status,
        allItems = terms,
        visibleItems = filteredTerms,
        enabledCount = terms.count { it.enabled },
        idOf = { it.id },
        selection = selection,
        selecting = selecting,
        deleting = deleting,
        onSelectionChange = onSelectionChange,
        onAdd = onAdd,
        onBatchImport = onBatchImport,
        onRequestDelete = onRequestDelete
    ) { entry ->
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.term, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.commonPhrases.isNotEmpty()) {
                Text(
                    "${entry.commonPhrases.size} 個語境句",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!selecting) {
            TextButton(onClick = { onEditContext(entry) }) { Text("語句") }
            Switch(
                checked = entry.enabled,
                onCheckedChange = { onToggle(entry, it) }
            )
        }
    }
}

@Composable
private fun CorrectionRulesTab(
    rules: List<TextCorrectionRule>,
    loaded: Boolean,
    status: String,
    search: String,
    onSearchChange: (String) -> Unit,
    onAdd: () -> Unit,
    onBatchImport: () -> Unit,
    onToggle: (TextCorrectionRule, Boolean) -> Unit,
    selection: PersonalizationSelection,
    selecting: Boolean,
    deleting: Boolean,
    onSelectionChange: (PersonalizationSelection) -> Unit,
    onRequestDelete: () -> Unit
) {
    val filteredRules = remember(rules, search) {
        rules.filter {
            it.sourceText.contains(search.trim(), ignoreCase = true) ||
                it.replacementText.contains(search.trim(), ignoreCase = true)
        }
    }
    PersonalizationTab(
        title = "文字修正规則",
        unit = "條",
        loadingText = "正在讀取修正规則…",
        emptyText = "目前沒有文字修正规則。",
        noMatchText = "沒有符合搜尋條件的規則。",
        addLabel = "新增規則",
        search = search,
        onSearchChange = onSearchChange,
        loaded = loaded,
        status = status,
        allItems = rules,
        visibleItems = filteredRules,
        enabledCount = rules.count { it.enabled },
        idOf = { it.id },
        selection = selection,
        selecting = selecting,
        deleting = deleting,
        onSelectionChange = onSelectionChange,
        onAdd = onAdd,
        onBatchImport = onBatchImport,
        onRequestDelete = onRequestDelete
    ) { rule ->
        Column(modifier = Modifier.weight(1f)) {
            Text(
                rule.sourceText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "→ ${rule.replacementText.ifEmpty { "（刪除）" }}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!selecting) {
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle(rule, it) }
            )
        }
    }
}
