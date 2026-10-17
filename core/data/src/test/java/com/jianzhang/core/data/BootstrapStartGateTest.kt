package com.jianzhang.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class BootstrapStartGateTest {

    @Test
    fun `start wins only once`() {
        val gate = BootstrapStartGate()

        assertThat(gate.tryStart()).isTrue()
        assertThat(gate.tryStart()).isFalse()
        assertThat(gate.tryStart()).isFalse()
    }

    @Test
    fun `reset allows a new attempt after failure`() {
        val gate = BootstrapStartGate()

        assertThat(gate.tryStart()).isTrue()
        gate.reset()
        assertThat(gate.tryStart()).isTrue()
        assertThat(gate.tryStart()).isFalse()
    }

    @Test
    fun `concurrent starts win exactly once`() {
        val gate = BootstrapStartGate()
        val winners = AtomicInteger(0)
        val start = CountDownLatch(1)
        val threads = (1..32).map {
            Thread {
                start.await()
                if (gate.tryStart()) winners.incrementAndGet()
            }.apply { start() }
        }

        start.countDown()
        threads.forEach { it.join() }

        assertThat(winners.get()).isEqualTo(1)
    }
}