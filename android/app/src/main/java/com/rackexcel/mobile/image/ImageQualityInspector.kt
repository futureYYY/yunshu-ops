package com.rackexcel.mobile.image

import android.graphics.Bitmap
import android.graphics.Color

enum class ImageQualityLevel {
    READY,
    NEEDS_ATTENTION,
}

data class ImageQualityMeasurements(
    val width: Int,
    val height: Int,
    val averageLuminance: Float,
)

data class ImageQualityResult(
    val level: ImageQualityLevel,
    val label: String,
    val reasons: List<String>,
)

/**
 * Lightweight local quality gate before a photo is submitted to the vision model.
 * It deliberately avoids device- or model-dependent image analysis so the result is
 * fast, deterministic, and available without a network connection.
 */
object ImageQualityInspector {
    private const val MINIMUM_IMAGE_EDGE = 720
    private const val DARK_LUMINANCE = 0.20f
    private const val BRIGHT_LUMINANCE = 0.90f
    private const val SAMPLE_COLUMNS = 48
    private const val SAMPLE_ROWS = 64

    fun inspect(bitmap: Bitmap): ImageQualityResult = inspect(
        ImageQualityMeasurements(
            width = bitmap.width,
            height = bitmap.height,
            averageLuminance = sampleAverageLuminance(bitmap),
        ),
    )

    fun inspect(measurements: ImageQualityMeasurements): ImageQualityResult {
        val reasons = buildList {
            if (minOf(measurements.width, measurements.height) < MINIMUM_IMAGE_EDGE) {
                add("图片分辨率较低，U 位数字和设备边界可能难以读取。")
            }
            if (measurements.width > measurements.height) {
                add("照片为横向画面，建议竖向完整拍摄机柜。")
            }
            when {
                measurements.averageLuminance < DARK_LUMINANCE -> {
                    add("画面偏暗，侧轨 U 位和设备边界可能不清晰。")
                }

                measurements.averageLuminance > BRIGHT_LUMINANCE -> {
                    add("画面偏亮，反光可能遮挡侧轨 U 位和设备边界。")
                }
            }
        }
        return if (reasons.isEmpty()) {
            ImageQualityResult(
                level = ImageQualityLevel.READY,
                label = "可直接识别",
                reasons = emptyList(),
            )
        } else {
            ImageQualityResult(
                level = ImageQualityLevel.NEEDS_ATTENTION,
                label = "建议补拍",
                reasons = reasons,
            )
        }
    }

    private fun sampleAverageLuminance(bitmap: Bitmap): Float {
        val xStep = (bitmap.width / SAMPLE_COLUMNS).coerceAtLeast(1)
        val yStep = (bitmap.height / SAMPLE_ROWS).coerceAtLeast(1)
        var total = 0f
        var count = 0
        for (y in 0 until bitmap.height step yStep) {
            for (x in 0 until bitmap.width step xStep) {
                val color = bitmap.getPixel(x, y)
                total += (Color.red(color) * 0.2126f + Color.green(color) * 0.7152f + Color.blue(color) * 0.0722f) / 255f
                count++
            }
        }
        return if (count == 0) 0f else total / count
    }
}
