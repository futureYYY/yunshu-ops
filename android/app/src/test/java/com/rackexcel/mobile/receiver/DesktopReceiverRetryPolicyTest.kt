package com.rackexcel.mobile.receiver

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopReceiverRetryPolicyTest {
    @Test
    fun usesTheProtocolBackoffSchedule() {
        assertEquals(1_000L, DesktopReceiverRetryPolicy.delayMillis(1))
        assertEquals(2_000L, DesktopReceiverRetryPolicy.delayMillis(2))
        assertEquals(4_000L, DesktopReceiverRetryPolicy.delayMillis(3))
    }

    @Test
    fun retriesTransportAndTransientServerFailures() {
        assertTrue(DesktopReceiverRetryPolicy.shouldRetry(IOException("connection reset")))
        assertTrue(DesktopReceiverRetryPolicy.shouldRetry(DesktopReceiverException.http(408, "timeout")))
        assertTrue(DesktopReceiverRetryPolicy.shouldRetry(DesktopReceiverException.http(429, "busy")))
        assertTrue(DesktopReceiverRetryPolicy.shouldRetry(DesktopReceiverException.http(503, "unavailable")))
    }

    @Test
    fun stopsForCredentialsAndProtocolErrors() {
        assertFalse(DesktopReceiverRetryPolicy.shouldRetry(DesktopReceiverException.http(401, "unauthorized")))
        assertFalse(DesktopReceiverRetryPolicy.shouldRetry(DesktopReceiverException.http(409, "mismatch")))
        assertFalse(DesktopReceiverRetryPolicy.shouldRetry(DesktopReceiverException.http(415, "xlsx required")))
    }
}
