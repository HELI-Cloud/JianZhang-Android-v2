package com.jianzhang.feature.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianzhang.core.common.Dates
import com.jianzhang.core.common.Money
import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.data.NewRecurring
import com.jianzhang.core.data.SettingsStore
import com.jianzhang.core.model.Account
import com.jianzhang.core.model.AppSettings
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.MonthSummary
import com.jianzhang.core.model.RecurrenceFrequency
import com.jianzhang.core.model.RecurringRule
import com.jianzhang.core.model.ReminderItem
import com.jianzhang.core.model.ReminderKind
import com.jianzhang.core.model.ReminderLevel
import com.jianzhang.core.model.Tag
import com.jianzhang.core.model.TagUsage
import com.jianzhang.core.model.TxnType
import com.jianzhang.core.model.TxnWithCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class BudgetRow(
    val categoryId: Long?,
    val name: String,
    val budgetCents: Long,
    val spentCents: Long,
)

data class BudgetUiState(
    val month: YearMonth = YearMonth.now(),
    val totalBudgetCents: Long = 0,
    val expenseCents: Long = 0,
    val rows: List<BudgetRow> = emptyList(),
    val categories: List<Category> = emptyList(),
    val loading: Boolean = true,
) {
    val remaining: Long get() = totalBudgetCents - expenseCents
    val progress: Float get() = if (totalBudgetCents <= 0) 0f else expenseCents.toFloat() / totalBudgetCents
    val dailyAvailable: Long get() {
        val left = Dates.daysLeftInMonth()
        return if (left <= 0) 0 else (remaining / (left + 1)).coerceAtLeast(0)
    }
}

