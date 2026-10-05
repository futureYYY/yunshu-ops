package com.rackexcel.mobile.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class RetryResult<T>(
    val value: T?,
    val retryCount: Int,
    val error: Throwable?,
)

/** Executes the original attempt once, followed by at most [maxRetries] repair attempts. */
class RetryExecutor(
    private val maxRetries: Int = 3,
    private val delayMillis: (Int) -> Long = { retry -> 1_000L * (1 shl (retry - 1).coerceAtMost(3)) },
) {
    init {
        require(maxRetries >= 0) { "maxRetries 必须大于或等于 0" }
    }

    suspend fun <T> attempt(
        onRetry: (suspend (retry: Int, error: Throwable) -> Unit)? = null,
        shouldRetry: (Throwable) -> Boolean = { true },
        block: suspend () -> T,
    ): RetryResult<T> {
        var retries = 0
        while (true) {
            try {
                return RetryResult(value = block(), retryCount = retries, error = null)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (retries >= maxRetries || !shouldRetry(error)) {
                    return RetryResult(value = null, retryCount = retries, error = error)
                }
                retries += 1
                onRetry?.invoke(retries, error)
                delay(delayMillis(retries).coerceAtLeast(0L))
            }
        }
    }
}
