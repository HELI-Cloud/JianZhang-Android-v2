package com.jianzhang.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.data.NewAccount
import com.jianzhang.core.data.NewTransfer
import com.jianzhang.core.model.Account
import com.jianzhang.core.model.AccountBalance
import com.jianzhang.core.model.AccountKind
import com.jianzhang.core.model.NetWorth
import com.jianzhang.core.model.Transfer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class AccountsUiState(
    val balances: List<AccountBalance> = emptyList(),
    val netWorth: NetWorth = NetWorth(0, 0, 0),
    val transfers: List<Transfer> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repository: LedgerRepository,
) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    private val month = MutableStateFlow(YearMonth.now())

    val state = combine(
        repository.accountBalances,
        repository.observeNetWorth(),
        repository.observeTransfers(month.value),
    ) { balances, netWorth, transfers ->
        AccountsUiState(balances, netWorth, transfers, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun saveAccount(
        id: Long?,
        name: String,
        kind: AccountKind,
        initialCents: Long,
        color: String,
        icon: String,
    ) {
        viewModelScope.launch {
            runCatching { repository.saveAccount(id, NewAccount(name, kind, initialCents, color, icon)) }
                .onFailure { error.value = it.message ?: "账户保存失败" }
        }
    }

    fun archiveAccount(account: Account, archived: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setAccountArchived(account.id, archived) }
                .onFailure { error.value = it.message ?: "账户更新失败" }
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            runCatching { repository.deleteAccount(account.id) }
                .onFailure { error.value = it.message ?: "账户删除失败" }
        }
    }

    fun saveTransfer(
        id: Long?,
        fromId: Long,
        toId: Long,
        amountCents: Long,
        feeCents: Long,
        note: String,
        date: LocalDate,
    ) {
        if (amountCents <= 0) {
            error.value = "请输入转账金额"
            return
        }
        if (fromId == toId) {
            error.value = "转出与转入账户不能相同"
            return
        }
        viewModelScope.launch {
            busy.value = true
            runCatching {
                repository.saveTransfer(id, NewTransfer(fromId, toId, amountCents, feeCents, note, date))
            }.onFailure { error.value = it.message ?: "转账保存失败" }
            busy.value = false
        }
    }

    fun deleteTransfer(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteTransfer(id) }
                .onFailure { error.value = it.message ?: "转账删除失败" }
        }
    }

    fun clearError() { error.value = null }
}
