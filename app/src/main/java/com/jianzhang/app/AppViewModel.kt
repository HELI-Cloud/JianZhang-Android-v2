package com.jianzhang.app

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianzhang.core.data.BootstrapState
import com.jianzhang.core.data.DatabaseBootstrap
import com.jianzhang.core.data.LockSession
import com.jianzhang.core.data.SettingsStore
import com.jianzhang.core.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val bootstrap: DatabaseBootstrap,
    lockSession: LockSession,
) : ViewModel() {
    /**
     * Settings are only exposed after bootstrap finishes legacy settings
     * migration. Restarting the DataStore flow at that point guarantees the
     * first emitted value is the migrated one, so an upgraded install with a
     * lock enabled can never render the ledger for a frame.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val settings = bootstrap.state
        .flatMapLatest { state ->
            if (state is BootstrapState.Ready) {
                settingsStore.settings.map<AppSettings, AppSettings?> { it }
            } else {
                flowOf(null)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )
    val lockUnlocked = lockSession.unlocked.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false,
    )
    val bootstrapState = bootstrap.state.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        BootstrapState.Loading,
    )

    init {
        bootstrap.start()
        viewModelScope.launch {
            settingsStore.settings.distinctUntilChanged().collect { settings ->
                runCatching { ReminderScheduler.sync(context, settings) }
                    .onFailure { Log.w("JianZhangReminder", "Unable to sync reminder schedule", it) }
            }
        }
    }

    fun retry() = bootstrap.retry()
}



