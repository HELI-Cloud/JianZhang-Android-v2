package com.jianzhang.feature.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiRequestPolicyTest {
    @Test
    fun endpointAppendsChatCompletionsOnce() {
        assertThat(AiRequestPolicy.normalizeEndpoint("https://api.deepseek.com/"))
            .isEqualTo("https://api.deepseek.com/chat/completions")
        assertThat(AiRequestPolicy.normalizeEndpoint("https://api.deepseek.com/v1"))
            .isEqualTo("https://api.deepseek.com/v1/chat/completions")
        assertThat(AiRequestPolicy.normalizeEndpoint("https://api.deepseek.com/chat/completions/"))
            .isEqualTo("https://api.deepseek.com/chat/completions")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankEndpoint() {
        AiRequestPolicy.normalizeEndpoint("  ")
    }

    @Test
    fun sanitizesNonFiniteTemperature() {
        assertThat(AiRequestPolicy.temperature(Double.NaN)).isEqualTo(0.35)
        assertThat(AiRequestPolicy.temperature(Double.POSITIVE_INFINITY)).isEqualTo(0.35)
        assertThat(AiRequestPolicy.temperature(9.0)).isEqualTo(1.5)
        assertThat(AiRequestPolicy.temperature(-1.0)).isEqualTo(0.0)
    }

    @Test
    fun clampsTimeout() {
        assertThat(AiRequestPolicy.timeoutSeconds(0)).isEqualTo(5)
        assertThat(AiRequestPolicy.timeoutSeconds(500)).isEqualTo(120)
    }
}