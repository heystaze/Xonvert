package com.example.ui.batch

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
import com.example.engine.ImageCompressOptions
import com.example.engine.ImageCompressionEngine
import com.example.engine.ImageOutputFormat
import com.example.engine.PdfCompressOptions
import com.example.engine.PdfCompressionEngine
import com.example.engine.PdfCompressionProfile
import com.example.engine.ResizePreset
import com.example.util.FormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

enum class BatchItemStatus {
    PENDING,
    PROCESSING,
    SUCCESS_MET,
    SUCCESS_UNMET,
    FAILED,
    CANCELLED
}

data class BatchItem(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val isPdf: Boolean,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long? = null,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val errorMessage: String? = null,
    val outputFile: File? = null,
    val outputMimeType: String = "image/jpeg"
)

class BatchCompressViewModel(application: Application) : AndroidViewModel(application) {

    private val imageEngine = ImageCompressionEngine(application)
    private val pdfEngine = PdfCompressionEngine(application)
    private val repository = CompressionRepository(
        AppDatabase.getDatabase(application).compressionRecordDao()
    )

    private val _items = MutableStateFlow<List<BatchItem>>(emptyList())
    val items: StateFlow<List<BatchItem>> = _items.asStateFlow()

    private val _targetSizeBytes = MutableStateFlow(100 * 1024L)
    val targetSizeBytes: StateFlow<Long> = _targetSizeBytes.asStateFlow()

    private val _customSizeText = MutableStateFlow("100")
    val customSizeText: StateFlow<String> = _customSizeText.asStateFlow()

    private val _isMbUnit = MutableStateFlow(false)
    val isMbUnit: StateFlow<Boolean> = _isMbUnit.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private var batchJob: Job? = null

