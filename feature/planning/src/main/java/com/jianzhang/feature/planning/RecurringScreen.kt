package com.jianzhang.feature.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.core.common.Dates
import com.jianzhang.core.common.Money
import com.jianzhang.core.designsystem.AmountText
import com.jianzhang.core.designsystem.EmptyState
import com.jianzhang.core.designsystem.GlassPrimaryButton
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.JzOutlinedButton
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.model.Account
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.RecurrenceFrequency
import com.jianzhang.core.model.RecurringRule
import com.jianzhang.core.model.TxnType
import java.time.LocalDate

private fun freqLabel(frequency: RecurrenceFrequency): String = when (frequency) {
    RecurrenceFrequency.DAILY -> "每天"
    RecurrenceFrequency.WEEKLY -> "每周"
    RecurrenceFrequency.MONTHLY -> "每月"
    RecurrenceFrequency.YEARLY -> "每年"
}

@Composable
fun RecurringScreen(
    onBack: () -> Unit = {},
    viewModel: PlanningViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RecurringRule?>(null) }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.savedRecurring.collect {
            showEditor = false
            editingRule = null
        }
    }

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
                    PageHeader(
                        title = "周期记账",
                        subtitle = "固定收支按时落到账本",
                        modifier = Modifier.weight(1f),
                        trailing = {
                            TextButton(onClick = viewModel::runRecurring, enabled = !busy) {
                                Text(if (busy) "执行中" else "执行到期", color = Jz.colors.accent)
                            }
                        },
                    )
                }
                JzDivider()
            }
        }
        if (state.recurring.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有周期规则",
                    body = "房租、订阅、工资都可以交给它，到期自动生成流水。",
                    modifier = Modifier.padding(horizontal = Jz.space.page),
                    action = { JzOutlinedButton("新建规则", onClick = { showEditor = true }) },
                )
            }
        } else {
            items(state.recurring.size) { index ->
                val rule = state.recurring[index]
                RecurringRow(
                    rule = rule,
                    onToggle = { viewModel.toggleRecurring(rule) },
                    onConfirm = { viewModel.confirmRecurring(rule.id) },
                    onEdit = { editingRule = rule },
                    onDelete = { viewModel.deleteRecurring(rule.id) },
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page, vertical = 18.dp)) {
                GlassPrimaryButton("新建周期规则", onClick = { showEditor = true })
            }
        }
    }

    if (showEditor || editingRule != null) {
        val editing = editingRule
        RecurringEditorDialog(
            categories = state.budgets.categories,
            accounts = state.accounts,
            rule = editing,
            onDismiss = { showEditor = false; editingRule = null },
            onSave = { type, amount, categoryId, accountId, note, frequency, interval, start, endDate, auto, enabled ->
                viewModel.saveRecurring(
                    id = editing?.id,
                    type = type,
                    amountText = amount,
                    categoryId = categoryId,
                    accountId = accountId,
                    note = note,
                    frequency = frequency,
                    interval = interval,
                    start = start,
                    endDate = endDate,
                    autoCreate = auto,
                    enabled = enabled,
                )
            },
        )
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("操作未完成") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("知道了") } },
        )
    }
}

