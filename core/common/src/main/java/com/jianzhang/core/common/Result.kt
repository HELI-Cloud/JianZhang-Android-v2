package com.jianzhang.core.common

sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : DataResult<Nothing>
}

inline fun <T> runData(block: () -> T): DataResult<T> =
    try {
        DataResult.Success(block())
    } catch (t: Throwable) {
        DataResult.Failure(t.message ?: "操作失败", t)
    }