    fun onFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        val newItems = uris.map { uri ->
            val name = FormatUtils.getFileName(context, uri)
            val mime = FormatUtils.getMimeType(context, uri)
            val isPdf = mime.contains("pdf", ignoreCase = true) || name.lowercase().endsWith(".pdf")
            BatchItem(
                uri = uri,
                fileName = name,
                mimeType = mime,
                isPdf = isPdf,
                originalSizeBytes = FormatUtils.getFileSize(context, uri),
                outputMimeType = if (isPdf) "application/pdf" else "image/jpeg"
            )
        }
        _items.value = newItems
        _currentIndex.value = 0
    }

    fun clear() {
        cancelBatch()
        _items.value = emptyList()
        _currentIndex.value = 0
    }

    fun setTargetSizePreset(bytes: Long) {
        _targetSizeBytes.value = bytes
        if (bytes >= 1024 * 1024L && bytes % (1024 * 1024L) == 0L) {
            _isMbUnit.value = true
            _customSizeText.value = (bytes / (1024 * 1024L)).toString()
        } else {
            _isMbUnit.value = false
            _customSizeText.value = (bytes / 1024L).toString()
        }
    }

    fun setCustomSize(text: String, isMb: Boolean) {
        _customSizeText.value = text
        _isMbUnit.value = isMb
        val parsed = FormatUtils.parseTargetSize(text, isMb)
        if (parsed != null && parsed > 0L) {
            _targetSizeBytes.value = parsed
        }
    }

    fun startBatch() {
        val currentList = _items.value
        if (currentList.isEmpty() || _isProcessing.value) return
        val indicesToProcess = currentList.indices.toList()
        processIndices(indicesToProcess)
    }

    fun retryFailedItems() {
        val currentList = _items.value
        if (currentList.isEmpty() || _isProcessing.value) return
        val failedIndices = currentList.mapIndexedNotNull { idx, item ->
            if (item.status == BatchItemStatus.FAILED || item.status == BatchItemStatus.CANCELLED) idx else null
        }
        if (failedIndices.isNotEmpty()) {
            processIndices(failedIndices)
        }
    }

    fun retrySingleItem(index: Int) {
        if (_isProcessing.value || index !in _items.value.indices) return
        processIndices(listOf(index))
    }

    private fun processIndices(indices: List<Int>) {
        val parsed = FormatUtils.parseTargetSize(_customSizeText.value, _isMbUnit.value)
        if (parsed != null && parsed > 0L) {
            _targetSizeBytes.value = parsed
        }
        val targetBytes = _targetSizeBytes.value

        _isProcessing.value = true
        batchJob = viewModelScope.launch {
            val updated = _items.value.toMutableList()
            try {
                for (i in indices) {
                    if (i !in updated.indices) continue
                    _currentIndex.value = i
                    val item = updated[i]
                    updated[i] = item.copy(status = BatchItemStatus.PROCESSING, errorMessage = null)
                    _items.value = updated.toList()

                    try {
                        if (item.isPdf) {
                            val pdfOpts = PdfCompressOptions(
                                targetMaxBytes = targetBytes,
                                profile = PdfCompressionProfile.BALANCED,
                                allowPageRasterization = true
                            )
                            val res = pdfEngine.compressPdf(item.uri, pdfOpts)
                            val status = if (res.isTargetMet) BatchItemStatus.SUCCESS_MET else BatchItemStatus.SUCCESS_UNMET
                            updated[i] = item.copy(
                                status = status,
                                outputSizeBytes = res.outputBytes,
                                outputFile = res.outputFile,
                                outputMimeType = "application/pdf",
                                errorMessage = null
                            )
                            repository.recordCompression(
                                CompressionRecord(
                                    fileName = item.fileName,
                                    fileType = "PDF",
                                    originalSizeBytes = item.originalSizeBytes,
                                    compressedSizeBytes = res.outputBytes,
                                    targetSizeBytes = targetBytes,
                                    isTargetMet = res.isTargetMet,
                                    outputFilePath = res.outputFile.absolutePath,
                                    mimeType = "application/pdf"
                                )
                            )
                        } else {
                            val imgOpts = ImageCompressOptions(
                                targetMaxBytes = targetBytes,
                                format = ImageOutputFormat.AUTO,
                                resizePreset = ResizePreset.ORIGINAL,
                                preserveAspectRatio = true,
                                allowDimensionDownscaling = true
                            )
                            val res = imageEngine.compressImage(item.uri, imgOpts)
                            val status = if (res.isTargetMet) BatchItemStatus.SUCCESS_MET else BatchItemStatus.SUCCESS_UNMET
                            val mime = res.finalFormat.mimeType.ifEmpty { "image/jpeg" }
                            updated[i] = item.copy(
                                status = status,
                                outputSizeBytes = res.outputBytes,
                                outputFile = res.outputFile,
                                outputMimeType = mime,
                                errorMessage = null
                            )
                            repository.recordCompression(
                                CompressionRecord(
                                    fileName = item.fileName,
                                    fileType = "IMAGE",
                                    originalSizeBytes = item.originalSizeBytes,
                                    compressedSizeBytes = res.outputBytes,
                                    targetSizeBytes = targetBytes,
                                    isTargetMet = res.isTargetMet,
                                    outputFilePath = res.outputFile.absolutePath,
                                    mimeType = mime
                                )
                            )
                        }
                    } catch (ce: CancellationException) {
                        updated[i] = item.copy(status = BatchItemStatus.CANCELLED, errorMessage = "Cancelled")
                        _items.value = updated.toList()
                        throw ce
                    } catch (e: Exception) {
                        updated[i] = item.copy(
                            status = BatchItemStatus.FAILED,
                            errorMessage = e.localizedMessage ?: "Processing error"
                        )
                    }
                    _items.value = updated.toList()
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun cancelBatch() {
        batchJob?.cancel()
        _isProcessing.value = false
    }

    fun shareBatchItem(context: Context, item: BatchItem): Intent? {
        val file = item.outputFile ?: return null
        if (!file.exists() || !file.canRead()) return null
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = item.outputMimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share ${item.fileName}")
    }

    fun saveBatchItemToUri(context: Context, destUri: Uri, item: BatchItem): Boolean {
        val file = item.outputFile ?: return false
        return try {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
