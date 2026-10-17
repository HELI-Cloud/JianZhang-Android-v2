package com.jianzhang.app

import com.jianzhang.core.common.Dates
import com.jianzhang.core.common.Money
import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.model.AppSettings
import com.jianzhang.core.model.ReminderItem
import com.jianzhang.core.model.ReminderKind
import com.jianzhang.core.model.ReminderLevel
import com.jianzhang.core.model.TxnType
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

object ReminderEngine {
    suspend fun build(repository: LedgerRepository, settings: AppSettings): List<ReminderItem> {
        val dismissed = repository.dismissedReminderKeys.first()
        val today = LocalDate.now()
        val out = mutableListOf<ReminderItem>()

        if (settings.budgetAlert) {
            val month = YearMonth.from(today)
            val budgets = repository.observeBudgets(month).first()
            val summary = repository.observeSums(month).first()
            val total = budgets.firstOrNull { it.categoryId == null }?.amountCents
                ?: budgets.sumOf { it.amountCents }
            if (total > 0L) {
                val ratio = summary.expenseCents.toFloat() / total
                if (ratio >= 0.8f) {
                    val over = ratio >= 1f
                    out += ReminderItem(
                        key = "budget:$month:${if (over) "over" else "near"}",
                        kind = ReminderKind.BUDGET,
                        title = if (over) "本月预算已超支" else "本月预算接近上限",
                        body = if (over) {
                            "已支出 ¥${Money.format(summary.expenseCents)}，超出 ¥${Money.format(summary.expenseCents - total)}"
                        } else {
                            "已使用 ${(ratio * 100).toInt()}%，剩余 ¥${Money.format(total - summary.expenseCents)}"
                        },
                        level = if (over) ReminderLevel.DANGER else ReminderLevel.WARN,
                        route = "budget",
                    )
                }
            }
            val categories = repository.categories.first().associateBy { it.id }
            val totals = repository.observeCategoryTotals(month, TxnType.EXPENSE).first()
                .associateBy { it.categoryId }
            budgets.filter { it.categoryId != null }.forEach { budget ->
                val spent = totals[budget.categoryId]?.amountCents ?: 0L
                if (spent > budget.amountCents) {
                    val name = categories[budget.categoryId]?.name ?: "分类"
                    out += ReminderItem(
                        key = "budget-cat:$month:${budget.categoryId}",
                        kind = ReminderKind.BUDGET,
                        title = "「$name」预算超支",
                        body = "已支出 ¥${Money.format(spent)}，超出 ¥${Money.format(spent - budget.amountCents)}",
                        level = ReminderLevel.WARN,
                        route = "budget",
                    )
                }
            }
        }

        if (settings.billAlert) {
            val horizon = today.plusDays(3)
            repository.recurring.first().forEach { rule ->
                if (!rule.enabled || rule.nextDate.isAfter(horizon)) return@forEach
                if (rule.endDate != null && rule.nextDate.isAfter(rule.endDate)) return@forEach
                val overdue = rule.nextDate.isBefore(today)
                out += ReminderItem(
                    key = "bill:${rule.id}:${rule.nextDate}",
                    kind = ReminderKind.BILL,
                    title = when {
                        !rule.autoCreate -> if (overdue) "周期账目待确认（已到期）" else "周期账目待确认"
                        overdue -> "周期账目已到期"
                        else -> "周期账目即将到期"
                    },
                    body = "${rule.note.ifBlank { if (rule.type == TxnType.EXPENSE) "固定支出" else "固定收入" }} · " +
                        "¥${Money.format(rule.amountCents)} · ${Dates.iso(rule.nextDate)}",
                    level = if (overdue) ReminderLevel.DANGER else ReminderLevel.INFO,
                    route = "recurring",
                    ruleId = rule.id,
                    confirmable = !rule.autoCreate,
                )
            }
        }

        if (settings.dailyReminder) {
            val now = LocalTime.now()
            val target = LocalTime.of(settings.reminderHour.coerceIn(0, 23), settings.reminderMinute.coerceIn(0, 59))
            if (!now.isBefore(target)) {
                val (start, end) = Dates.dateRange(today, today)
                if (repository.countTransactions(start, end) == 0) {
                    out += ReminderItem(
                        key = "daily:$today",
                        kind = ReminderKind.DAILY,
                        title = "今天还没有记账",
                        body = "已过 ${"%02d:%02d".format(target.hour, target.minute)}，花一分钟补上今天的收支。",
                        level = ReminderLevel.INFO,
                        route = "ledger",
                    )
                }
            }
        }

        if (settings.weeklyReminder) {
            val targetDow = if (settings.weeklyReminderWeekday == 0) DayOfWeek.SUNDAY
            else DayOfWeek.of(settings.weeklyReminderWeekday.coerceIn(1, 7))
            val reached = today.dayOfWeek.value >= if (targetDow == DayOfWeek.SUNDAY) 7 else targetDow.value
            if (reached) {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val (start, end) = Dates.dateRange(monday, today)
                if (repository.countTransactions(start, end) == 0) {
                    out += ReminderItem(
                        key = "weekly:${Dates.iso(monday)}",
                        kind = ReminderKind.WEEKLY,
                        title = "本周账本还是空的",
                        body = "本周（${Dates.iso(monday)} 至 ${Dates.iso(today)}）还没有记录，记得补记。",
                        level = ReminderLevel.INFO,
                        route = "ledger",
                    )
                }
            }
        }

        return out.filter { it.key !in dismissed }
    }

    fun parseTime(hour: Int, minute: Int): LocalTime =
        LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
}