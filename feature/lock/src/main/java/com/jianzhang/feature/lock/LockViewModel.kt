package com.jianzhang.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianzhang.core.common.LockoutPolicy
import com.jianzhang.core.common.LockoutState
import com.jianzhang.core.common.PasswordHasher
import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.data.LockGuard
import com.jianzhang.core.data.LockSession
import com.jianzhang.core.data.SettingsStore
import com.jianzhang.core.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val repository: LedgerRepository,
    private val session: LockSession,
) : ViewModel() {
    val settings = settingsStore.settings
        .map<AppSettings, AppSettings?> { it }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )
    val sessionUnlocked = session.unlocked.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false,
    )
    val error = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val failedAttempts = MutableStateFlow(0)
    val lockedUntil = MutableStateFlow(0L)

    /** 串行化 guard 的读改写，避免并发点击绕过失败次数。 */
    private val guardMutex = Mutex()

    init {
        viewModelScope.launch {
            runCatching { settingsStore.lockGuard() }.onSuccess { guard ->
                val now = System.currentTimeMillis()
                when {
                    guard.lockedUntil > now -> {
                        failedAttempts.value = guard.failedAttempts
                        lockedUntil.value = guard.lockedUntil
                        error.value = lockoutMessage(guard.lockedUntil)
                    }
                    guard.lockedUntil != 0L -> {
                        // 锁定期已结束：清除过期锁定，允许重新尝试。
                        runCatching { settingsStore.setLockGuard(0, 0L) }
                        failedAttempts.value = 0
                        lockedUntil.value = 0L
                    }
                    else -> {
                        // 未锁定但已有失败记录：进程重启不得把计数清零，否则可绕过五次限制。
                        failedAttempts.value = guard.failedAttempts
                        lockedUntil.value = 0L
                    }
                }
            }
        }
    }

    fun clearError() { error.value = null }
    fun clearMessage() { message.value = null }

    private fun lockoutMessage(until: Long): String =
        LockoutPolicy.message(LockoutState(lockedUntil = until), System.currentTimeMillis())

    private fun validateNew(password: String, confirm: String): String? = when {
        password.length < 4 -> "密码至少 4 位"
        password.length > 64 -> "密码最多 64 位"
        password != confirm -> "两次输入的密码不一致"
        else -> null
    }

    private suspend fun verify(password: String, settings: AppSettings): Boolean {
        val format = withContext(Dispatchers.Default) {
            PasswordHasher.verifyStored(password, settings.lockSalt, settings.lockHash)
        }
        if (format != null && format != PasswordHasher.StoredHashFormat.PBKDF2) {
            val salt = PasswordHasher.newSalt()
            settingsStore.setLock(true, PasswordHasher.pbkdf2Hash(password, salt), salt)
        }
        return format != null
    }

    private suspend fun resetGuard() {
        runCatching { settingsStore.setLockGuard(0, 0L) }
        failedAttempts.value = 0
        lockedUntil.value = 0L
    }

    fun enable(password: String, confirm: String) {
        validateNew(password, confirm)?.let { error.value = it; return }
        if (settings.value == null) {
            error.value = "正在读取安全设置，请稍候"
            return
        }
        viewModelScope.launch {
            busy.value = true
            runCatching {
                val salt = PasswordHasher.newSalt()
                val hash = withContext(Dispatchers.Default) { PasswordHasher.pbkdf2Hash(password, salt) }
                settingsStore.setLock(true, hash, salt)
                resetGuard()
                session.unlock()
            }.onSuccess {
                message.value = "应用锁已开启"
            }.onFailure {
                error.value = it.message ?: "应用锁开启失败"
            }
            busy.value = false
        }
    }

    /**
     * Single guarded password check used by unlock, change-password and disable.
     * The guard read-modify-write stays serialized, so no UI path can bypass the
     * persisted five-attempt lockout.
     */
    private suspend fun verifyGuarded(password: String): Boolean = guardMutex.withLock {
        val now = System.currentTimeMillis()
        val guard = runCatching { settingsStore.lockGuard() }.getOrDefault(LockGuard())
        if (guard.lockedUntil > now) {
            failedAttempts.value = guard.failedAttempts
            lockedUntil.value = guard.lockedUntil
            error.value = lockoutMessage(guard.lockedUntil)
            return@withLock false
        }
        val settings = settings.value
        if (settings == null) {
            error.value = "正在读取安全设置，请稍候"
            return@withLock false
        }
        if (!settings.lockEnabled) {
            resetGuard()
            return@withLock true
        }

        val ok = runCatching { verify(password, settings) }.getOrDefault(false)
        if (ok) {
            resetGuard()
        } else {
            val next = LockoutPolicy.onFailure(
                LockoutState(guard.failedAttempts, guard.lockedUntil),
                now,
            )
            runCatching { settingsStore.setLockGuard(next.failedAttempts, next.lockedUntil) }
            failedAttempts.value = next.failedAttempts
            lockedUntil.value = next.lockedUntil
            error.value = if (next.lockedUntil > now) {
                lockoutMessage(next.lockedUntil)
            } else {
                LockoutPolicy.wrongPasswordMessage(next)
            }
        }
        ok
    }

    suspend fun unlock(password: String): Boolean {
        busy.value = true
        return try {
            val ok = verifyGuarded(password)
            if (ok) session.unlock()
            ok
        } finally {
            busy.value = false
        }
    }

    fun changePassword(current: String, next: String, confirm: String) {
        validateNew(next, confirm)?.let { error.value = it; return }
        viewModelScope.launch {
            busy.value = true
            try {
                if (!verifyGuarded(current)) return@launch
                runCatching {
                    val salt = PasswordHasher.newSalt()
                    val hash = withContext(Dispatchers.Default) { PasswordHasher.pbkdf2Hash(next, salt) }
                    settingsStore.setLock(true, hash, salt)
                    resetGuard()
                    session.unlock()
                }.onSuccess { message.value = "密码已更新" }
                    .onFailure { error.value = it.message ?: "密码更新失败" }
            } finally {
                busy.value = false
            }
        }
    }

    fun disable(current: String) {
        viewModelScope.launch {
            busy.value = true
            try {
                if (!verifyGuarded(current)) return@launch
                runCatching {
                    settingsStore.clearLock()
                    resetGuard()
                }.onSuccess {
                    session.unlock()
                    message.value = "应用锁已关闭"
                }.onFailure { error.value = it.message ?: "关闭失败" }
            } finally {
                busy.value = false
            }
        }
    }

    fun lockNow() = session.lock()

    fun resetEverything() {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                repository.resetAllData()
                settingsStore.clearLock()
                resetGuard()
                session.unlock()
            }.onSuccess { message.value = "账本已清空，应用锁已移除" }
                .onFailure { error.value = it.message ?: "重置失败" }
            busy.value = false
        }
    }

}

