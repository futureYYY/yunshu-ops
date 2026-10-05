package com.rackexcel.mobile.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageQualityInspectorTest {
    @Test
    fun clear_portrait_photo_is_ready_for_recognition() {
        val result = ImageQualityInspector.inspect(
            ImageQualityMeasurements(width = 1440, height = 1920, averageLuminance = 0.52f),
        )

        assertEquals(ImageQualityLevel.READY, result.level)
        assertEquals("可直接识别", result.label)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun low_resolution_dark_landscape_photo_is_marked_for_retake() {
        val result = ImageQualityInspector.inspect(
            ImageQualityMeasurements(width = 640, height = 480, averageLuminance = 0.15f),
        )

        assertEquals(ImageQualityLevel.NEEDS_ATTENTION, result.level)
        assertEquals("建议补拍", result.label)
        assertTrue(result.reasons.any { it.contains("分辨率") })
        assertTrue(result.reasons.any { it.contains("偏暗") })
        assertTrue(result.reasons.any { it.contains("横向") })
    }

    @Test
    fun overexposed_photo_is_marked_for_retake() {
        val result = ImageQualityInspector.inspect(
            ImageQualityMeasurements(width = 1080, height = 1920, averageLuminance = 0.94f),
        )

        assertEquals(ImageQualityLevel.NEEDS_ATTENTION, result.level)
        assertTrue(result.reasons.any { it.contains("偏亮") })
    }
}
