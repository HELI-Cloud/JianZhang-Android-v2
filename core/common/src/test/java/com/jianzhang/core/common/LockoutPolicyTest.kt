package com.jianzhang.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LockoutPolicyTest {
    @Test
    fun `five consecutive failures create a thirty second lockout`() {
        var state = LockoutState()
        repeat(4) { state = LockoutPolicy.onFailure(state, now = 1_000L) }
        assertThat(state.failedAttempts).isEqualTo(4)
        assertThat(state.lockedUntil).isEqualTo(0L)

        state = LockoutPolicy.onFailure(state, now = 2_000L)
        assertThat(state.failedAttempts).isEqualTo(0)
        assertThat(state.lockedUntil).isEqualTo(32_000L)
        assertThat(LockoutPolicy.isLocked(state, 31_999L)).isTrue()
        assertThat(LockoutPolicy.isLocked(state, 32_000L)).isFalse()
    }

    @Test
    fun `remaining time rounds up and never goes negative`() {
        val state = LockoutState(lockedUntil = 31_001L)
        assertThat(LockoutPolicy.remainingSeconds(state, 1_000L)).isEqualTo(31L)
        assertThat(LockoutPolicy.remainingSeconds(state, 32_000L)).isEqualTo(0L)
        assertThat(LockoutPolicy.message(state, 1_000L)).contains("31 秒")
    }

    @Test
    fun `wrong password message counts down exactly`() {
        assertThat(LockoutPolicy.wrongPasswordMessage(LockoutState(failedAttempts = 1)))
            .isEqualTo("密码不正确，还可尝试 4 次")
        assertThat(LockoutPolicy.wrongPasswordMessage(LockoutState(failedAttempts = 4)))
            .isEqualTo("密码不正确，还可尝试 1 次")
    }
}
