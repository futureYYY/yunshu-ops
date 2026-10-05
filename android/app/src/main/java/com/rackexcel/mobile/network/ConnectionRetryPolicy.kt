package com.rackexcel.mobile.network

/**
 * Connection checks should self-heal around brief service or network pressure,
 * while configuration and request-shape errors are reported immediately.
 */
object ConnectionRetryPolicy {
    fun shouldRetry(error: Throwable): Boolean {
        val status = (error as? VisionApiException)?.httpStatus
        return when (status) {
            null -> true
            408, 409, 425, 429 -> true
            in 500..599 -> true
            else -> false
        }
    }
}
