package com.jianzhang.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.jianzhang.core.common.Dates
import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.model.Account
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.MonthSummary
import com.jianzhang.core.model.Tag
import com.jianzhang.core.model.Transfer
import com.jianzhang.core.model.TxnType
import com.jianzhang.core.model.TxnWithCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

sealed interface LedgerEntry {
    data class DateHeader(val date: LocalDate) : LedgerEntry
    data class Row(val value: TxnWithCategory) : LedgerEntry
}

data class LedgerUiState(
    val month: YearMonth = YearMonth.now(),
    val type: TxnType? = null,
    val transfersOnly: Boolean = false,
    val accountId: Long? = null,
    val tagId: Long? = null,
    val query: String = "",
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val transfers: List<Transfer> = emptyList(),
    val expense: Long = 0,
    val income: Long = 0,
    val totalCount: Int = 0,
    val dailyExpense: Map<String, Long> = emptyMap(),
    val loading: Boolean = true,
)

private data class Filter(
    val month: YearMonth,
    val type: TxnType?,
    val transfersOnly: Boolean,
    val accountId: Long?,
    val tagId: Long?,
    val query: String,
)

private data class FilterBundle(
    val summary: MonthSummary,
    val count: Int,
    val dailyExpense: Map<String, Long>,
    val transfers: List<Transfer>,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val repository: LedgerRepository,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val type = MutableStateFlow<TxnType?>(null)
    private val transfersOnly = MutableStateFlow(false)
    private val account = MutableStateFlow<Long?>(null)
    private val tag = MutableStateFlow<Long?>(null)
    private val query = MutableStateFlow("")

    val selected = MutableStateFlow<Set<Long>>(emptySet())

    private val typeOrTransfer = combine(type, transfersOnly) { type, only -> type to only }

    private val filter = combine(month, typeOrTransfer, account, tag, query.debounce(220)) { month, type, account, tag, query ->
        Filter(month, type.first, type.second, account, tag, query.trim())
    }

    /**
     * Paged transaction stream. Room invalidates the PagingSource on every write,
     * so edits, deletes and imports refresh the visible window without loading the
     * whole month into memory.
     */
    val entries: Flow<PagingData<LedgerEntry>> = filter.flatMapLatest { f ->
        if (f.transfersOnly) {
            return@flatMapLatest flowOf(PagingData.empty<LedgerEntry>())
        }
        val (start, end) = Dates.monthRange(f.month)
        repository.filteredPager(start, end, f.type, f.accountId, f.tagId, f.query)
            .map { paging ->
                val entries: PagingData<LedgerEntry> = paging.map { row -> LedgerEntry.Row(row) }
                entries.insertSeparators { before, after ->
                    val previousDate = when (before) {
                        is LedgerEntry.DateHeader -> before.date
                        is LedgerEntry.Row -> before.value.txn.date
                        null -> null
                    }
                    val nextDate = (after as? LedgerEntry.Row)?.value?.txn?.date
                    if (nextDate != null && nextDate != previousDate) {
                        LedgerEntry.DateHeader(nextDate)
                    } else {
                        null
                    }
                }
            }
    }.cachedIn(viewModelScope)

    private val bundle = filter.flatMapLatest { f ->
        val (start, end) = Dates.monthRange(f.month)
        combine(
            repository.observeFilteredSums(start, end, f.type, f.accountId, f.tagId, f.query),
            repository.observeFilteredCount(start, end, f.type, f.accountId, f.tagId, f.query),
            repository.observeDailySums(f.month),
            repository.observeTransfers(f.month),
        ) { summary, count, daily, transfers ->
            FilterBundle(
                summary = summary,
                count = count,
                dailyExpense = daily.filter { it.amountCents > 0 }.associate { Dates.iso(it.date) to it.amountCents },
                transfers = transfers,
            )
        }
    }

    val state = combine(
        filter,
        bundle,
        repository.accounts,
        repository.categories,
        repository.tags,
    ) { f, b, accounts, categories, tags ->
        LedgerUiState(
            month = f.month,
            type = f.type,
            transfersOnly = f.transfersOnly,
            accountId = f.accountId,
            tagId = f.tagId,
            query = f.query,
            accounts = accounts,
            categories = categories,
            tags = tags,
            transfers = b.transfers,
            expense = b.summary.expenseCents,
            income = b.summary.incomeCents,
            totalCount = b.count,
            dailyExpense = b.dailyExpense,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUiState())

    fun setType(value: TxnType?) {
        transfersOnly.value = false
        type.value = value
    }

    fun setTransfersOnly() {
        type.value = null
        transfersOnly.value = true
    }
    fun setAccount(value: Long?) { account.value = value }
    fun setTag(value: Long?) { tag.value = value }
    fun setQuery(value: String) { query.value = value }
    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() {
        val next = month.value.plusMonths(1)
        if (next <= YearMonth.now()) month.value = next
    }

    fun toggle(id: Long) {
        selected.value = if (id in selected.value) selected.value - id else selected.value + id
    }

    fun clearSelection() { selected.value = emptySet() }

    suspend fun deleteSelected(): Int {
        val ids = selected.value
        if (ids.isEmpty()) return 0
        repository.deleteTransactions(ids)
        selected.value = emptySet()
        return ids.size
    }

    suspend fun setCategoryForSelected(categoryId: Long?) {
        val ids = selected.value.toList()
        if (ids.isEmpty()) return
        repository.bulkSetCategory(ids, categoryId)
        selected.value = emptySet()
    }

    suspend fun setAccountForSelected(accountId: Long?) {
        val ids = selected.value.toList()
        if (ids.isEmpty()) return
        repository.bulkSetAccount(ids, accountId)
        selected.value = emptySet()
    }

    suspend fun addTagToSelected(tagId: Long) {
        val ids = selected.value.toList()
        if (ids.isEmpty()) return
        repository.bulkAddTag(ids, tagId)
        selected.value = emptySet()
    }

    suspend fun deleteTransfer(id: Long) {
        repository.deleteTransfer(id)
    }
}
