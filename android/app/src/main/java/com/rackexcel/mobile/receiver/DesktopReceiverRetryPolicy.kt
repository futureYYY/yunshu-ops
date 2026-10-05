package com.rackexcel.mobile.receiver

import java.io.IOException

object DesktopReceiverRetryPolicy {
    const val MAX_RETRIES = 3

    fun delayMillis(retryAttempt: Int): Long = when (retryAttempt.coerceAtLeast(1)) {
        1 -> 1_000L
        2 -> 2_000L
        else -> 4_000L
    }

    fun shouldRetry(error: Throwable): Boolean = when (error) {
        is DesktopReceiverException -> error.httpStatus?.let(::isRetryableStatus)
            ?: (error.type == DesktopReceiverErrorType.NETWORK)
        is IOException -> true
        else -> false
    }

    fun isRetryableStatus(status: Int): Boolean = status == 408 || status == 429 || status in 500..599
}
