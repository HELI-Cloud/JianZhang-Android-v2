package com.jianzhang.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianzhang.core.common.Dates
import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.model.Budget
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.CategoryTotal
import com.jianzhang.core.model.MonthAggregate
import com.jianzhang.core.model.MonthSummary
import com.jianzhang.core.model.NetWorth
import com.jianzhang.core.model.TxnWithCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

data class BudgetRow(
    val categoryId: Long?,
    val name: String,
    val amountCents: Long,
    val spentCents: Long,
)

data class OverviewUiState(
    val month: YearMonth = YearMonth.now(),
    val netWorth: NetWorth = NetWorth(0, 0, 0),
    val summary: MonthSummary = MonthSummary(YearMonth.now(), 0, 0),
    val budgetRows: List<BudgetRow> = emptyList(),
    val totalBudgetCents: Long = 0,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val dailyExpenses: List<Long> = emptyList(),
    val monthlyTrend: List<MonthAggregate> = emptyList(),
    val recent: List<TxnWithCategory> = emptyList(),
    val loading: Boolean = true,
) {
    val budgetProgress: Float
        get() = if (totalBudgetCents <= 0) 0f else summary.expenseCents.toFloat() / totalBudgetCents
    val budgetRemaining: Long get() = totalBudgetCents - summary.expenseCents
    val dailyAvailable: Long
        get() = if (Dates.daysLeftInMonth() <= 0) 0 else (budgetRemaining / (Dates.daysLeftInMonth() + 1)).coerceAtLeast(0)
}

private data class OverviewCore(
    val netWorth: NetWorth,
    val sums: MonthSummary,
    val budgets: List<Budget>,
    val transactions: List<TxnWithCategory>,
    val categories: List<Category>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    repository: LedgerRepository,
) : ViewModel() {
    private val monthFlow = MutableStateFlow(YearMonth.now())

    val state = monthFlow.flatMapLatest { month ->
        val trendStart = Dates.iso(month.minusMonths(5).atDay(1))
        val trendEnd = Dates.iso(month.plusMonths(1).atDay(1))
        combine(
            repository.observeNetWorth(),
            repository.observeSums(month),
            repository.observeBudgets(month),
            repository.observeTransactions(month),
            repository.categories,
        ) { netWorth, sums, budgets, transactions, categories ->
            OverviewCore(netWorth, sums, budgets, transactions, categories)
        }.combine(repository.observeMonthlyAggregates(trendStart, trendEnd)) { core, trendRows ->
            val netWorth = core.netWorth
            val sums = core.sums
            val budgets = core.budgets
            val transactions = core.transactions
            val categories = core.categories
            val categoryNames = categories.associate { it.id to it.name }
            val totalBudget = budgets.firstOrNull { it.categoryId == null }?.amountCents
                ?: budgets.sumOf { it.amountCents }
            val expenseByCategory = transactions.filter { it.txn.type.name == "EXPENSE" }
                .groupBy { it.txn.categoryId }
                .map { (id, list) ->
                    CategoryTotal(id, categoryNames[id] ?: list.firstOrNull()?.categoryName ?: "未分类", list.sumOf { it.txn.amountCents })
                }
                .sortedByDescending { it.amountCents }
            val budgetRows = budgets.mapNotNull { budget ->
                if (budget.categoryId == null) null else BudgetRow(
                    categoryId = budget.categoryId,
                    name = categoryNames[budget.categoryId] ?: "未分类",
                    amountCents = budget.amountCents,
                    spentCents = expenseByCategory.firstOrNull { it.categoryId == budget.categoryId }?.amountCents ?: 0,
                )
            }
            val days = month.lengthOfMonth()
            val daily = LongArray(days)
            transactions.filter { it.txn.type.name == "EXPENSE" }.forEach { t ->
                val day = t.txn.date.dayOfMonth
                if (day in 1..days) daily[day - 1] += t.txn.amountCents
            }
            OverviewUiState(
                month = month,
                netWorth = netWorth,
                summary = MonthSummary(month, sums.incomeCents, sums.expenseCents),
                budgetRows = budgetRows,
                totalBudgetCents = totalBudget,
                categoryTotals = expenseByCategory,
                dailyExpenses = daily.toList(),
                monthlyTrend = (5 downTo 0).map { offset ->
                    val target = month.minusMonths(offset.toLong())
                    trendRows.firstOrNull { it.month == target } ?: MonthAggregate(target, 0, 0)
                },
                recent = transactions.take(7),
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    fun previousMonth() {
        monthFlow.value = monthFlow.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = monthFlow.value.plusMonths(1)
        if (next <= YearMonth.now()) monthFlow.value = next
    }
}