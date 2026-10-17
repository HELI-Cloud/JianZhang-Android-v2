package com.jianzhang.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One-shot navigation requested by a notification tap. */
object NavigationRequests {
    private val _target = MutableStateFlow<String?>(null)
    val target = _target.asStateFlow()

    fun request(target: String) { _target.value = target }
    fun consume() { _target.value = null }
}