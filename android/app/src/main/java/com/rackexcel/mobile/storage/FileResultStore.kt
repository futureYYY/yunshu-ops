package com.rackexcel.mobile.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream

data class ResultFileInfo(
    val displayName: String,
    val sizeBytes: Long,
)

object FileResultStore {
    const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun save(context: Context, bytes: ByteArray, displayName: String = defaultName()): Uri {
        val safeName = safeName(displayName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, XLSX_MIME)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/云枢智维")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("创建下载文件失败")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("打开下载文件失败")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            directory.mkdirs()
            val file = File(directory, safeName)
            file.writeBytes(bytes)
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
    }

    /**
     * Writes a durable app-private copy used as the canonical task result.
     *
     * The file lives below getExternalFilesDir(), which is private to this app
     * while still being covered by the existing FileProvider path. This keeps
     * resultUri readable after a process restart without exposing a file in the
     * user's Downloads directory when automatic saving is disabled.
     */
    fun savePrivate(context: Context, bytes: ByteArray, displayName: String = defaultName()): Uri {
        val directory = (context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.cacheDir).resolve("云枢智维/交付")
        directory.mkdirs()
        val file = File(directory, safeName(displayName))
        val temporary = File(directory, ".${file.name}.tmp")
        runCatching { temporary.delete() }
        try {
            temporary.outputStream().use { output -> output.write(bytes) }
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                check(temporary.delete()) { "清理临时交付文件失败" }
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /**
     * Recovers a private delivery created by an older app version that stored
     * only the workbook name in history. The filename is sanitized before it
     * is used as a path segment, so a history value cannot escape the folder.
     */
    fun findPrivate(context: Context, displayName: String?): Uri? {
        val name = displayName?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        val directory = (context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.cacheDir).resolve("云枢智维/交付")
        val file = directory.resolve(safeName(name))
        return file.takeIf { it.isFile && it.length() > 0L }
            ?.let { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
    }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = XLSX_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享到微信或其他应用").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, XLSX_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Opens the user-visible delivery file without materializing it in memory. */
    fun openInputStream(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: error("读取交付文件失败")

    fun fileInfo(context: Context, uri: Uri): ResultFileInfo? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                val resolvedSize = if (size >= 0L) size else {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor -> descriptor.statSize } ?: -1L
                }
                ResultFileInfo(
                    displayName = name.ifBlank { defaultName() },
                    sizeBytes = resolvedSize,
                )
            }
        }.getOrNull()
    }

    fun canRead(context: Context, uri: Uri): Boolean = runCatching {
        openInputStream(context, uri).use { stream -> stream.read() }
        true
    }.getOrDefault(false)

    private fun safeName(displayName: String): String =
        displayName.replace(Regex("[^\\w.\\-\\u4e00-\\u9fff]"), "_")
            .ifBlank { defaultName() }

    private fun defaultName(): String = "机架识别结果_${System.currentTimeMillis()}.xlsx"
}
