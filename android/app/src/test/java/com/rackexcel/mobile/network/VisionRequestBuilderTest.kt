package com.rackexcel.mobile.network

import com.rackexcel.mobile.RackPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisionRequestBuilderTest {
    @Test
    fun default_prompt_requests_evidence_based_risk_and_action_fields() {
        assertTrue(RackPrompt.DEFAULT.contains("risk_candidates"))
        assertTrue(RackPrompt.DEFAULT.contains("action_hints"))
        assertTrue(RackPrompt.DEFAULT.contains("evidence"))
        assertTrue(RackPrompt.DEFAULT.contains("待核验"))
    }

    @Test
    fun places_original_and_four_same_photo_crops_in_one_multimodal_request() {
        val body = VisionRequestBuilder.build(
            model = "example-model",
            systemPrompt = "STRICT PROMPT",
            originalImageDataUri = "data:image/jpeg;base64,ORIGINAL",
            cropDataUris = listOf("TOP", "UPPER", "MIDDLE", "LOWER"),
        )

        assertTrue(body.contains("\"model\":\"example-model\""))
        assertTrue(body.contains("STRICT PROMPT"))
        assertTrue(body.contains("ORIGINAL"))
        assertTrue(body.contains("同一张完整机柜原图的顶部细节"))
        assertTrue(body.contains("TOP"))
        assertTrue(body.contains("LOWER"))
        assertEquals(5, "\"type\":\"image_url\"".toRegex().findAll(body).count())
    }
}
