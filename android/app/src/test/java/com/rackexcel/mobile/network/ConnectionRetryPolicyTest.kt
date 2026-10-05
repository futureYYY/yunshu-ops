package com.rackexcel.mobile.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionRetryPolicyTest {
    @Test
    fun retries_transient_http_service_errors() {
        assertTrue(ConnectionRetryPolicy.shouldRetry(VisionApiException("busy", httpStatus = 503)))
        assertTrue(ConnectionRetryPolicy.shouldRetry(VisionApiException("limited", httpStatus = 429)))
    }

    @Test
    fun does_not_retry_invalid_request_or_model_errors() {
        assertFalse(ConnectionRetryPolicy.shouldRetry(VisionApiException("bad request", httpStatus = 400)))
        assertFalse(ConnectionRetryPolicy.shouldRetry(VisionApiException("missing model", httpStatus = 404)))
    }
}
