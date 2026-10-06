package tw.kuies.voiceime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class EditorDialog {
    NONE,
    ADD_GLOSSARY,
    BATCH_GLOSSARY,
    ADD_RULE,
    BATCH_RULES
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
    onImportDefaultGlossary: () -> Unit,
    onSetGlossaryTermEnabled: (PersonalGlossaryTerm, Boolean) -> Unit,
    onDeleteGlossaryTerm: (PersonalGlossaryTerm) -> Unit,
    onAddCorrectionRules: (List<Pair<String, String>>) -> Unit,
    onImportDefaultCorrectionRules: () -> Unit,
    onSetCorrectionRuleEnabled: (TextCorrectionRule, Boolean) -> Unit,
    onDeleteCorrectionRule: (TextCorrectionRule) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(EditorDialog.NONE) }
    var glossarySearch by remember { mutableStateOf("") }
    var correctionSearch by remember { mutableStateOf("") }
    var glossaryInput by remember { mutableStateOf("") }
    var glossaryBatchInput by remember { mutableStateOf("") }
    var correctionSourceInput by remember { mutableStateOf("") }
    var correctionReplacementInput by remember { mutableStateOf("") }
    var correctionBatchInput by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("個人化", style = MaterialTheme.typography.headlineSmall)
        }
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("常用詞") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
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
                onImportDefaults = onImportDefaultGlossary,
                onToggle = onSetGlossaryTermEnabled,
                onDelete = onDeleteGlossaryTerm
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
                onImportDefaults = onImportDefaultCorrectionRules,
                onToggle = onSetCorrectionRuleEnabled,
                onDelete = onDeleteCorrectionRule
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
    onImportDefaults: () -> Unit,
    onToggle: (PersonalGlossaryTerm, Boolean) -> Unit,
    onDelete: (PersonalGlossaryTerm) -> Unit
) {
    val filteredTerms = remember(terms, search) {
        terms.filter { it.term.contains(search.trim(), ignoreCase = true) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text("常用詞", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            label = { Text("搜尋") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd, enabled = loaded, modifier = Modifier.weight(1f)) {
                Text("新增詞彙", maxLines = 1)
            }
            Button(onClick = onBatchImport, enabled = loaded, modifier = Modifier.weight(1f)) {
                Text("批次匯入", maxLines = 1)
            }
        }
        TextButton(
            onClick = onImportDefaults,
            enabled = loaded,
            modifier = Modifier.fillMaxWidth()
        ) { Text("匯入預設詞庫") }
        Text("總數 ${terms.size} 個 · 啟用 ${terms.count { it.enabled }} 個")
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (!loaded) {
                item { Text("正在讀取詞庫…", modifier = Modifier.padding(12.dp)) }
            } else if (filteredTerms.isEmpty()) {
                item {
                    Text(
                        if (terms.isEmpty()) "詞庫目前是空的。" else "沒有符合搜尋條件的詞彙。",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                items(filteredTerms, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            entry.term,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { onToggle(entry, it) }
                        )
                        TextButton(onClick = { onDelete(entry) }) { Text("刪除") }
                    }
                    HorizontalDivider()
                }
            }
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
    onImportDefaults: () -> Unit,
    onToggle: (TextCorrectionRule, Boolean) -> Unit,
    onDelete: (TextCorrectionRule) -> Unit
) {
    val filteredRules = remember(rules, search) {
        rules.filter {
            it.sourceText.contains(search.trim(), ignoreCase = true) ||
                it.replacementText.contains(search.trim(), ignoreCase = true)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text("文字修正规則", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            label = { Text("搜尋") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd, enabled = loaded, modifier = Modifier.weight(1f)) {
                Text("新增規則", maxLines = 1)
            }
            Button(onClick = onBatchImport, enabled = loaded, modifier = Modifier.weight(1f)) {
                Text("批次匯入", maxLines = 1)
            }
        }
        TextButton(
            onClick = onImportDefaults,
            enabled = loaded,
            modifier = Modifier.fillMaxWidth()
        ) { Text("匯入預設修正规則") }
        Text("總數 ${rules.size} 條 · 啟用 ${rules.count { it.enabled }} 條")
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (!loaded) {
                item { Text("正在讀取修正规則…", modifier = Modifier.padding(12.dp)) }
            } else if (filteredRules.isEmpty()) {
                item {
                    Text(
                        if (rules.isEmpty()) "目前沒有文字修正规則。" else "沒有符合搜尋條件的規則。",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                items(filteredRules, key = { it.id }) { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onToggle(rule, it) }
                        )
                        TextButton(onClick = { onDelete(rule) }) { Text("刪除") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
