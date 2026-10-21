package com.example.ui.pdf

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
import com.example.data.CustomPreset
import com.example.data.CustomPresetRepository
import com.example.engine.PdfCompressOptions
import com.example.engine.PdfCompressResult
import com.example.engine.PdfCompressionEngine
import com.example.engine.PdfCompressionProfile
import com.example.engine.PdfMetadata
import com.example.util.FormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.FileInputStream

enum class PdfToolTab {
    COMPRESS,
    UTILITIES
}

enum class PdfUtilityMode(val title: String) {
    MERGE("Merge PDFs"),
    SPLIT_EXTRACT("Split / Extract"),
    REORDER_ROTATE("Reorder & Rotate"),
    IMAGES_TO_PDF("Images to PDF")
}

sealed class PdfUiState {
    object Idle : PdfUiState()
    object LoadingMetadata : PdfUiState()
    data class Compressing(val currentPage: Int, val totalPages: Int, val stage: String) : PdfUiState()
    data class Finished(val result: PdfCompressResult) : PdfUiState()
    data class Error(val message: String) : PdfUiState()
}

class PdfCompressViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PdfCompressionEngine(application)
    private val repository = CompressionRepository(
        AppDatabase.getDatabase(application).compressionRecordDao()
    )
    private val presetRepository = CustomPresetRepository.getInstance(application)

    val customPdfPresets: StateFlow<List<CustomPreset>> = presetRepository.presets
        .map { list -> list.filter { it.targetType == "PDF" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTab = MutableStateFlow(PdfToolTab.COMPRESS)
    val activeTab: StateFlow<PdfToolTab> = _activeTab.asStateFlow()

    private val _utilityMode = MutableStateFlow(PdfUtilityMode.MERGE)
    val utilityMode: StateFlow<PdfUtilityMode> = _utilityMode.asStateFlow()

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _metadata = MutableStateFlow<PdfMetadata?>(null)
    val metadata: StateFlow<PdfMetadata?> = _metadata.asStateFlow()

    private val _targetSizeBytes = MutableStateFlow(500 * 1024L)
    val targetSizeBytes: StateFlow<Long> = _targetSizeBytes.asStateFlow()

    private val _customSizeText = MutableStateFlow("500")
    val customSizeText: StateFlow<String> = _customSizeText.asStateFlow()

    private val _isMbUnit = MutableStateFlow(false)
    val isMbUnit: StateFlow<Boolean> = _isMbUnit.asStateFlow()

    private val _selectedProfile = MutableStateFlow(PdfCompressionProfile.BALANCED)
    val selectedProfile: StateFlow<PdfCompressionProfile> = _selectedProfile.asStateFlow()

    // Explicit opt-in for scanned page rasterization (Part 5)
    private val _allowPageRasterization = MutableStateFlow(false)
    val allowPageRasterization: StateFlow<Boolean> = _allowPageRasterization.asStateFlow()

    // Part 9: PDF Utilities states
    private val _mergePdfUris = MutableStateFlow<List<Uri>>(emptyList())
    val mergePdfUris: StateFlow<List<Uri>> = _mergePdfUris.asStateFlow()

    private val _imagesForPdfUris = MutableStateFlow<List<Uri>>(emptyList())
    val imagesForPdfUris: StateFlow<List<Uri>> = _imagesForPdfUris.asStateFlow()

    private val _pageRangeInput = MutableStateFlow("1")
    val pageRangeInput: StateFlow<String> = _pageRangeInput.asStateFlow()

    // 0-based page sequence & rotation map for Reorder & Rotate
    private val _pageSequence = MutableStateFlow<List<Int>>(emptyList())
    val pageSequence: StateFlow<List<Int>> = _pageSequence.asStateFlow()

    private val _pageRotations = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val pageRotations: StateFlow<Map<Int, Int>> = _pageRotations.asStateFlow()

    private val _uiState = MutableStateFlow<PdfUiState>(PdfUiState.Idle)
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private var compressionJob: Job? = null

    fun setActiveTab(tab: PdfToolTab) {
        _activeTab.value = tab
        if (_uiState.value is PdfUiState.Finished || _uiState.value is PdfUiState.Error) {
            _uiState.value = PdfUiState.Idle
        }
    }

    fun setUtilityMode(mode: PdfUtilityMode) {
        _utilityMode.value = mode
        if (_uiState.value is PdfUiState.Finished || _uiState.value is PdfUiState.Error) {
            _uiState.value = PdfUiState.Idle
        }
    }

    fun onPdfSelected(uri: Uri) {
        _selectedUri.value = uri
        _uiState.value = PdfUiState.LoadingMetadata
        viewModelScope.launch {
            try {
                val data = engine.inspectPdf(uri)
                if (data.isEncrypted) {
                    _uiState.value = PdfUiState.Error("Selected PDF is password-protected. Encrypted PDFs cannot be modified without unlocking them first.")
                } else if (data.isCorrupted || data.pageCount <= 0) {
                    _uiState.value = PdfUiState.Error("Selected PDF could not be read or contains 0 pages.")
                } else {
                    _metadata.value = data
                    _pageRangeInput.value = if (data.pageCount > 1) "1-${data.pageCount}" else "1"
                    _pageSequence.value = (0 until data.pageCount).toList()
                    _pageRotations.value = emptyMap()
                    _uiState.value = PdfUiState.Idle
                }
            } catch (e: Exception) {
                _uiState.value = PdfUiState.Error("Failed to parse PDF: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun prefillForCompressAgain(uri: Uri?, targetBytes: Long) {
        _activeTab.value = PdfToolTab.COMPRESS
        if (targetBytes > 0L) {
            setTargetPreset(targetBytes)
        }
        if (uri != null) {
            onPdfSelected(uri)
        } else {
            clearSelection()
        }
    }

    fun applyCustomPreset(preset: CustomPreset) {
        setTargetPreset(preset.targetSizeBytes)
        runCatching { PdfCompressionProfile.valueOf(preset.pdfProfile) }.getOrNull()?.let {
            _selectedProfile.value = it
        }
    }

    fun saveCurrentAsCustomPreset(name: String): Boolean {
        val preset = CustomPreset(
            id = "user_pdf_${System.currentTimeMillis()}",
            name = name,
            targetType = "PDF",
            targetSizeBytes = _targetSizeBytes.value,
            pdfProfile = _selectedProfile.value.name
        )
        return presetRepository.savePreset(preset)
    }

    fun clearSelection() {
        compressionJob?.cancel()
        _selectedUri.value = null
        _metadata.value = null
        _mergePdfUris.value = emptyList()
        _imagesForPdfUris.value = emptyList()
        _uiState.value = PdfUiState.Idle
    }

    fun setTargetPreset(bytes: Long) {
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
        if (parsed != null && parsed > 0) {
            _targetSizeBytes.value = parsed
        }
    }

    fun setProfile(profile: PdfCompressionProfile) {
        _selectedProfile.value = profile
    }

    fun setAllowPageRasterization(enabled: Boolean) {
        _allowPageRasterization.value = enabled
    }

    // PDF Utilities state setters
    fun onMergePdfsSelected(uris: List<Uri>) {
        _mergePdfUris.value = uris
        _uiState.value = PdfUiState.Idle
    }

    fun onImagesForPdfSelected(uris: List<Uri>) {
        _imagesForPdfUris.value = uris
        _uiState.value = PdfUiState.Idle
    }

    fun setPageRangeInput(input: String) {
        _pageRangeInput.value = input
    }

    fun movePageInSequence(position: Int, direction: Int) {
        val current = _pageSequence.value.toMutableList()
        val targetPos = position + direction
        if (position in current.indices && targetPos in current.indices) {
            val tmp = current[position]
            current[position] = current[targetPos]
            current[targetPos] = tmp
            _pageSequence.value = current
        }
    }

    fun rotatePageAt(sourcePageIndex: Int) {
        val current = _pageRotations.value.toMutableMap()
        val existing = current[sourcePageIndex] ?: 0
        current[sourcePageIndex] = (existing + 90) % 360
        _pageRotations.value = current
    }

    fun rotateAllPages90() {
        val meta = _metadata.value ?: return
        val current = _pageRotations.value.toMutableMap()
        for (i in 0 until meta.pageCount) {
            val existing = current[i] ?: 0
            current[i] = (existing + 90) % 360
        }
        _pageRotations.value = current
    }

    fun startCompression() {
        val uri = _selectedUri.value ?: return
        val parsedTarget = FormatUtils.parseTargetSize(_customSizeText.value, _isMbUnit.value)
        val target = parsedTarget ?: _targetSizeBytes.value
        if (target <= 0L) {
            _uiState.value = PdfUiState.Error("Please enter a valid target size greater than 0.")
            return
        }
        _targetSizeBytes.value = target

        val options = PdfCompressOptions(
            targetMaxBytes = target,
            profile = _selectedProfile.value,
            allowPageRasterization = _allowPageRasterization.value
        )

        compressionJob?.cancel()
        compressionJob = viewModelScope.launch {
            try {
                _uiState.value = PdfUiState.Compressing(1, _metadata.value?.pageCount ?: 1, "Initializing PDF engine...")
                val result = engine.compressPdf(uri, options) { cur, total, stage ->
                    _uiState.value = PdfUiState.Compressing(cur, total, stage)
                }

                repository.recordCompression(
                    CompressionRecord(
                        fileName = result.originalMetadata.fileName,
                        fileType = "PDF",
                        originalSizeBytes = result.originalMetadata.sizeBytes,
                        compressedSizeBytes = result.outputBytes,
                        targetSizeBytes = target,
                        isTargetMet = result.isTargetMet,
                        outputFilePath = result.outputFile.absolutePath,
                        mimeType = "application/pdf"
                    )
                )

                _uiState.value = PdfUiState.Finished(result)
            } catch (_: CancellationException) {
                _uiState.value = PdfUiState.Idle
            } catch (e: Exception) {
                _uiState.value = PdfUiState.Error(e.localizedMessage ?: "PDF compression failed.")
            }
        }
    }

    fun runMergePdfs() {
        val uris = _mergePdfUris.value
        if (uris.size < 2) {
            _uiState.value = PdfUiState.Error("Please select at least 2 PDF files to merge.")
            return
        }
        compressionJob?.cancel()
        compressionJob = viewModelScope.launch {
            try {
                _uiState.value = PdfUiState.Compressing(1, uris.size, "Merging ${uris.size} PDFs...")
                val context = getApplication<Application>()
                val totalInputSize = uris.sumOf { FormatUtils.getFileSize(context, it) }
                val outFile = engine.mergePdfs(uris) { stage ->
                    _uiState.value = PdfUiState.Compressing(1, uris.size, stage)
                }
                val outMeta = PdfMetadata(
                    fileName = "merged_document.pdf",
                    pageCount = uris.size,
                    sizeBytes = totalInputSize
                )
                val res = PdfCompressResult(
                    originalMetadata = outMeta,
                    outputBytes = outFile.length(),
                    pageCount = outMeta.pageCount,
                    profileUsed = PdfCompressionProfile.LIGHT,
                    wasRasterized = true,
                    isTargetMet = true,
                    outputFile = outFile,
                    isValidPdf = true,
                    statusMessage = "Successfully merged ${uris.size} PDFs into ${FormatUtils.formatBytes(outFile.length())}."
                )
                repository.recordCompression(
                    CompressionRecord(
                        fileName = "merged_document.pdf",
                        fileType = "PDF",
                        originalSizeBytes = totalInputSize,
                        compressedSizeBytes = outFile.length(),
                        targetSizeBytes = outFile.length(),
                        isTargetMet = true,
                        outputFilePath = outFile.absolutePath,
                        mimeType = "application/pdf"
                    )
                )
                _uiState.value = PdfUiState.Finished(res)
            } catch (_: CancellationException) {
                _uiState.value = PdfUiState.Idle
            } catch (e: Exception) {
                _uiState.value = PdfUiState.Error(e.localizedMessage ?: "Failed to merge PDFs.")
            }
        }
    }

    fun runSplitOrExtractPages() {
        val uri = _selectedUri.value ?: return
        val meta = _metadata.value ?: return
        val indices = PdfCompressionEngine.parsePageRangeInput(_pageRangeInput.value, meta.pageCount)
        if (indices.isEmpty()) {
            _uiState.value = PdfUiState.Error("Invalid page range '${_pageRangeInput.value}'. Enter valid page numbers between 1 and ${meta.pageCount} (e.g. 1-2, 4).")
            return
        }
        compressionJob?.cancel()
        compressionJob = viewModelScope.launch {
            try {
                _uiState.value = PdfUiState.Compressing(1, indices.size, "Extracting ${indices.size} pages...")
                val outFile = engine.transformPdfPages(uri, indices) { stage ->
                    _uiState.value = PdfUiState.Compressing(1, indices.size, stage)
                }
                val res = PdfCompressResult(
                    originalMetadata = meta,
                    outputBytes = outFile.length(),
                    pageCount = indices.size,
                    profileUsed = PdfCompressionProfile.LIGHT,
                    wasRasterized = true,
                    isTargetMet = true,
                    outputFile = outFile,
                    isValidPdf = true,
                    statusMessage = "Extracted ${indices.size} page(s) (${FormatUtils.formatBytes(outFile.length())})."
                )
                repository.recordCompression(
                    CompressionRecord(
                        fileName = "extracted_${meta.fileName}",
                        fileType = "PDF",
                        originalSizeBytes = meta.sizeBytes,
                        compressedSizeBytes = outFile.length(),
                        targetSizeBytes = outFile.length(),
                        isTargetMet = true,
                        outputFilePath = outFile.absolutePath,
                        mimeType = "application/pdf"
                    )
                )
                _uiState.value = PdfUiState.Finished(res)
            } catch (_: CancellationException) {
                _uiState.value = PdfUiState.Idle
            } catch (e: Exception) {
                _uiState.value = PdfUiState.Error(e.localizedMessage ?: "Failed to extract PDF pages.")
            }
        }
    }

    fun runReorderAndRotatePages() {
        val uri = _selectedUri.value ?: return
        val meta = _metadata.value ?: return
        val seq = _pageSequence.value
        val rots = _pageRotations.value
        if (seq.isEmpty()) return

        compressionJob?.cancel()
        compressionJob = viewModelScope.launch {
            try {
                _uiState.value = PdfUiState.Compressing(1, seq.size, "Reordering & rotating pages...")
                val outFile = engine.transformPdfPages(uri, seq, rots) { stage ->
                    _uiState.value = PdfUiState.Compressing(1, seq.size, stage)
                }
                val res = PdfCompressResult(
                    originalMetadata = meta,
                    outputBytes = outFile.length(),
                    pageCount = seq.size,
                    profileUsed = PdfCompressionProfile.LIGHT,
                    wasRasterized = true,
                    isTargetMet = true,
                    outputFile = outFile,
                    isValidPdf = true,
                    statusMessage = "Saved reordered/rotated PDF with ${seq.size} pages (${FormatUtils.formatBytes(outFile.length())})."
                )
                repository.recordCompression(
                    CompressionRecord(
                        fileName = "updated_${meta.fileName}",
                        fileType = "PDF",
                        originalSizeBytes = meta.sizeBytes,
                        compressedSizeBytes = outFile.length(),
                        targetSizeBytes = outFile.length(),
                        isTargetMet = true,
                        outputFilePath = outFile.absolutePath,
                        mimeType = "application/pdf"
                    )
                )
                _uiState.value = PdfUiState.Finished(res)
            } catch (_: CancellationException) {
                _uiState.value = PdfUiState.Idle
            } catch (e: Exception) {
                _uiState.value = PdfUiState.Error(e.localizedMessage ?: "Failed to reorder/rotate PDF.")
            }
        }
    }

    fun runImagesToPdf() {
        val uris = _imagesForPdfUris.value
        if (uris.isEmpty()) {
            _uiState.value = PdfUiState.Error("Please select at least 1 image to convert to PDF.")
            return
        }
        compressionJob?.cancel()
        compressionJob = viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val totalSize = uris.sumOf { FormatUtils.getFileSize(context, it) }
                _uiState.value = PdfUiState.Compressing(1, uris.size, "Converting ${uris.size} images to PDF...")
                val outFile = engine.imagesToPdf(uris, fitToA4 = true) { stage ->
                    _uiState.value = PdfUiState.Compressing(1, uris.size, stage)
                }
                val meta = PdfMetadata(
                    fileName = "images_bundle.pdf",
                    pageCount = uris.size,
                    sizeBytes = totalSize
                )
                val res = PdfCompressResult(
                    originalMetadata = meta,
                    outputBytes = outFile.length(),
                    pageCount = uris.size,
                    profileUsed = PdfCompressionProfile.LIGHT,
                    wasRasterized = true,
                    isTargetMet = true,
                    outputFile = outFile,
                    isValidPdf = true,
                    statusMessage = "Created ${uris.size}-page PDF (${FormatUtils.formatBytes(outFile.length())}) from selected images."
                )
                repository.recordCompression(
                    CompressionRecord(
                        fileName = "images_bundle.pdf",
                        fileType = "PDF",
                        originalSizeBytes = totalSize,
                        compressedSizeBytes = outFile.length(),
                        targetSizeBytes = outFile.length(),
                        isTargetMet = true,
                        outputFilePath = outFile.absolutePath,
                        mimeType = "application/pdf"
                    )
                )
                _uiState.value = PdfUiState.Finished(res)
            } catch (_: CancellationException) {
                _uiState.value = PdfUiState.Idle
            } catch (e: Exception) {
                _uiState.value = PdfUiState.Error(e.localizedMessage ?: "Failed to convert images to PDF.")
            }
        }
    }

    fun cancelCompression() {
        compressionJob?.cancel()
        _uiState.value = PdfUiState.Idle
    }

    fun saveOutputFileToUri(context: Context, destUri: Uri, result: PdfCompressResult): Boolean {
        return try {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                FileInputStream(result.outputFile).use { input ->
                    input.copyTo(out)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun createShareIntent(context: Context, result: PdfCompressResult): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, result.outputFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createOpenIntent(context: Context, result: PdfCompressResult): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, result.outputFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
