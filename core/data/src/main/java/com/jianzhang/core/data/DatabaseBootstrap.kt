package com.jianzhang.core.data

import android.content.Context
import com.jianzhang.core.database.AppDatabase
import com.jianzhang.core.database.LegacyImporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BootstrapState {
    data object Loading : BootstrapState
    data class Ready(val message: String = "") : BootstrapState
    data class Failed(val message: String) : BootstrapState
}

@Singleton
class DatabaseBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: DatabaseKeyManager,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<BootstrapState>(BootstrapState.Loading)
    val state: StateFlow<BootstrapState> = _state.asStateFlow()

    @Volatile
    private var instance: AppDatabase? = null
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { initialize() }
    }

    fun retry() {
        job?.cancel()
        job = scope.launch { initialize() }
    }

    suspend fun await(): AppDatabase {
        start()
        val current = state.first { it !is BootstrapState.Loading }
        if (current is BootstrapState.Failed) throw IllegalStateException(current.message)
        return instance ?: throw IllegalStateException("数据库尚未准备完成")
    }

    private suspend fun initialize() = mutex.withLock {
        _state.value = BootstrapState.Loading
        var database: AppDatabase? = null
        try {
            settingsStore.migrateLegacyIfNeeded()
            val passphrase = keyManager.passphrase()
            database = AppDatabase.build(context, passphrase)
            database.openHelper.writableDatabase
            val migration = LegacyImporter(database).importIfNeeded(context)
            if (!migration.ok) {
                throw IllegalStateException(migration.message)
            }
            LedgerRepository(database).seedIfEmpty()
            instance = database
            _state.value = BootstrapState.Ready(migration.message)
        } catch (t: Throwable) {
            runCatching { database?.close() }
            instance = null
            _state.value = BootstrapState.Failed(t.message ?: "数据库初始化失败")
        }
    }
}
