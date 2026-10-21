package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {

    private val decFormat = DecimalFormat("#.##")

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> "${decFormat.format(gb)} GB"
            mb >= 1.0 -> "${decFormat.format(mb)} MB"
            kb >= 1.0 -> "${decFormat.format(kb)} KB"
            else -> "$bytes B"
        }
    }

    fun parseTargetSize(input: String, isMb: Boolean): Long? {
        val clean = input.trim().replace(",", ".")
        val value = clean.toDoubleOrNull() ?: return null
        if (value <= 0) return null
        return if (isMb) {
            (value * 1024.0 * 1024.0).toLong()
        } else {
            (value * 1024.0).toLong()
        }
    }

    fun calculateSavingsPercentage(original: Long, compressed: Long): Int {
        if (original <= 0L) return 0
        if (compressed >= original) return 0
        val reduction = ((original - compressed).toDouble() / original.toDouble()) * 100.0
        return reduction.toInt().coerceIn(0, 100)
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getFileName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        if (name == null) {
            name = uri.lastPathSegment ?: "unknown_file"
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size: Long = 0L
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        if (size <= 0L) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    size = stream.available().toLong()
                }
            } catch (_: Exception) {
            }
        }
        return size
    }

    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "application/octet-stream"
    }
}
