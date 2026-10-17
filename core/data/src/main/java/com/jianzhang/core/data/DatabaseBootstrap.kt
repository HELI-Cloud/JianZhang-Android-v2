package com.jianzhang.core.data

import android.content.Context
import com.jianzhang.core.database.AppDatabase
import com.jianzhang.core.database.LegacyImporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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

/**
 * Process-wide, start-once database bootstrap.
 *
 * [await] is called from every repository flow collection, so after the
 * database is ready it must be a pure read. Re-entering [initialize] there
 * used to publish [BootstrapState.Loading] again, which made the root UI flip
 * between the startup spinner and the ledger forever.
 */
@Singleton
class DatabaseBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: DatabaseKeyManager,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val startGate = BootstrapStartGate()
    private val _state = MutableStateFlow<BootstrapState>(BootstrapState.Loading)
    val state: StateFlow<BootstrapState> = _state.asStateFlow()

    @Volatile
    private var instance: AppDatabase? = null

    @Volatile
    private var job: Job? = null

    fun start() {
        if (instance != null) return
        if (!startGate.tryStart()) return
        job = scope.launch { initialize() }
    }

    /**
     * Only a failed bootstrap may be restarted from the UI. A ready database is
     * never closed or reopened by a retry.
     */
    fun retry() {
        if (instance != null) return
        if (job?.isActive == true) return
        startGate.reset()
        _state.value = BootstrapState.Loading
        start()
    }

    suspend fun await(): AppDatabase {
        instance?.let { return it }
        start()
        val current = state.first { it !is BootstrapState.Loading }
        if (current is BootstrapState.Failed) throw IllegalStateException(current.message)
        return instance ?: throw IllegalStateException("数据库尚未准备完成")
    }

    private suspend fun initialize() = mutex.withLock {
        instance?.let {
            if (_state.value !is BootstrapState.Ready) {
                _state.value = BootstrapState.Ready()
            }
            return@withLock
        }
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
        } catch (cancelled: CancellationException) {
            runCatching { database?.close() }
            throw cancelled
        } catch (t: Throwable) {
            runCatching { database?.close() }
            instance = null
            _state.value = BootstrapState.Failed(t.message ?: "数据库初始化失败")
        }
    }
}

/**
 * Single-attempt gate for bootstrap initialization. Kept as a tiny pure Kotlin
 * unit so the "never initialize twice" contract is directly testable.
 */
internal class BootstrapStartGate {
    private val lock = Any()
    private var started = false

    fun tryStart(): Boolean = synchronized(lock) {
        if (started) {
            false
        } else {
            started = true
            true
        }
    }

    fun reset() = synchronized(lock) {
        started = false
    }
}
