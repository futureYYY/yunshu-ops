package com.rackexcel.mobile.network

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetryExecutorTest {
    @Test
    fun retries_three_times_after_the_initial_failure_then_succeeds() = runBlocking {
        var calls = 0
        val result = RetryExecutor(maxRetries = 3, delayMillis = { 0L }).attempt {
            calls += 1
            if (calls < 4) error("transient")
            "ready"
        }

        assertEquals("ready", result.value)
        assertEquals(4, calls)
        assertEquals(3, result.retryCount)
        assertNull(result.error)
    }

    @Test
    fun reports_terminal_failure_after_three_repair_attempts() = runBlocking {
        val repairs = mutableListOf<Int>()
        val result = RetryExecutor(maxRetries = 3, delayMillis = { 0L }).attempt(
            onRetry = { retry, _ -> repairs += retry },
        ) {
            error("bad response")
        }

        assertEquals(listOf(1, 2, 3), repairs)
        assertEquals(3, result.retryCount)
        assertTrue(result.error is IllegalStateException)
    }

    @Test
    fun stops_immediately_when_the_error_is_not_retryable() = runBlocking {
        var calls = 0
        val repairs = mutableListOf<Int>()

        val result = RetryExecutor(maxRetries = 3, delayMillis = { 0L }).attempt(
            onRetry = { retry, _ -> repairs += retry },
            shouldRetry = { false },
        ) {
            calls += 1
            error("invalid configuration")
        }

        assertEquals(1, calls)
        assertEquals(emptyList(), repairs)
        assertEquals(0, result.retryCount)
        assertTrue(result.error is IllegalStateException)
    }
}
