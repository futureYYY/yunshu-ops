package com.rackexcel.mobile.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.max

data class PreparedImages(
    val originalDataUri: String,
    val cropDataUris: List<String>,
)

object ImagePreprocessor {
    private const val MAX_DIMENSION = 1800
    private const val QUALITY_MAX_DIMENSION = 960
    private val cropRanges = listOf(
        0f to 0.24f,
        0.10f to 0.43f,
        0.36f to 0.70f,
        0.64f to 1f,
    )

    fun prepare(context: Context, uri: Uri): PreparedImages {
        val bytes = openImage(context, uri)?.use { it.readBytes() }
            ?: error("读取图片失败")
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("图片格式解析失败")
        val scaled = scaleDown(source)
        val original = encode(scaled)
        val crops = cropRanges.map { (startRatio, endRatio) ->
            val start = (scaled.height * startRatio).toInt().coerceIn(0, scaled.height - 1)
            val end = (scaled.height * endRatio).toInt().coerceIn(start + 1, scaled.height)
            val crop = Bitmap.createBitmap(scaled, 0, start, scaled.width, end - start)
            encode(crop).also { crop.recycle() }
        }
        if (scaled !== source) source.recycle()
        scaled.recycle()
        return PreparedImages(original, crops)
    }

    /** Decodes a bounded preview so quality feedback never allocates a full camera bitmap. */
    fun inspectQuality(context: Context, uri: Uri): ImageQualityResult {
        val bytes = openImage(context, uri)?.use { it.readBytes() }
            ?: error("读取图片失败")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("图片格式解析失败")
        val largest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (largest / (sample * 2) >= QUALITY_MAX_DIMENSION) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("图片格式解析失败")
        return try {
            ImageQualityInspector.inspect(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= MAX_DIMENSION) return source
        val ratio = MAX_DIMENSION.toFloat() / largest.toFloat()
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun encode(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "图片压缩失败" }
        return "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun openImage(context: Context, uri: Uri): InputStream? =
        if (uri.scheme.equals("file", ignoreCase = true)) {
            uri.path?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf(File::isFile)?.let(::FileInputStream)
        } else {
            context.contentResolver.openInputStream(uri)
        }
}
