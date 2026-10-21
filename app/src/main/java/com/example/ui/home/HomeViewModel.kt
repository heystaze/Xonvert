package com.example.ui.home

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CompressionRecord
import com.example.data.CompressionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class HomeStats(
    val totalCount: Int = 0,
    val totalBytesSaved: Long = 0L,
    val averageSavingsPercent: Int = 0
)

sealed class FileActionOutcome {
    data class ReadyIntent(val intent: Intent) : FileActionOutcome()
    data class ReadyUri(val uri: Uri, val record: CompressionRecord) : FileActionOutcome()
    data class MissingFile(val message: String, val record: CompressionRecord) : FileActionOutcome()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CompressionRepository(
        AppDatabase.getDatabase(application).compressionRecordDao()
    )

    val recentRecords: StateFlow<List<CompressionRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val stats: StateFlow<HomeStats> = recentRecords.map { list ->
        if (list.isEmpty()) {
            HomeStats()
        } else {
            val totalSaved = list.sumOf { it.savedBytes }
            val avgPercent = (list.map { it.savingsPercentage }.average()).toInt()
            HomeStats(
                totalCount = list.size,
                totalBytesSaved = totalSaved,
                averageSavingsPercent = avgPercent
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeStats()
    )

    fun isRecordFileAccessible(record: CompressionRecord): Boolean {
        val path = record.outputFilePath ?: return false
        if (path.isBlank()) return false
        val file = File(path)
        return file.exists() && file.isFile && file.canRead() && file.length() > 0L
    }

    fun resolveMimeType(record: CompressionRecord): String {
        if (record.mimeType.isNotBlank()) return record.mimeType
        return if (record.fileType.equals("PDF", ignoreCase = true)) {
            "application/pdf"
        } else {
            val lower = record.fileName.lowercase()
            when {
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".webp") -> "image/webp"
                else -> "image/jpeg"
            }
        }
    }

    fun buildShareAction(context: Context, record: CompressionRecord): FileActionOutcome {
        val path = record.outputFilePath
        val file = path?.let { File(it) }
        if (file == null || !file.exists() || !file.canRead() || file.length() <= 0L) {
            return FileActionOutcome.MissingFile(
                "Compressed output file for '${record.fileName}' is no longer in local cache.",
                record
            )
        }
        return try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            val mime = resolveMimeType(record)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${record.fileName}")
            FileActionOutcome.ReadyIntent(chooser)
        } catch (e: Exception) {
            FileActionOutcome.MissingFile(
                "Unable to share '${record.fileName}': ${e.localizedMessage ?: "File inaccessible"}",
                record
            )
        }
    }

    fun buildOpenAction(context: Context, record: CompressionRecord): FileActionOutcome {
        val path = record.outputFilePath
        val file = path?.let { File(it) }
        if (file == null || !file.exists() || !file.canRead() || file.length() <= 0L) {
            return FileActionOutcome.MissingFile(
                "Compressed output file for '${record.fileName}' is no longer available on device.",
                record
            )
        }
        return try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            val mime = resolveMimeType(record)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(viewIntent, "Open ${record.fileName}")
            FileActionOutcome.ReadyIntent(chooser)
        } catch (e: Exception) {
            FileActionOutcome.MissingFile(
                "Unable to open '${record.fileName}': ${e.localizedMessage ?: "File inaccessible"}",
                record
            )
        }
    }

    fun buildCompressAgainAction(context: Context, record: CompressionRecord): FileActionOutcome {
        val path = record.outputFilePath
        val file = path?.let { File(it) }
        if (file == null || !file.exists() || !file.canRead() || file.length() <= 0L) {
            return FileActionOutcome.MissingFile(
                "Previous file '${record.fileName}' is no longer available. Previous target (${com.example.util.FormatUtils.formatBytes(record.targetSizeBytes)}) has been prefilled — please select a file.",
                record
            )
        }
        return try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            FileActionOutcome.ReadyUri(contentUri, record)
        } catch (e: Exception) {
            FileActionOutcome.MissingFile(
                "Previous file could not be reopened. Target size prefilled — please select a file.",
                record
            )
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteRecord(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
