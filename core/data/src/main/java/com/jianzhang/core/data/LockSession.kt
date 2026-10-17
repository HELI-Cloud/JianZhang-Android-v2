package com.jianzhang.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-local unlock state. It is intentionally not persisted: after the
 * process is recreated the app must ask for the lock password again.
 */
@Singleton
class LockSession @Inject constructor() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked = _unlocked.asStateFlow()

    fun unlock() { _unlocked.value = true }
    fun lock() { _unlocked.value = false }
}