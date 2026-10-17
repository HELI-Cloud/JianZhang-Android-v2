package com.jianzhang.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianzhang.core.common.Dates
import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.model.CategoryTotal
import com.jianzhang.core.model.DailyTotal
import com.jianzhang.core.model.MonthAggregate
import com.jianzhang.core.model.MonthSummary
import com.jianzhang.core.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class StatsRange(val label: String) {
    MONTH("本月"),
    THREE_MONTHS("近 3 月"),
    TWELVE_MONTHS("近 12 月"),
    YEAR("本年"),
    CUSTOM("自定义"),
}

data class StatsUiState(
    val range: StatsRange = StatsRange.MONTH,
    val start: LocalDate = LocalDate.now().withDayOfMonth(1),
    val end: LocalDate = LocalDate.now(),
    val summary: MonthSummary = MonthSummary(YearMonth.now(), 0, 0),
    val categories: List<CategoryTotal> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val selectedTagId: Long? = null,
    val daily: List<DailyTotal> = emptyList(),
    val months: List<MonthAggregate> = emptyList(),
    val previousExpense: Long = 0,
    val yearAgoExpense: Long = 0,
    val loading: Boolean = true,
) {
    val expenseMom: Double? get() = if (previousExpense <= 0) null else (summary.expenseCents - previousExpense).toDouble() / previousExpense
    val expenseYoy: Double? get() = if (yearAgoExpense <= 0) null else (summary.expenseCents - yearAgoExpense).toDouble() / yearAgoExpense
}

private data class StatsQuery(val range: StatsRange, val tagId: Long?, val customStart: LocalDate, val customEnd: LocalDate)
private data class CoreStats(
    val summary: MonthSummary,
    val categories: List<CategoryTotal>,
    val tags: List<Tag>,
    val daily: List<DailyTotal>,
    val months: List<MonthAggregate>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: LedgerRepository,
) : ViewModel() {
    private val range = MutableStateFlow(StatsRange.MONTH)
    private val tagId = MutableStateFlow<Long?>(null)
    private val customStart = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    private val customEnd = MutableStateFlow(LocalDate.now())

    val state = combine(range, tagId, customStart, customEnd) { r, t, s, e -> StatsQuery(r, t, s, e) }
        .flatMapLatest { query ->
            val (start, end) = bounds(query)
            val (prevStart, prevEnd) = previousBounds(start, end)
            val (yearStart, yearEnd) = yearAgoBounds(start, end)
            val summaryFlow = if (query.tagId == null) {
                repository.observeRangeSums(Dates.iso(start), Dates.iso(end.plusDays(1)))
            } else {
                repository.observeTagSummary(Dates.iso(start), Dates.iso(end.plusDays(1)), query.tagId)
            }
            val categoryFlow = if (query.tagId == null) {
                repository.observeCategoryTotals(Dates.iso(start), Dates.iso(end.plusDays(1)))
            } else {
                repository.observeTagCategoryTotals(Dates.iso(start), Dates.iso(end.plusDays(1)), tagId = query.tagId)
            }
            val core = combine(
                summaryFlow,
                categoryFlow,
                repository.tags,
                repository.observeDailySums(Dates.iso(start), Dates.iso(end.plusDays(1))),
                repository.observeMonthlyAggregates(
                    Dates.iso(YearMonth.now().withMonth(1).atDay(1)),
                    Dates.iso(LocalDate.now().plusDays(1)),
                ),
            ) { summary, categories, tags, daily, months ->
                CoreStats(summary, categories, tags, daily, months)
            }
            val previous = repository.observeRangeSums(Dates.iso(prevStart), Dates.iso(prevEnd.plusDays(1)))
            val yearAgo = repository.observeRangeSums(Dates.iso(yearStart), Dates.iso(yearEnd.plusDays(1)))
            combine(core, previous, yearAgo) { c, p, y ->
                StatsUiState(
                    range = query.range,
                    start = start,
                    end = end,
                    summary = c.summary,
                    categories = c.categories,
                    tags = c.tags,
                    selectedTagId = query.tagId,
                    daily = c.daily,
                    months = c.months,
                    previousExpense = p.expenseCents,
                    yearAgoExpense = y.expenseCents,
                    loading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun selectRange(value: StatsRange) { range.value = value }

    fun selectTag(id: Long?) { tagId.value = id }

    fun setCustom(start: LocalDate, end: LocalDate) {
        if (start.isAfter(end)) return
        customStart.value = start
        customEnd.value = end
        range.value = StatsRange.CUSTOM
    }

    private fun bounds(query: StatsQuery): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (query.range) {
            StatsRange.MONTH -> YearMonth.from(today).atDay(1) to today
            StatsRange.THREE_MONTHS -> YearMonth.from(today).minusMonths(2).atDay(1) to today
            StatsRange.TWELVE_MONTHS -> YearMonth.from(today).minusMonths(11).atDay(1) to today
            StatsRange.YEAR -> today.withDayOfYear(1) to today
            StatsRange.CUSTOM -> query.customStart to query.customEnd
        }
    }

    private fun previousBounds(start: LocalDate, end: LocalDate): Pair<LocalDate, LocalDate> =
        start.minusMonths(1) to end.minusMonths(1)

    private fun yearAgoBounds(start: LocalDate, end: LocalDate): Pair<LocalDate, LocalDate> =
        start.minusYears(1) to end.minusYears(1)
}
