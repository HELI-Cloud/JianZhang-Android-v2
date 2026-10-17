package com.jianzhang.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.core.common.Money
import com.jianzhang.core.designsystem.EmptyState
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.designsystem.SectionTitle
import com.jianzhang.core.model.CategoryTotal
import com.jianzhang.core.model.DailyTotal
import com.jianzhang.core.model.MonthAggregate
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var customDialog by remember { mutableStateOf(false) }
    if (customDialog) {
        CustomRangeDialog(
            initialStart = state.start,
            initialEnd = state.end,
            onDismiss = { customDialog = false },
            onApply = { start, end ->
                viewModel.setCustom(start, end)
                customDialog = false
            },
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 104.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                PageHeader("统计", "看见结构，而不是只看总额")
                RangeSelector(
                    selected = state.range,
                    onSelect = viewModel::selectRange,
                    onCustom = { customDialog = true },
                )
                if (state.range == StatsRange.CUSTOM) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${state.start} → ${state.end}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Jz.colors.ink3,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(12.dp))
                TagSelector(state.tags.map { it.id to it.name }, state.selectedTagId, viewModel::selectTag)
                JzDivider()
            }
        }
        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text("正在汇总…", color = Jz.colors.ink2) } }
        } else {
            item {
                Column(Modifier.padding(horizontal = Jz.space.page)) {
                    SummaryPanel(
                        income = state.summary.incomeCents,
                        expense = state.summary.expenseCents,
                        net = state.summary.netCents,
                        mom = state.expenseMom,
                        yoy = state.expenseYoy,
                    )
                    SectionTitle("年度走势", "${java.time.YearMonth.now().year} 年")
                    AnnualBars(state.months)
                    SectionTitle("日支出", "所选区间")
                    DailyLine(state.daily)
                    SectionTitle("分类占比", "按支出金额")
                    if (state.categories.isEmpty()) {
                        EmptyState("暂无支出", "记录几笔之后，这里会出现结构。")
                    } else {
                        CategoryBars(state.categories)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun RangeSelector(
    selected: StatsRange,
    onSelect: (StatsRange) -> Unit,
    onCustom: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatsRange.entries.forEach { range ->
            Chip(
                text = range.label,
                selected = selected == range,
                onClick = { if (range == StatsRange.CUSTOM) onCustom() else onSelect(range) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TagSelector(tags: List<Pair<Long, String>>, selected: Long?, onSelect: (Long?) -> Unit) {
    Column {
        Text("标签筛选", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Chip("全部", selected == null, { onSelect(null) }) }
            items(tags, key = { it.first }) { (id, name) ->
                Chip(name, selected == id, { onSelect(id) })
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(if (selected) Jz.colors.accent else Jz.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Jz.colors.onAccent else Jz.colors.ink2,
        )
    }
}

@Composable
private fun SummaryPanel(income: Long, expense: Long, net: Long, mom: Double?, yoy: Double?) {
    Column(
        Modifier.fillMaxWidth().background(Jz.colors.surface).padding(14.dp),
    ) {
        Text("区间支出", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
        Text(
            "¥${Money.format(expense)}",
            style = MaterialTheme.typography.displayMedium,
            color = Jz.colors.ink,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric("收入", "¥${Money.format(income)}", Jz.colors.income, Modifier.weight(1f))
            Metric("结余", "¥${Money.format(net)}", if (net >= 0) Jz.colors.income else Jz.colors.expense, Modifier.weight(1f))
            Metric("环比", mom?.let { percent(it) } ?: "—", Jz.colors.ink2, Modifier.weight(1f))
            Metric("同比", yoy?.let { percent(it) } ?: "—", Jz.colors.ink2, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontFamily = FontFamily.Monospace)
    }
}

private fun percent(value: Double): String {
    val sign = if (value > 0) "+" else ""
    return "$sign${"%.1f".format(value * 100)}%"
}

@Composable
private fun AnnualBars(months: List<MonthAggregate>) {
    val values = (1..12).map { index ->
        months.firstOrNull { it.month.monthValue == index }?.expenseCents ?: 0L
    }
    val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val accent = Jz.colors.accent
    val lineHard = Jz.colors.lineHard
    var selected by remember(values) { mutableIntStateOf(LocalDate.now().monthValue - 1) }
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        selected = ((offset.x / size.width) * 12).toInt().coerceIn(0, 11)
                    }
                },
        ) {
            val gap = 6.dp.toPx()
            val bar = (size.width - gap * 11) / 12f
            values.forEachIndexed { index, value ->
                val h = (value.toFloat() / max.toFloat()) * size.height
                drawRect(
                    color = if (index == selected) accent else lineHard,
                    topLeft = Offset(index * (bar + gap), size.height - h),
                    size = Size(bar, h.coerceAtLeast(2f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("1", "3", "5", "7", "9", "11").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "%d 月 · 支出 %s".format(selected + 1, Money.format(values[selected])),
            style = MaterialTheme.typography.bodyMedium,
            color = Jz.colors.ink2,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun DailyLine(daily: List<DailyTotal>) {
    if (daily.isEmpty()) {
        Text("所选区间没有支出", style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
        return
    }
    val max = daily.maxOfOrNull { it.amountCents }?.coerceAtLeast(1L) ?: 1L
    val accent = Jz.colors.accent
    var selected by remember(daily) { mutableIntStateOf(daily.lastIndex) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .pointerInput(daily) {
                detectTapGestures { offset ->
                    val step = size.width.toFloat() / (daily.size - 1).coerceAtLeast(1)
                    selected = (offset.x / step).roundToInt().coerceIn(0, daily.lastIndex)
                }
            },
    ) {
        val step = if (daily.size <= 1) size.width else size.width / (daily.size - 1)
        var previous: Offset? = null
        daily.forEachIndexed { index, item ->
            val point = Offset(
                x = index * step,
                y = size.height - (item.amountCents.toFloat() / max.toFloat()) * (size.height - 4.dp.toPx()) - 2.dp.toPx(),
            )
            previous?.let { drawLine(accent, it, point, strokeWidth = 2.dp.toPx()) }
            previous = point
        }
        val point = Offset(
            x = selected * step,
            y = size.height - (daily[selected].amountCents.toFloat() / max.toFloat()) * (size.height - 4.dp.toPx()) - 2.dp.toPx(),
        )
        drawCircle(accent, 3.dp.toPx(), point)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(daily.first().date.toString(), style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
        Text(daily.last().date.toString(), style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "${daily[selected].date} · 支出 ${Money.format(daily[selected].amountCents)}",
        style = MaterialTheme.typography.bodyMedium,
        color = Jz.colors.ink2,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun CategoryBars(items: List<CategoryTotal>) {
    val max = items.maxOfOrNull { it.amountCents }?.coerceAtLeast(1L) ?: 1L
    Column {
        items.take(8).forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, color = Jz.colors.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(74.dp))
                Box(Modifier.weight(1f).height(8.dp).background(Jz.colors.track)) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.amountCents.toFloat() / max.toFloat())
                            .height(8.dp)
                            .background(Jz.colors.accent),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "¥${Money.format(item.amountCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Jz.colors.ink2,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
@Composable
private fun CustomRangeDialog(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onDismiss: () -> Unit,
    onApply: (LocalDate, LocalDate) -> Unit,
) {
    var start by remember { mutableStateOf(initialStart.toString()) }
    var end by remember { mutableStateOf(initialEnd.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义区间") },
        text = {
            Column {
                Text(
                    "格式 YYYY-MM-DD，包含首尾两天。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Jz.colors.ink2,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it.take(10); error = null },
                    label = { Text("开始日期") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it.take(10); error = null },
                    label = { Text("结束日期") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error!!, color = Jz.colors.expense, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedStart = runCatching { LocalDate.parse(start.trim()) }.getOrNull()
                val parsedEnd = runCatching { LocalDate.parse(end.trim()) }.getOrNull()
                when {
                    parsedStart == null || parsedEnd == null -> error = "日期格式应为 YYYY-MM-DD"
                    parsedStart.isAfter(parsedEnd) -> error = "开始日期不能晚于结束日期"
                    else -> onApply(parsedStart, parsedEnd)
                }
            }) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}


