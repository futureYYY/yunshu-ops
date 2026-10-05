package com.rackexcel.mobile.network

import com.rackexcel.mobile.storage.ModelConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VisionApiClientTest {
    @Test
    fun check_connection_validates_target_model_and_minimal_visual_response() = runBlocking {
        val paths = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val request = chain.request()
            paths += request.url.encodedPath
            val body = when (request.url.encodedPath) {
                "/v1/models" -> """{"data":[{"id":"example-model"}]}"""
                "/v1/chat/completions" -> """{"choices":[{"message":{"content":"{\"ready\":true}"}}]}"""
                else -> """{"error":{"message":"bad path"}}"""
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody())
                .build()
        }).build()

        val result = VisionApiClient(http).checkConnection(
            ModelConfig("https://example.test/v1", "example-model", "key"),
        )

        assertEquals("example-model", result.model)
        assertTrue(result.visualCheckPassed)
        assertTrue(result.latencyMillis >= 0)
        assertEquals(listOf("/v1/models", "/v1/chat/completions"), paths)
    }

    @Test
    fun preserves_http_status_for_connection_retry_decisions() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Service Unavailable")
                .body("""{\"error\":{\"message\":\"busy\"}}""".toResponseBody())
                .build()
        }.build()

        val error = assertFailsWith<VisionApiException> {
            VisionApiClient(http).checkConnection(ModelConfig("https://example.test/v1", "example-model", "key"))
        }

        assertEquals(503, error.httpStatus)
    }
}