data class PlanningUiState(
    val settings: AppSettings = AppSettings(),
    val budgets: BudgetUiState = BudgetUiState(),
    val accounts: List<Account> = emptyList(),
    val recurring: List<RecurringRule> = emptyList(),
    val tags: List<TagUsage> = emptyList(),
    val reminders: List<ReminderItem> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanningViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    private val _savedRecurring = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val savedRecurring = _savedRecurring.asSharedFlow()

    private val budgetState = month.flatMapLatest { m ->
        combine(
            repository.observeBudgets(m),
            repository.observeSums(m),
            repository.categories,
            repository.observeTransactions(m),
        ) { budgets, summary, categories, transactions ->
            val total = budgets.firstOrNull { it.categoryId == null }?.amountCents ?: budgets.sumOf { it.amountCents }
            val spent = transactions.filter { it.txn.type == TxnType.EXPENSE }
                .groupBy { it.txn.categoryId }
                .mapValues { (_, value) -> value.sumOf { it.txn.amountCents } }
            val names = categories.associate { it.id to it.name }
            BudgetUiState(
                month = m,
                totalBudgetCents = total,
                expenseCents = summary.expenseCents,
                rows = budgets.filter { it.categoryId != null }.map {
                    BudgetRow(it.categoryId, names[it.categoryId] ?: "未分类", it.amountCents, spent[it.categoryId] ?: 0L)
                },
                categories = categories,
                loading = false,
            )
        }
    }

    private data class Partial(
        val settings: AppSettings,
        val recurring: List<RecurringRule>,
        val tags: List<TagUsage>,
        val monthTxns: List<TxnWithCategory>,
        val dismissed: Set<String>,
    )

    private val partial = combine(
        repository.recurring,
        repository.tagUsage,
        repository.observeTransactions(YearMonth.now()),
        repository.dismissedReminderKeys,
        settingsStore.settings,
    ) { recurring, tags, monthTxns, dismissed, settings ->
        Partial(settings, recurring, tags, monthTxns, dismissed)
    }

    val state = combine(partial, budgetState, repository.activeAccounts) { p, budgets, accounts ->
        PlanningUiState(
            settings = p.settings,
            budgets = budgets,
            accounts = accounts,
            recurring = p.recurring,
            tags = p.tags,
            reminders = buildReminders(p.recurring, p.monthTxns, budgets, p.settings, p.dismissed),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanningUiState())

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }

    fun saveBudget(categoryId: Long?, amountText: String) {
        val cents = Money.parseToCents(amountText) ?: 0L
        viewModelScope.launch {
            runCatching { repository.saveBudget(month.value, categoryId, cents) }
                .onFailure { error.value = it.message ?: "预算保存失败" }
        }
    }

    fun saveRecurring(
        id: Long?,
        type: TxnType,
        amountText: String,
        categoryId: Long?,
        accountId: Long?,
        note: String,
        frequency: RecurrenceFrequency,
        interval: Int,
        start: LocalDate,
        endDate: LocalDate?,
        autoCreate: Boolean,
        enabled: Boolean,
    ) {
        val cents = Money.parseToCents(amountText) ?: 0L
        if (cents <= 0) {
            error.value = "请输入金额"
            return
        }
        if (endDate != null && endDate.isBefore(start)) {
            error.value = "结束日期不能早于起始日期"
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.saveRecurring(
                    id,
                    NewRecurring(
                        type = type,
                        amountCents = cents,
                        categoryId = categoryId,
                        accountId = accountId,
                        note = note,
                        frequency = frequency,
                        intervalCount = interval,
                        weekday = start.dayOfWeek.value,
                        dayOfMonth = start.dayOfMonth,
                        monthOfYear = start.monthValue,
                        startDate = start,
                        endDate = endDate,
                        autoCreate = autoCreate,
                        enabled = enabled,
                    ),
                )
            }.onSuccess { savedId ->
                _savedRecurring.emit(savedId)
            }.onFailure { error.value = it.message ?: "周期规则保存失败" }
        }
    }

    fun toggleRecurring(rule: RecurringRule) {
        viewModelScope.launch {
            runCatching { repository.setRecurringEnabled(rule, !rule.enabled) }
                .onFailure { error.value = it.message ?: "周期规则更新失败" }
        }
    }

    fun deleteRecurring(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteRecurring(id) }
                .onFailure { error.value = it.message ?: "周期规则删除失败" }
        }
    }

    fun runRecurring() {
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.runRecurring(autoOnly = false) }
                .onFailure { error.value = it.message ?: "执行失败" }
            busy.value = false
        }
    }

    fun confirmRecurring(id: Long) {
        viewModelScope.launch {
            runCatching { repository.confirmRecurring(id) }
                .onFailure { error.value = it.message ?: "确认失败" }
        }
    }

    fun saveTag(id: Long?, name: String, color: String) {
        viewModelScope.launch {
            runCatching { repository.saveTag(id, name, color) }
                .onFailure { error.value = it.message ?: "标签保存失败" }
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteTag(id) }
                .onFailure { error.value = it.message ?: "标签删除失败" }
        }
    }

    fun dismiss(key: String) {
        viewModelScope.launch { repository.dismissReminder(key) }
    }

    fun clearDismissed() {
        viewModelScope.launch { repository.clearDismissedReminders() }
    }

    fun saveReminderSettings(settings: AppSettings) {
        viewModelScope.launch {
            runCatching {
                settingsStore.setReminderFlags(
                    budgetAlert = settings.budgetAlert,
                    billAlert = settings.billAlert,
                    dailyReminder = settings.dailyReminder,
                    weeklyReminder = settings.weeklyReminder,
                    weeklyWeekday = settings.weeklyReminderWeekday,
                    systemNotify = settings.systemNotify,
                )
            }.onFailure { error.value = it.message ?: "提醒设置保存失败" }
        }
    }

    fun saveReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            runCatching { settingsStore.setReminderTime(hour, minute) }
                .onFailure { error.value = it.message ?: "提醒时间保存失败" }
        }
    }
    fun clearError() { error.value = null }

    private fun buildReminders(
        recurring: List<RecurringRule>,
        monthTxns: List<TxnWithCategory>,
        budgets: BudgetUiState,
        settings: AppSettings,
        dismissed: Set<String>,
    ): List<ReminderItem> {
        val items = mutableListOf<ReminderItem>()
        val today = LocalDate.now()
        if (settings.budgetAlert && budgets.totalBudgetCents > 0) {
            val ratio = budgets.expenseCents.toFloat() / budgets.totalBudgetCents
            if (ratio >= 0.8f) {
                val key = "budget:${budgets.month}:${if (ratio >= 1f) "over" else "near"}"
                items += ReminderItem(
                    key = key,
                    kind = ReminderKind.BUDGET,
                    title = if (ratio >= 1f) "本月预算已超支" else "本月预算接近上限",
                    body = if (ratio >= 1f) {
                        "已支出 ¥${Money.format(budgets.expenseCents)}，超出 ¥${Money.format(-budgets.remaining)}"
                    } else {
                        "已使用 ${(ratio * 100).toInt()}%，剩余 ¥${Money.format(budgets.remaining)}"
                    },
                    level = if (ratio >= 1f) ReminderLevel.DANGER else ReminderLevel.WARN,
                    route = "budget",
                )
            }
        }
        if (settings.billAlert) {
            recurring.filter { it.enabled && !it.nextDate.isAfter(today.plusDays(3)) }.forEach { rule ->
                items += ReminderItem(
                    key = "bill:${rule.id}:${rule.nextDate}",
                    kind = ReminderKind.BILL,
                    title = "周期账单即将到期",
                    body = "${rule.note.ifBlank { "固定收支" }} · ¥${Money.format(rule.amountCents)} · ${rule.nextDate}",
                    level = if (rule.nextDate.isBefore(today)) ReminderLevel.WARN else ReminderLevel.INFO,
                    route = "recurring",
                    ruleId = rule.id,
                    confirmable = true,
                )
            }
        }
        if (settings.dailyReminder && !monthTxns.any { it.txn.date == today }) {
            items += ReminderItem(
                key = "daily:$today",
                kind = ReminderKind.DAILY,
                title = "今天还没有记账",
                body = "花一分钟把今天的收支记下来。",
                level = ReminderLevel.INFO,
                route = "record",
            )
        }
        if (settings.weeklyReminder && today.dayOfWeek.value % 7 == settings.weeklyReminderWeekday % 7) {
            val (start, end) = Dates.weekStart(today) to today
            val weekExpense = monthTxns.filter { it.txn.date in start..end && it.txn.type == TxnType.EXPENSE }
                .sumOf { it.txn.amountCents }
            items += ReminderItem(
                key = "weekly:${Dates.iso(start)}",
                kind = ReminderKind.WEEKLY,
                title = "本周账本小结",
                body = "本周支出 ¥${Money.format(weekExpense)}，打开统计看看结构。",
                level = ReminderLevel.INFO,
                route = "stats",
            )
        }
        return items.filter { it.key !in dismissed }
    }
}

