package com.jianzhang.feature.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.core.common.Dates
import com.jianzhang.core.common.Money
import com.jianzhang.core.designsystem.AmountText
import com.jianzhang.core.designsystem.EmptyState
import com.jianzhang.core.designsystem.GaugeBar
import com.jianzhang.core.designsystem.GaugeScale
import com.jianzhang.core.designsystem.GlassPrimaryButton
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.JzOutlinedButton
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.designsystem.SectionTitle
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.TxnType

@Composable
fun BudgetScreen(
    onBack: () -> Unit = {},
    viewModel: PlanningViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val budget = state.budgets
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var editCategoryId by remember { mutableStateOf<Long?>(null) }

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
                        title = "预算",
                        subtitle = "给每个月一条可执行的线",
                        modifier = Modifier.weight(1f),
                    )
                }
                JzDivider()
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月", tint = Jz.colors.ink2)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            Dates.cnMonth(budget.month),
                            style = MaterialTheme.typography.titleLarge,
                            color = Jz.colors.ink,
                        )
                        Text(
                            if (budget.month == java.time.YearMonth.now()) "本月" else "历史月份",
                            style = MaterialTheme.typography.labelSmall,
                            color = Jz.colors.ink3,
                        )
                    }
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月", tint = Jz.colors.ink2)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().background(Jz.colors.surface).padding(16.dp)) {
                    Text("本月总预算", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "¥" + Money.format(budget.totalBudgetCents),
                            style = MaterialTheme.typography.displayMedium,
                            color = Jz.colors.ink,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    GaugeBar(
                        progress = if (budget.totalBudgetCents > 0) budget.progress else 0f,
                        over = budget.remaining < 0,
                        ticks = 24,
                        height = 18.dp,
                    )
                    Spacer(Modifier.height(7.dp))
                    GaugeScale(listOf("0", "25%", "50%", "75%", "100%"))
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricBlock("已支出", Money.format(budget.expenseCents), Jz.colors.expense)
                        MetricBlock(
                            "剩余",
                            Money.format(kotlin.math.abs(budget.remaining)),
                            if (budget.remaining < 0) Jz.colors.expense else Jz.colors.income,
                        )
                        MetricBlock("日均可用", Money.format(budget.dailyAvailable), Jz.colors.ink)
                    }
                }
                Spacer(Modifier.height(16.dp))
                GlassPrimaryButton(
                    text = if (budget.totalBudgetCents > 0) "调整本月预算" else "设置本月预算",
                    onClick = {
                        editCategoryId = null
                        showEditor = true
                    },
                )
                SectionTitle("分类额度", right = "按支出统计")
            }
        }
        if (budget.rows.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有分类预算",
                    body = "为容易超支的分类单独设一条线，刻度会实时跟着流水走。",
                    modifier = Modifier.padding(horizontal = Jz.space.page),
                    action = {
                        JzOutlinedButton("添加分类预算", onClick = {
                            editCategoryId = budget.categories.firstOrNull { it.type == TxnType.EXPENSE }?.id
                            showEditor = true
                        })
                    },
                )
            }
        } else {
            items(budget.rows.size) { index ->
                val row = budget.rows[index]
                val ratio = if (row.budgetCents > 0) row.spentCents.toFloat() / row.budgetCents else 0f
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            editCategoryId = row.categoryId
                            showEditor = true
                        }
                        .padding(horizontal = Jz.space.page, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
                        Spacer(Modifier.height(8.dp))
                        GaugeBar(progress = ratio, over = ratio > 1f, ticks = 16, height = 10.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        AmountText(cents = row.spentCents, type = TxnType.EXPENSE, showSign = false)
                        Text(
                            "/ " + Money.format(row.budgetCents),
                            style = MaterialTheme.typography.labelSmall,
                            color = Jz.colors.ink3,
                        )
                    }
                }
                JzDivider()
            }
        }
    }

    if (showEditor) {
        BudgetEditorDialog(
            categories = budget.categories.filter { it.type == TxnType.EXPENSE },
            initialCategoryId = editCategoryId,
            initialAmount = if (editCategoryId == null) {
                if (budget.totalBudgetCents > 0) Money.format(budget.totalBudgetCents) else ""
            } else {
                budget.rows.firstOrNull { it.categoryId == editCategoryId }
                    ?.let { Money.format(it.budgetCents) } ?: ""
            },
            onDismiss = { showEditor = false },
            onSave = { categoryId, amount ->
                viewModel.saveBudget(categoryId, amount)
                showEditor = false
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
private fun MetricBlock(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun BudgetEditorDialog(
    categories: List<Category>,
    initialCategoryId: Long?,
    initialAmount: String,
    onDismiss: () -> Unit,
    onSave: (Long?, String) -> Unit,
) {
    var categoryId by remember { mutableStateOf(initialCategoryId) }
    var amount by remember { mutableStateOf(initialAmount) }
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == categoryId }?.name ?: "总预算"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置预算") },
        text = {
            Column {
                Box {
                    JzOutlinedButton(selectedName + "  ▾", onClick = { expanded = true })
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("总预算") },
                            onClick = { categoryId = null; expanded = false },
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { categoryId = category.id; expanded = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("金额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "留空或 0 表示取消这一项预算",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Jz.colors.ink3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(categoryId, amount.ifBlank { "0" }) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
