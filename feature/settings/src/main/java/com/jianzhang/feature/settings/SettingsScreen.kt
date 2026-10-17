package com.jianzhang.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.core.data.BackupCrypto
import com.jianzhang.core.designsystem.GlassPrimaryButton
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.JzOutlinedButton
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.designsystem.SectionTitle
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.TxnType
import java.time.LocalDate

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenLock: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val importPasswordError by viewModel.importPasswordError.collectAsStateWithLifecycle()
    val settings = state.settings

    var showCategories by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var showEncryptedExport by remember { mutableStateOf(false) }
    var showPlainExportWarning by remember { mutableStateOf(false) }

    val exportEncrypted = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) viewModel.cancelEncryptedExport() else viewModel.completeEncryptedExport(uri)
    }
    val exportPlain = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importBackup(it, replace = false) } }
    val importReplace = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importBackup(it, replace = true) } }

    val stamp = LocalDate.now().toString().replace("-", "")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Jz.colors.ink2)
                    }
                    PageHeader("设置", "外观、数据与安全", modifier = Modifier.weight(1f))
                }
                JzDivider()
            }
        }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                SectionTitle("外观")
                Label("主题")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice("深色", settings.themeMode == "dark") { viewModel.setTheme("dark") }
                    Choice("浅色", settings.themeMode == "light") { viewModel.setTheme("light") }
                    Choice("跟随系统", settings.themeMode == "system") { viewModel.setTheme("system") }
                }
                Spacer(Modifier.height(14.dp))
                Label("字号")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice("小", settings.fontScale < 0.95f) { viewModel.setFontScale(0.9f) }
                    Choice("标准", settings.fontScale in 0.95f..1.05f) { viewModel.setFontScale(1f) }
                    Choice("大", settings.fontScale > 1.05f) { viewModel.setFontScale(1.15f) }
                }
                SectionTitle("记账默认值")
                Label("默认类型")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice("支出", settings.defaultType == TxnType.EXPENSE) { viewModel.setDefaultType(TxnType.EXPENSE) }
                    Choice("收入", settings.defaultType == TxnType.INCOME) { viewModel.setDefaultType(TxnType.INCOME) }
                }
                Spacer(Modifier.height(14.dp))
                DefaultAccountRow(
                    accounts = state.accounts,
                    selectedId = settings.defaultAccountId,
                    onSelect = viewModel::setDefaultAccount,
                )
                ToggleRow("记住上次分类", "下一次记账自动选中", settings.rememberLastCategory, viewModel::setRememberCategory)
                ToggleRow("金额显示正负号", "支出为负、收入为正", settings.showAmountSign, viewModel::setShowSign)
                SectionTitle("分类")
                Row(
                    Modifier.fillMaxWidth().clickable { showCategories = true }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("管理分类", style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
                        Text(
                            "共 ${state.categories.size} 个分类",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Jz.colors.ink3,
                        )
                    }
                    Text("打开", style = MaterialTheme.typography.labelLarge, color = Jz.colors.accent)
                }
                JzDivider()
                SectionTitle("数据")
                ActionRow("导出加密备份", "AES-256-GCM · 需要设置口令") {
                    showEncryptedExport = true
                }
                ActionRow("导出明文备份", "不加密，仅用于兼容旧版", danger = true) {
                    showPlainExportWarning = true
                }
                ActionRow("导入并合并", "保留现有数据，按名称合并") {
                    importBackup.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
                ActionRow("导入并替换", "清空当前账本后导入") {
                    confirmReplace = true
                }
                ActionRow("导出 CSV", "可用 Excel 打开，UTF-8 带 BOM") {
                    exportCsv.launch("jianzhang-$stamp.csv")
                }
                ActionRow("清空全部数据", "不可撤销，会恢复默认分类", danger = true) {
                    confirmReset = true
                }
                SectionTitle("AI 分析")
                AiSection(
                    endpoint = settings.aiEndpoint,
                    hasKey = settings.aiApiKey.isNotEmpty(),
                    model = settings.aiModel,
                    temperature = settings.aiTemperature,
                    timeout = settings.aiTimeoutSeconds,
                    onSave = viewModel::saveAi,
                    onClearKey = viewModel::clearAiKey,
                )
                SectionTitle("安全与关于")
                ActionRow("应用锁", if (settings.lockEnabled) "已开启" else "未开启") { onOpenLock() }
                ActionRow("关于简账", "版本、许可与隐私") { onOpenAbout() }
                Spacer(Modifier.height(20.dp))
                if (busy) {
                    Text("正在处理…", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
                }
            }
        }
    }

    if (showCategories) {
        CategoryDialog(
            categories = state.categories,
            onDismiss = { showCategories = false },
            onSave = { id, type, name -> viewModel.saveCategory(id, type, name) },
            onDelete = viewModel::deleteCategory,
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("清空全部数据") },
            text = { Text("所有流水、账户、预算、标签和周期规则都会被删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; viewModel.resetAll() }) {
                    Text("确认清空", color = Jz.colors.expense)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
        )
    }
    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("替换当前账本") },
            text = { Text("导入前会清空现有数据。建议先导出一份备份。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    importReplace.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("取消") } },
        )
    }
    if (showEncryptedExport) {
        var password by remember { mutableStateOf("") }
        var confirmation by remember { mutableStateOf("") }
        var localError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showEncryptedExport = false },
            title = { Text("设置备份口令") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "备份会用 AES-256-GCM 加密。口令不会保存在应用里，遗失后无法解密。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Jz.colors.ink3,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(128); localError = null },
                        label = { Text("口令（至少 8 位）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.take(128); localError = null },
                        label = { Text("再次输入口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    localError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.expense)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val validation = BackupCrypto.validateExportPassword(password)
                    when {
                        validation != null -> localError = validation
                        password != confirmation -> localError = "两次输入的口令不一致"
                        else -> {
                            showEncryptedExport = false
                            viewModel.beginEncryptedExport(password)
                            exportEncrypted.launch("jianzhang-backup-$stamp.jzbak")
                        }
                    }
                }) { Text("选择保存位置") }
            },
            dismissButton = { TextButton(onClick = { showEncryptedExport = false }) { Text("取消") } },
        )
    }
    if (showPlainExportWarning) {
        AlertDialog(
            onDismissRequest = { showPlainExportWarning = false },
            title = { Text("导出明文备份") },
            text = {
                Text(
                    "明文备份不加密，任何拿到文件的人都能读取全部账本内容。仅在需要导入旧版本或自行加密时使用。",
                    color = Jz.colors.expense,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPlainExportWarning = false
                    exportPlain.launch("jianzhang-backup-$stamp.json")
                }) { Text("仍要导出", color = Jz.colors.expense) }
            },
            dismissButton = { TextButton(onClick = { showPlainExportWarning = false }) { Text("取消") } },
        )
    }
    pendingImport?.let { pending ->
        var password by remember(pending) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("加密备份") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (pending.replace) {
                            "该备份已加密。输入口令后会替换当前账本，现有数据将被清空。"
                        } else {
                            "该备份已加密，请输入导出时设置的口令。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Jz.colors.ink3,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it.take(128)
                            viewModel.clearImportPasswordError()
                        },
                        label = { Text("备份口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    importPasswordError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.expense)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.submitImportPassword(password) },
                    enabled = !busy,
                ) { Text(if (busy) "正在导入…" else "解锁并导入") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("取消") } },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text("完成") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("好") } },
        )
    }
    error?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("未完成") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("知道了") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Jz.colors.ink3,
        modifier = Modifier.padding(bottom = 7.dp),
    )
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Jz.colors.onAccent else Jz.colors.ink2,
        modifier = Modifier
            .background(if (selected) Jz.colors.accent else Jz.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ToggleRow(title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
    JzDivider()
}

@Composable
private fun ActionRow(
    title: String,
    caption: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (danger) Jz.colors.expense else Jz.colors.ink,
            )
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
        }
        Text("›", color = Jz.colors.ink3, style = MaterialTheme.typography.titleLarge)
    }
    JzDivider()
}

