package com.rackexcel.mobile.network

import org.json.JSONObject
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionProbePayloadTest {
    @Test
    fun probe_image_is_a_real_jpeg_data_uri_not_a_placeholder_payload() {
        val dataUri = ConnectionProbePayload.imageDataUri
        assertTrue(dataUri.startsWith("data:image/jpeg;base64,"))
        val bytes = Base64.getDecoder().decode(dataUri.substringAfter(","))
        assertTrue(bytes.size > 100)
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, bytes[1].toInt() and 0xFF)
        assertEquals(0xFF, bytes[bytes.lastIndex - 1].toInt() and 0xFF)
        assertEquals(0xD9, bytes.last().toInt() and 0xFF)
    }

    @Test
    fun probe_body_puts_the_valid_data_uri_in_openai_image_url_shape() {
        val root = JSONObject(ConnectionProbePayload.body("vision-model"))
        val content = root.getJSONArray("messages")
            .getJSONObject(1)
            .getJSONArray("content")
        val image = content.getJSONObject(1)
            .getJSONObject("image_url")
            .getString("url")

        assertEquals("vision-model", root.getString("model"))
        assertEquals(ConnectionProbePayload.imageDataUri, image)
    }
}
