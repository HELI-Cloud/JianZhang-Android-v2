package com.jianzhang.feature.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianzhang.core.common.Money
import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.data.NewTransaction
import com.jianzhang.core.data.SettingsStore
import com.jianzhang.core.model.Account
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.Tag
import com.jianzhang.core.model.TxnType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RecordUiState(
    val editing: Boolean = false,
    val type: TxnType = TxnType.EXPENSE,
    val amountText: String = "",
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val saving: Boolean = false,
    val error: String? = null,
) {
    val amountCents: Long get() = Money.parseToCents(amountText) ?: 0L
    val visibleCategories: List<Category> get() = categories.filter { it.type == type }
    val canSave: Boolean get() = amountCents > 0 && !saving
}

sealed interface RecordEvent {
    data class Saved(val id: Long) : RecordEvent
    data class Failed(val message: String) : RecordEvent
}

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val settingsStore: SettingsStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val editId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val _state = MutableStateFlow(
        RecordUiState(
            editing = editId != null,
            type = runCatching { TxnType.valueOf(savedStateHandle.get<String>("type") ?: "EXPENSE") }
                .getOrElse { TxnType.EXPENSE },
            amountText = savedStateHandle.get<Long>("amountCents")?.takeIf { it > 0 }
                ?.let { Money.format(it, false) } ?: "",
        ),
    )
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RecordEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val catalogue = combine(
        repository.categories,
        repository.accounts,
        repository.tags,
    ) { categories, accounts, tags -> Triple(categories, accounts, tags) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Triple(emptyList(), emptyList(), emptyList()))

    init {
        viewModelScope.launch {
            catalogue.collect { (categories, accounts, tags) ->
                _state.value = _state.value.copy(
                    categories = categories,
                    accounts = accounts,
                    tags = tags,
                )
            }
        }
        viewModelScope.launch {
            val settings = settingsStore.snapshot()
            if (editId == null) {
                _state.value = _state.value.copy(
                    type = settings.defaultType,
                    accountId = settings.defaultAccountId,
                )
            }
        }
        if (editId != null) {
            viewModelScope.launch {
                val txn = repository.transaction(editId) ?: return@launch
                val tagIds = repository.tagIds(editId)
                _state.value = _state.value.copy(
                    type = txn.type,
                    amountText = Money.format(txn.amountCents, false),
                    categoryId = txn.categoryId,
                    accountId = txn.accountId,
                    date = txn.date,
                    note = txn.note,
                    selectedTagIds = tagIds.toSet(),
                )
            }
        } else {
            viewModelScope.launch {
                val settings = settingsStore.snapshot()
                val firstCategory = repository.categories.first()
                    .firstOrNull { it.type == _state.value.type && it.id == settings.lastCategoryId }
                if (firstCategory != null) _state.value = _state.value.copy(categoryId = firstCategory.id)
            }
        }
    }

    fun setType(type: TxnType) {
        if (_state.value.type == type) return
        _state.value = _state.value.copy(
            type = type,
            categoryId = _state.value.categories
                .firstOrNull { it.type == type && it.id == _state.value.categoryId }?.id,
        )
    }

    fun chooseCategory(id: Long) {
        _state.value = _state.value.copy(categoryId = id)
    }

    fun chooseAccount(id: Long?) {
        _state.value = _state.value.copy(accountId = id)
    }

    fun chooseDate(date: LocalDate) {
        _state.value = _state.value.copy(date = date)
    }

    fun setNote(value: String) {
        _state.value = _state.value.copy(note = value.take(40), error = null)
    }

    fun toggleTag(id: Long) {
        val current = _state.value.selectedTagIds
        _state.value = _state.value.copy(
            selectedTagIds = if (id in current) current - id else current + id,
        )
    }

    fun appendKey(key: String) {
        val current = _state.value.amountText
        if (key == "." && current.contains('.')) return
        if (key == "." && current.isEmpty()) return
        val decimal = current.substringAfter('.', "")
        if (current.contains('.') && decimal.length >= 2) return
        if (!current.contains('.') && current.replace(".", "").length >= 9) return
        val next = when {
            current == "0" && key != "." -> key
            else -> current + key
        }
        _state.value = _state.value.copy(amountText = next, error = null)
    }

    fun backspace() {
        val next = _state.value.amountText.dropLast(1)
        _state.value = _state.value.copy(amountText = next, error = null)
    }

    fun clearAmount() {
        _state.value = _state.value.copy(amountText = "", error = null)
    }

    fun save() {
        val current = _state.value
        if (current.amountCents <= 0) {
            _state.value = current.copy(error = "请输入金额")
            return
        }
        if (current.saving) return
        _state.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching {
                repository.saveTransaction(
                    editId,
                    NewTransaction(
                        type = current.type,
                        amountCents = current.amountCents,
                        categoryId = current.categoryId,
                        accountId = current.accountId,
                        note = current.note,
                        date = current.date,
                        tagIds = current.selectedTagIds.toList(),
                    ),
                )
            }.onSuccess { id ->
                if (current.categoryId != null) settingsStore.setLastCategory(current.categoryId)
                _state.value = _state.value.copy(saving = false)
                _events.emit(RecordEvent.Saved(id))
            }.onFailure { error ->
                val message = error.message ?: "保存失败"
                _state.value = _state.value.copy(saving = false, error = message)
                _events.emit(RecordEvent.Failed(message))
            }
        }
    }
}