@Composable
private fun DefaultAccountRow(accounts: List<com.jianzhang.core.model.Account>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val name = accounts.firstOrNull { it.id == selectedId }?.name ?: "首个在用账户"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("默认账户", style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
            Text("新流水没有指定账户时使用", style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
        }
        Box {
            JzOutlinedButton(name + "  ▾", onClick = { expanded = true })
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("首个在用账户") }, onClick = { onSelect(null); expanded = false })
                accounts.filterNot { it.archived }.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = { onSelect(account.id); expanded = false },
                    )
                }
            }
        }
    }
    JzDivider()
}

@Composable
private fun AiSection(
    endpoint: String,
    hasKey: Boolean,
    model: String,
    temperature: Double,
    timeout: Int,
    onSave: (String, String, String, String, String) -> Unit,
    onClearKey: () -> Unit,
) {
    var endpointText by remember(endpoint) { mutableStateOf(endpoint) }
    var keyText by remember(hasKey) { mutableStateOf("") }
    var modelText by remember(model) { mutableStateOf(model) }
    var tempText by remember(temperature) { mutableStateOf(temperature.toString()) }
    var timeoutText by remember(timeout) { mutableStateOf(timeout.toString()) }

    Column {
        OutlinedTextField(
            value = endpointText,
            onValueChange = { endpointText = it },
            label = { Text("接口地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = modelText,
            onValueChange = { modelText = it },
            label = { Text("模型") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = keyText,
            onValueChange = { keyText = it },
            label = { Text(if (hasKey) "API Key（已保存，留空不修改）" else "API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = tempText,
                onValueChange = { tempText = it },
                label = { Text("温度") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = timeoutText,
                onValueChange = { timeoutText = it },
                label = { Text("超时秒") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        GlassPrimaryButton(
            "保存 AI 配置",
            onClick = { onSave(endpointText, keyText, modelText, tempText, timeoutText) },
        )
        if (hasKey) {
            Spacer(Modifier.height(8.dp))
            JzOutlinedButton("清除 API Key", onClick = onClearKey)
        }
    }
}

@Composable
private fun CategoryDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Long?, TxnType, String) -> Unit,
    onDelete: (Category) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxnType.EXPENSE) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var pendingEdit by remember { mutableStateOf<Category?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类管理") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice("支出", type == TxnType.EXPENSE) { type = TxnType.EXPENSE }
                    Choice("收入", type == TxnType.INCOME) { type = TxnType.INCOME }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(12) },
                        label = { Text("新分类") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    JzOutlinedButton("添加", onClick = {
                        if (name.isNotBlank()) {
                            onSave(null, type, name)
                            name = ""
                        }
                    })
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(categories, key = { it.id }) { category ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Jz.colors.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (category.type == TxnType.EXPENSE) "支出" else "收入",
                                style = MaterialTheme.typography.labelSmall,
                                color = Jz.colors.ink3,
                            )
                            IconButton(onClick = { pendingEdit = category }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = Jz.colors.ink3)
                            }
                            IconButton(onClick = { pendingDelete = category }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Jz.colors.ink3)
                            }
                        }
                        JzDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )

    pendingEdit?.let { category ->
        var editName by remember(category.id) { mutableStateOf(category.name) }
        var editType by remember(category.id) { mutableStateOf(category.type) }
        AlertDialog(
            onDismissRequest = { pendingEdit = null },
            title = { Text("编辑分类") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Choice("支出", editType == TxnType.EXPENSE) { editType = TxnType.EXPENSE }
                        Choice("收入", editType == TxnType.INCOME) { editType = TxnType.INCOME }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it.take(12) },
                        label = { Text("分类名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank()) {
                        onSave(category.id, editType, editName.trim())
                        pendingEdit = null
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { pendingEdit = null }) { Text("取消") } },
        )
    }

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除分类") },
            text = { Text("「${category.name}」下的流水会变成未分类，不会删除流水。") },
            confirmButton = {
                TextButton(onClick = { onDelete(category); pendingDelete = null }) {
                    Text("删除", color = Jz.colors.expense)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}





