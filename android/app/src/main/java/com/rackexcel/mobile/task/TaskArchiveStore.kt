package com.rackexcel.mobile.task

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.max

data class TaskImageArchive(
    val reviewImagePath: String,
    val reviewImageUri: Uri,
    val originalImagePath: String?,
)

class TaskArchiveException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * Keeps task evidence in app-private storage. Every historical JSON record
 * references one of these generated paths rather than a picker-provided URI.
 */
class TaskArchiveStore(context: Context) {
    private val appContext = context.applicationContext

    fun archive(
        taskId: String,
        imageId: String,
        sourceUri: Uri,
        retainOriginal: Boolean,
    ): TaskImageArchive = try {
        val reviewFile = localFile(reviewRelativePath(taskId, imageId))
        writeReviewImage(sourceUri, reviewFile)
        val originalFile = if (retainOriginal) {
            localFile(originalRelativePath(taskId, imageId, appContext.contentResolver.getType(sourceUri))).also {
                copyOriginal(sourceUri, it)
            }
        } else {
            removeOriginalVariants(taskId, imageId)
            null
        }
        TaskImageArchive(
            reviewImagePath = reviewFile.absolutePath,
            reviewImageUri = Uri.fromFile(reviewFile),
            originalImagePath = originalFile?.absolutePath,
        )
    } catch (error: Exception) {
        if (error is TaskArchiveException) throw error
        throw TaskArchiveException("归档复核图片失败：${error.message ?: "读取图片失败"}", error)
    }

    fun reviewUri(reviewImagePath: String?): Uri? = reviewFile(reviewImagePath)?.let(Uri::fromFile)

    fun reviewFile(reviewImagePath: String?): File? = reviewImagePath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { isInsideRoot(it, reviewRoot()) && it.isFile }

    fun openReview(reviewImagePath: String): InputStream? = reviewFile(reviewImagePath)?.let(::FileInputStream)

    fun taskArchiveSize(taskId: String): Long = listOf(reviewTaskDirectory(taskId), originalTaskDirectory(taskId))
        .sumOf(::directorySize)

    fun deleteTaskArchive(taskId: String): Boolean = listOf(reviewTaskDirectory(taskId), originalTaskDirectory(taskId))
        .all { directory -> !directory.exists() || directory.deleteRecursively() }

    private fun writeReviewImage(sourceUri: Uri, destination: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openSource(sourceUri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw TaskArchiveException("图片格式解析失败")
        }
        val decoded = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = openSource(sourceUri).use { BitmapFactory.decodeStream(it, null, decoded) }
            ?: throw TaskArchiveException("图片格式解析失败")
        val scaled = scaleDown(source)
        try {
            destination.parentFile?.mkdirs()
            writeAtomically(destination) { output ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) { "图片压缩失败" }
            }
        } finally {
            if (scaled !== source) source.recycle()
            scaled.recycle()
        }
    }

    private fun copyOriginal(sourceUri: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        writeAtomically(destination) { output ->
            openSource(sourceUri).use { input -> input.copyTo(output) }
        }
    }

    private fun openSource(sourceUri: Uri): InputStream =
        if (sourceUri.scheme.equals("file", ignoreCase = true)) {
            val path = sourceUri.path?.takeIf { it.isNotBlank() }
                ?: throw TaskArchiveException("图片读取失败")
            FileInputStream(File(path))
        } else {
            appContext.contentResolver.openInputStream(sourceUri)
                ?: throw TaskArchiveException("图片读取失败")
        }

    private fun writeAtomically(destination: File, writer: (java.io.OutputStream) -> Unit) {
        val temporary = File(destination.parentFile, ".${destination.name}.tmp")
        runCatching { temporary.delete() }
        try {
            temporary.outputStream().buffered().use(writer)
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= MAX_REVIEW_DIMENSION) return source
        val ratio = MAX_REVIEW_DIMENSION.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var largest = max(width, height)
        while (largest / 2 >= MAX_REVIEW_DIMENSION) {
            sample *= 2
            largest /= 2
        }
        return sample
    }

    private fun removeOriginalVariants(taskId: String, imageId: String) {
        val directory = originalTaskDirectory(taskId)
        val prefix = safeSegment(imageId) + "."
        directory.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach(File::delete)
    }

    private fun reviewTaskDirectory(taskId: String): File = localFile("$REVIEW_ROOT/${safeSegment(taskId)}")

    private fun originalTaskDirectory(taskId: String): File = localFile("$ORIGINAL_ROOT/${safeSegment(taskId)}")

    private fun localFile(relativePath: String): File {
        val root = appContext.filesDir.canonicalFile
        val file = File(root, relativePath).canonicalFile
        check(isInsideRoot(file, root)) { "归档路径无效" }
        return file
    }

    private fun reviewRoot(): File = localFile(REVIEW_ROOT)

    private fun isInsideRoot(file: File, root: File): Boolean {
        val rootPath = root.canonicalPath.trimEnd(File.separatorChar) + File.separator
        return file.canonicalPath.startsWith(rootPath)
    }

    private fun directorySize(directory: File): Long = when {
        !directory.exists() -> 0L
        directory.isFile -> directory.length()
        else -> directory.listFiles()?.sumOf(::directorySize) ?: 0L
    }

    companion object {
        private const val REVIEW_ROOT = "review_images"
        private const val ORIGINAL_ROOT = "original_images"
        private const val MAX_REVIEW_DIMENSION = 1800
        private const val JPEG_QUALITY = 90

        fun reviewRelativePath(taskId: String, imageId: String): String =
            "$REVIEW_ROOT/${safeSegment(taskId)}/${safeSegment(imageId)}.jpg"

        fun originalRelativePath(taskId: String, imageId: String, mimeType: String?): String =
            "$ORIGINAL_ROOT/${safeSegment(taskId)}/${safeSegment(imageId)}.${extensionFor(mimeType)}"

        private fun safeSegment(value: String): String = value.trim()
            .replace(Regex("[^\\w.\\-\\u4e00-\\u9fff]"), "_")
            .trim('.')
            .take(96)
            .ifBlank { "item" }

        private fun extensionFor(mimeType: String?): String = when (mimeType?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "bin"
        }
    }
}
