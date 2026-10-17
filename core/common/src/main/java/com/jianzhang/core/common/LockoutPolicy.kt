package com.jianzhang.core.common

/**
 * Pure lockout state machine. It intentionally has no Android dependencies so the
 * security policy can be unit-tested and reused by every password-entry path.
 */
data class LockoutState(
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L,
)

object LockoutPolicy {
    const val MAX_ATTEMPTS = 5
    const val LOCKOUT_MS = 30_000L

    fun isLocked(state: LockoutState, now: Long): Boolean = state.lockedUntil > now

    fun onFailure(state: LockoutState, now: Long): LockoutState {
        val attempts = state.failedAttempts + 1
        return if (attempts >= MAX_ATTEMPTS) {
            LockoutState(failedAttempts = 0, lockedUntil = now + LOCKOUT_MS)
        } else {
            LockoutState(failedAttempts = attempts, lockedUntil = 0L)
        }
    }

    fun remainingSeconds(state: LockoutState, now: Long): Long =
        ((state.lockedUntil - now).coerceAtLeast(0L) + 999L) / 1000L

    fun message(state: LockoutState, now: Long): String {
        val seconds = remainingSeconds(state, now).coerceAtLeast(1L)
        return "尝试次数过多，请 ${seconds} 秒后再试"
    }

    fun wrongPasswordMessage(state: LockoutState): String =
        "密码不正确，还可尝试 ${(MAX_ATTEMPTS - state.failedAttempts).coerceAtLeast(0)} 次"
}