@Composable
private fun RecurringRow(
    rule: RecurringRule,
    onToggle: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Jz.space.page, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.note.ifBlank { if (rule.type == TxnType.EXPENSE) "固定支出" else "固定收入" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Jz.colors.ink,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    freqLabel(rule.frequency) + if (rule.intervalCount > 1) " · ${rule.intervalCount}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Jz.colors.ink3,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "下次 " + Dates.iso(rule.nextDate) + if (!rule.enabled) " · 已暂停" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (rule.enabled) Jz.colors.ink2 else Jz.colors.ink3,
            )
        }
        AmountText(cents = rule.amountCents, type = rule.type, showSign = true)
        Spacer(Modifier.width(6.dp))
        Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = Jz.colors.ink3)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("编辑规则") },
                    onClick = { menu = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("立即确认入账") },
                    onClick = { menu = false; onConfirm() },
                )
                DropdownMenuItem(
                    text = { Text("删除规则", color = Jz.colors.expense) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
    JzDivider()
}

@Composable
private fun RecurringEditorDialog(
    categories: List<Category>,
    accounts: List<Account>,
    rule: RecurringRule?,
    onDismiss: () -> Unit,
    onSave: (
        TxnType,
        String,
        Long?,
        Long?,
        String,
        RecurrenceFrequency,
        Int,
        LocalDate,
        LocalDate?,
        Boolean,
        Boolean,
    ) -> Unit,
) {
    val key = rule?.id ?: 0L

    fun defaultCategoryId(type: TxnType): Long? {
        val options = categories.filter { it.type == type }
        return options.firstOrNull { it.name == "其他" }?.id ?: options.firstOrNull()?.id
    }

    var type by remember(key) { mutableStateOf(rule?.type ?: TxnType.EXPENSE) }
    var amount by remember(key) { mutableStateOf(rule?.let { Money.format(it.amountCents) } ?: "") }
    var note by remember(key) { mutableStateOf(rule?.note ?: "") }
    var frequency by remember(key) { mutableStateOf(rule?.frequency ?: RecurrenceFrequency.MONTHLY) }
    var interval by remember(key) { mutableStateOf(rule?.intervalCount?.toString() ?: "1") }
    var start by remember(key) { mutableStateOf(Dates.iso(rule?.startDate ?: LocalDate.now())) }
    var endDate by remember(key) { mutableStateOf(rule?.endDate?.let(Dates::iso) ?: "") }
    var categoryId by remember(key) {
        mutableStateOf(rule?.categoryId ?: defaultCategoryId(rule?.type ?: TxnType.EXPENSE))
    }
    var accountId by remember(key) { mutableStateOf(rule?.accountId ?: accounts.firstOrNull()?.id) }
    var accountTouched by remember(key) { mutableStateOf(false) }
    var auto by remember(key) { mutableStateOf(rule?.autoCreate ?: true) }
    var enabled by remember(key) { mutableStateOf(rule?.enabled ?: true) }
    var categoryMenu by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    var endDateError by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(categories, type) {
        val available = categories.filter { it.type == type }
        if (available.isNotEmpty() && available.none { it.id == categoryId }) {
            categoryId = defaultCategoryId(type)
            saveError = null
        }
    }

    LaunchedEffect(accounts) {
        if (rule == null && !accountTouched && accountId == null && accounts.isNotEmpty()) {
            accountId = accounts.first().id
        }
    }

    val options = categories.filter { it.type == type }
    val selectedName = options.firstOrNull { it.id == categoryId }?.name ?: "请选择分类"
    val selectedAccountName = accounts.firstOrNull { it.id == accountId }?.name ?: "不关联账户"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) "新建周期规则" else "编辑周期规则") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip("支出", type == TxnType.EXPENSE) {
                        type = TxnType.EXPENSE
                        categoryId = defaultCategoryId(TxnType.EXPENSE)
                        saveError = null
                    }
                    TypeChip("收入", type == TxnType.INCOME) {
                        type = TxnType.INCOME
                        categoryId = defaultCategoryId(TxnType.INCOME)
                        saveError = null
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; saveError = null },
                    label = { Text("金额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecurrenceFrequency.entries.forEach { item ->
                        TypeChip(freqLabel(item), frequency == item) { frequency = item }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { interval = it.filter(Char::isDigit).take(2) },
                        label = { Text("间隔") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it; dateError = false; saveError = null },
                        label = { Text("起始日期") },
                        singleLine = true,
                        isError = dateError,
                        modifier = Modifier.weight(1.6f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it; endDateError = false; saveError = null },
                    label = { Text("结束日期（可选）") },
                    placeholder = { Text("留空表示长期有效") },
                    singleLine = true,
                    isError = endDateError,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "分类",
                    style = MaterialTheme.typography.labelLarge,
                    color = Jz.colors.ink2,
                )
                Box {
                    JzOutlinedButton(selectedName + "  ▾", onClick = { categoryMenu = true })
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        options.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { categoryId = category.id; categoryMenu = false; saveError = null },
                            )
                        }
                    }
                }
                if (options.isEmpty()) {
                    Text(
                        "当前类型还没有分类，请先到设置中创建分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = Jz.colors.expense,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "账户",
                    style = MaterialTheme.typography.labelLarge,
                    color = Jz.colors.ink2,
                )
                Box {
                    JzOutlinedButton(selectedAccountName + "  ▾", onClick = { accountMenu = true })
                    DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("不关联账户") },
                            onClick = { accountId = null; accountTouched = true; accountMenu = false },
                        )
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = { accountId = account.id; accountTouched = true; accountMenu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ToggleLine("自动生成流水", auto) { auto = it }
                ToggleLine("启用规则", enabled) { enabled = it }
                saveError?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Jz.colors.expense,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedStart = runCatching { LocalDate.parse(start.trim()) }.getOrNull()
                    val parsedEnd = endDate.trim().takeIf { it.isNotEmpty() }?.let {
                        runCatching { LocalDate.parse(it) }.getOrNull()
                    }
                    when {
                        parsedStart == null -> dateError = true
                        endDate.isNotBlank() && parsedEnd == null -> endDateError = true
                        parsedEnd != null && parsedEnd.isBefore(parsedStart) ->
                            saveError = "结束日期不能早于起始日期"
                        categoryId == null -> saveError = "请选择分类"
                        else -> onSave(
                            type,
                            amount,
                            categoryId,
                            accountId,
                            note,
                            frequency,
                            interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            parsedStart,
                            parsedEnd,
                            auto,
                            enabled,
                        )
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Jz.colors.ink2)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Jz.colors.onAccent else Jz.colors.ink2,
        modifier = Modifier
            .background(if (selected) Jz.colors.accent else Jz.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
