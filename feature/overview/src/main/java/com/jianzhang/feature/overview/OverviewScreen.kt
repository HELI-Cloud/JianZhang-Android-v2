package com.jianzhang.feature.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.core.common.Dates
import com.jianzhang.core.common.Money
import com.jianzhang.core.designsystem.AmountText
import com.jianzhang.core.designsystem.EmptyState
import com.jianzhang.core.designsystem.GaugeBar
import com.jianzhang.core.designsystem.GaugeScale
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.MetricRow
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.designsystem.SectionTitle
import com.jianzhang.core.model.CategoryTotal
import com.jianzhang.core.model.MonthAggregate
import com.jianzhang.core.model.TxnType

@Composable
fun OverviewScreen(
    onOpenRecord: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenPlanning: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                PageHeader(
                    title = "刻度",
                    subtitle = "把每一笔，放回它该在的位置",
                    trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ChevronLeft,
                            contentDescription = "上个月",
                            tint = Jz.colors.ink3,
                            modifier = Modifier.size(22.dp).clickable(onClick = viewModel::previousMonth),
                        )
                        Text(
                            Dates.cnMonth(state.month),
                            style = MaterialTheme.typography.labelLarge,
                            color = Jz.colors.ink2,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = "下个月",
                            tint = if (state.month < java.time.YearMonth.now()) Jz.colors.ink3 else Jz.colors.ink.copy(alpha = 0.18f),
                            modifier = Modifier.size(22.dp).clickable(enabled = state.month < java.time.YearMonth.now(), onClick = viewModel::nextMonth),
                        )
                    }
                },
                )
            }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Jz.colors.surface)
                    .padding(horizontal = 18.dp, vertical = 20.dp),
            ) {
                Text("净值", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink2)
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥", color = Jz.colors.ink2, fontSize = 19.sp, modifier = Modifier.padding(bottom = 7.dp, end = 4.dp))
                    AmountText(
                        cents = state.netWorth.totalCents,
                        large = true,
                        showSign = false,
                    )
                }
                Spacer(Modifier.height(16.dp))
                JzDivider()
                MetricRow("本月支出", Money.format(state.summary.expenseCents), Jz.colors.expense)
                MetricRow("收入", Money.format(state.summary.incomeCents), Jz.colors.income)
                MetricRow(
                    "结余",
                    (if (state.summary.netCents >= 0) "+" else "-") + Money.format(kotlin.math.abs(state.summary.netCents)),
                    if (state.summary.netCents >= 0) Jz.colors.income else Jz.colors.expense,
                )
                Sparkline(state.dailyExpenses, Modifier.fillMaxWidth().height(42.dp).padding(top = 8.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                SectionTitle("近 6 月", "点按折线读数")
            }
        }
        item { SixMonthTrend(state.monthlyTrend) }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                SectionTitle("本月预算", if (state.totalBudgetCents > 0) "${Dates.daysLeftInMonth()} 天" else "未设置")
                if (state.totalBudgetCents <= 0) {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onOpenBudget).padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("还没有预算", color = Jz.colors.ink2, modifier = Modifier.weight(1f))
                        Text("去设置", color = Jz.colors.accent, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    GaugeBar(progress = state.budgetProgress, over = state.budgetProgress > 1f)
                    Spacer(Modifier.height(5.dp))
                    GaugeScale(
                        listOf(
                            "0",
                            Money.formatAxis(state.totalBudgetCents / 2),
                            Money.formatAxis(state.totalBudgetCents),
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = if (state.budgetProgress > 1f) {
                                "已超支 ${Money.format(-state.budgetRemaining)}"
                            } else {
                                "已用 ${(state.budgetProgress * 100).roundToInt().coerceAtLeast(0)}%"
                            },
                            color = if (state.budgetProgress > 1f) Jz.colors.expense else Jz.colors.ink2,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "日均可用 ${Money.format(state.dailyAvailable)}",
                            color = Jz.colors.ink2,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (state.budgetRows.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        state.budgetRows.take(4).forEach { row ->
                            val progress = if (row.amountCents <= 0) 0f else row.spentCents.toFloat() / row.amountCents
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = row.name,
                                    color = Jz.colors.ink2,
                                    modifier = Modifier.weight(0.30f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Box(Modifier.weight(0.42f).padding(horizontal = 8.dp)) {
                                    GaugeBar(progress, height = 10.dp, ticks = 8, over = progress > 1f)
                                }
                                Text(
                                    text = Money.formatCompact(row.spentCents),
                                    color = if (progress > 1f) Jz.colors.expense else Jz.colors.ink,
                                    modifier = Modifier.weight(0.28f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                SectionTitle("分类占比", "点按扇区读数")
            }
        }
        if (state.categoryTotals.isNotEmpty()) {
            item { CategoryDonut(state.categoryTotals) }
        }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                SectionTitle("分类明细", "按金额排序")
            }
        }
        if (state.categoryTotals.isEmpty()) {
            item {
                EmptyState(
                    "本月还没有支出",
                    "记下第一笔，刻度就会开始移动",
                    modifier = Modifier.padding(horizontal = Jz.space.page),
                )
            }
        } else {
            items(state.categoryTotals.take(8), key = { it.categoryId ?: -1L }) { total ->
                val max = state.categoryTotals.maxOfOrNull { it.amountCents }?.coerceAtLeast(1) ?: 1
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Jz.space.page, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = total.name,
                        color = Jz.colors.ink2,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(0.30f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.weight(0.42f).padding(horizontal = 8.dp)) {
                        GaugeBar(total.amountCents.toFloat() / max, height = 12.dp, ticks = 16)
                    }
                    Text(
                        text = Money.formatCompact(total.amountCents),
                        color = Jz.colors.ink,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(0.28f),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                SectionTitle("今天", "最近 ${state.recent.size} 笔")
            }
        }
        if (state.recent.isEmpty()) {
            item {
                EmptyState(
                    "还没有流水",
                    "点底部的 + 记第一笔",
                    modifier = Modifier.padding(horizontal = Jz.space.page),
                    action = {
                    Text("记一笔", color = Jz.colors.accent, modifier = Modifier.clickable(onClick = onOpenRecord), style = MaterialTheme.typography.labelLarge)
                    },
                )
            }
        } else {
            items(state.recent, key = { it.txn.id }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(row.txn.id) }
                        .padding(horizontal = Jz.space.page, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            row.txn.note.ifBlank { row.categoryName },
                            style = MaterialTheme.typography.titleMedium,
                            color = Jz.colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${Dates.weekday(row.txn.date)} · ${row.categoryName}${row.accountName?.let { " · $it" } ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Jz.colors.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        AmountText(row.txn.amountCents, row.txn.type)
                        Text(Dates.iso(row.txn.date).substring(5), style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
                    }
                }
                JzDivider(Modifier.padding(horizontal = Jz.space.page))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(7.dp).background(color))
}

@Composable
private fun SixMonthTrend(months: List<MonthAggregate>, modifier: Modifier = Modifier) {
    if (months.isEmpty()) {
        EmptyState("还没有趋势", "连续记录几个月之后，这里会出现走势。")
        return
    }
    var selected by remember(months) { mutableIntStateOf(months.lastIndex) }
    val maxValue = months.maxOf { maxOf(it.incomeCents, it.expenseCents) }.coerceAtLeast(1L)
    val expenseColor = Jz.colors.expense
    val incomeColor = Jz.colors.income
    val gridColor = Jz.colors.line.copy(alpha = 0.45f)
    val markerColor = Jz.colors.accent.copy(alpha = 0.30f)
    Column(modifier.padding(horizontal = Jz.space.page)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(expenseColor)
            Spacer(Modifier.width(5.dp))
            Text("支出", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink2)
            Spacer(Modifier.width(14.dp))
            LegendDot(incomeColor)
            Spacer(Modifier.width(5.dp))
            Text("收入", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink2)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(126.dp)
                .pointerInput(months) {
                    detectTapGestures { offset ->
                        val step = size.width.toFloat() / (months.size - 1).coerceAtLeast(1)
                        selected = (offset.x / step).roundToInt().coerceIn(0, months.lastIndex)
                    }
                },
        ) {
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val usable = (bottom - top).coerceAtLeast(1f)
            repeat(4) { i ->
                val y = top + usable * i / 3f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val step = if (months.size <= 1) 0f else size.width / (months.size - 1)
            fun x(index: Int): Float = step * index
            fun y(value: Long): Float = bottom - (value.toFloat() / maxValue) * usable
            fun pathFor(value: (MonthAggregate) -> Long): Path {
                val path = Path()
                months.forEachIndexed { index, month ->
                    if (index == 0) path.moveTo(x(index), y(value(month)))
                    else path.lineTo(x(index), y(value(month)))
                }
                return path
            }
            drawLine(
                markerColor,
                Offset(x(selected), top),
                Offset(x(selected), bottom),
                1.dp.toPx(),
            )
            drawPath(pathFor { it.expenseCents }, expenseColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawPath(pathFor { it.incomeCents }, incomeColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            months.forEachIndexed { index, month ->
                drawCircle(expenseColor, 2.4.dp.toPx(), Offset(x(index), y(month.expenseCents)))
                drawCircle(incomeColor, 2.4.dp.toPx(), Offset(x(index), y(month.incomeCents)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            months.forEachIndexed { index, month ->
                Text(
                    "${month.month.monthValue}月",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == selected) Jz.colors.ink else Jz.colors.ink3,
                )
            }
        }
        val current = months[selected.coerceIn(months.indices)]
        Spacer(Modifier.height(8.dp))
        Text(
            "${current.month.monthValue} 月 · 支出 ${Money.format(current.expenseCents)} · 收入 ${Money.format(current.incomeCents)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Jz.colors.ink2,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun CategoryDonut(items: List<CategoryTotal>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val total = items.sumOf { it.amountCents }.coerceAtLeast(1L)
    var selected by remember(items) { mutableIntStateOf(0) }
    val palette = listOf(
        Jz.colors.accent,
        Jz.colors.income,
        Jz.colors.expense,
        Jz.colors.accent.copy(alpha = 0.62f),
        Jz.colors.income.copy(alpha = 0.55f),
        Jz.colors.expense.copy(alpha = 0.55f),
        Jz.colors.ink2,
        Jz.colors.ink3,
    )
    Column(modifier.padding(horizontal = Jz.space.page)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(140.dp)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(items) {
                            detectTapGestures { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                val radius = minOf(size.width, size.height) / 2f
                                val distance = hypot(dx, dy)
                                if (distance in radius * 0.46f..radius) {
                                    var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                    if (degrees < 0f) degrees += 360f
                                    var start = 0f
                                    items.forEachIndexed { index, item ->
                                        val sweep = item.amountCents.toFloat() / total * 360f
                                        if (degrees >= start && degrees < start + sweep) selected = index
                                        start += sweep
                                    }
                                }
                            }
                        },
                ) {
                    val stroke = 15.dp.toPx()
                    var start = -90f
                    items.forEachIndexed { index, item ->
                        val sweep = item.amountCents.toFloat() / total * 360f
                        val color = palette[index % palette.size]
                        drawArc(
                            color = if (index == selected) color else color.copy(alpha = 0.50f),
                            startAngle = start + 1.2f,
                            sweepAngle = (sweep - 2.4f).coerceAtLeast(0.6f),
                            useCenter = false,
                            style = Stroke(width = if (index == selected) stroke + 3.dp.toPx() else stroke),
                        )
                        start += sweep
                    }
                }
                val current = items[selected.coerceIn(items.indices)]
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        current.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Jz.colors.ink3,
                        maxLines = 1,
                    )
                    Text(
                        Money.format(current.amountCents),
                        style = MaterialTheme.typography.titleMedium,
                        color = Jz.colors.ink,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "${current.amountCents * 100 / total}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Jz.colors.accent,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                items.take(5).forEachIndexed { index, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = index }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).background(palette[index % palette.size]))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index == selected) Jz.colors.ink else Jz.colors.ink2,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Money.format(item.amountCents),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Jz.colors.ink2,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun Sparkline(values: List<Long>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val color = Jz.colors.accent.copy(alpha = 0.72f)
    Canvas(modifier) {
        val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val step = if (values.size <= 1) size.width else size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = step * index
            val y = size.height - (value.toFloat() / max) * size.height * 0.82f - 2.dp.toPx()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx()))
    }
}