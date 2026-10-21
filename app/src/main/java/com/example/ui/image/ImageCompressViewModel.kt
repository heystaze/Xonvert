package com.example.ui.image

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
import com.example.engine.ImageCompressOptions
import com.example.engine.ImageCompressResult
import com.example.engine.ImageCompressionEngine
import com.example.engine.ImageMetadata
import com.example.engine.ImageOutputFormat
import com.example.engine.ImageResizeMode
import com.example.engine.ImageTransformOptions
import com.example.engine.ResizePreset
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

enum class ImageToolTab {
    COMPRESS,
    RESIZE_CONVERT
}

sealed class ImageUiState {
    object Idle : ImageUiState()
    object LoadingMetadata : ImageUiState()
    data class Compressing(val step: String, val iteration: Int, val maxIterations: Int) : ImageUiState()
    data class Finished(val result: ImageCompressResult) : ImageUiState()
    data class Error(val message: String) : ImageUiState()
}

class ImageCompressViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = ImageCompressionEngine(application)
    private val repository = CompressionRepository(
        AppDatabase.getDatabase(application).compressionRecordDao()
    )
    private val presetRepository = CustomPresetRepository.getInstance(application)

    val customImagePresets: StateFlow<List<CustomPreset>> = presetRepository.presets
        .map { list -> list.filter { it.targetType == "IMAGE" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTab = MutableStateFlow(ImageToolTab.COMPRESS)
    val activeTab: StateFlow<ImageToolTab> = _activeTab.asStateFlow()

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _metadata = MutableStateFlow<ImageMetadata?>(null)
    val metadata: StateFlow<ImageMetadata?> = _metadata.asStateFlow()

    // Compress mode states
    private val _targetSizeBytes = MutableStateFlow(100 * 1024L)
    val targetSizeBytes: StateFlow<Long> = _targetSizeBytes.asStateFlow()

    private val _customSizeText = MutableStateFlow("100")
    val customSizeText: StateFlow<String> = _customSizeText.asStateFlow()

    private val _isMbUnit = MutableStateFlow(false)
    val isMbUnit: StateFlow<Boolean> = _isMbUnit.asStateFlow()

    private val _selectedFormat = MutableStateFlow(ImageOutputFormat.AUTO)
    val selectedFormat: StateFlow<ImageOutputFormat> = _selectedFormat.asStateFlow()

    private val _resizePreset = MutableStateFlow(ResizePreset.ORIGINAL)
    val resizePreset: StateFlow<ResizePreset> = _resizePreset.asStateFlow()

    private val _preserveAspectRatio = MutableStateFlow(true)
    val preserveAspectRatio: StateFlow<Boolean> = _preserveAspectRatio.asStateFlow()

    private val _allowDimensionDownscaling = MutableStateFlow(true)
    val allowDimensionDownscaling: StateFlow<Boolean> = _allowDimensionDownscaling.asStateFlow()

    // Part 8: Resize & Convert mode states
    private val _resizeMode = MutableStateFlow(ImageResizeMode.PERCENTAGE)
    val resizeMode: StateFlow<ImageResizeMode> = _resizeMode.asStateFlow()

    private val _scalePercentage = MutableStateFlow(75)
    val scalePercentage: StateFlow<Int> = _scalePercentage.asStateFlow()

    private val _customWidthText = MutableStateFlow("")
    val customWidthText: StateFlow<String> = _customWidthText.asStateFlow()

    private val _customHeightText = MutableStateFlow("")
    val customHeightText: StateFlow<String> = _customHeightText.asStateFlow()

    private val _rotationDegrees = MutableStateFlow(0)
    val rotationDegrees: StateFlow<Int> = _rotationDegrees.asStateFlow()

    private val _convertFormat = MutableStateFlow(ImageOutputFormat.WEBP)
    val convertFormat: StateFlow<ImageOutputFormat> = _convertFormat.asStateFlow()

    private val _uiState = MutableStateFlow<ImageUiState>(ImageUiState.Idle)
    val uiState: StateFlow<ImageUiState> = _uiState.asStateFlow()

    private var compressionJob: Job? = null

    fun setActiveTab(tab: ImageToolTab) {
        _activeTab.value = tab
        if (_uiState.value is ImageUiState.Finished || _uiState.value is ImageUiState.Error) {
            _uiState.value = ImageUiState.Idle
        }
    }

    fun onImageSelected(uri: Uri) {
        _selectedUri.value = uri
        _uiState.value = ImageUiState.LoadingMetadata
        viewModelScope.launch {
            try {
                val data = engine.inspectImage(uri)
                _metadata.value = data
                _customWidthText.value = data.width.toString()
                _customHeightText.value = data.height.toString()
                _uiState.value = ImageUiState.Idle
            } catch (e: Exception) {
                _uiState.value = ImageUiState.Error("Failed to read image: ${e.localizedMessage ?: "Unsupported or corrupted file"}")
            }
        }
    }

    fun prefillForCompressAgain(uri: Uri?, targetBytes: Long) {
        _activeTab.value = ImageToolTab.COMPRESS
        if (targetBytes > 0L) {
            setTargetSizePreset(targetBytes)
        }
        if (uri != null) {
            onImageSelected(uri)
        } else {
            clearSelection()
        }
    }

    fun applyCustomPreset(preset: CustomPreset) {
        setTargetSizePreset(preset.targetSizeBytes)
        runCatching { ImageOutputFormat.valueOf(preset.imageFormat) }.getOrNull()?.let {
            _selectedFormat.value = it
        }
        runCatching { ResizePreset.valueOf(preset.resizePreset) }.getOrNull()?.let {
            _resizePreset.value = it
        }
    }

    fun saveCurrentAsCustomPreset(name: String): Boolean {
        val preset = CustomPreset(
            id = "user_img_${System.currentTimeMillis()}",
            name = name,
            targetType = "IMAGE",
            targetSizeBytes = _targetSizeBytes.value,
            imageFormat = _selectedFormat.value.name,
            resizePreset = _resizePreset.value.name
        )
        return presetRepository.savePreset(preset)
    }

    fun clearSelection() {
        compressionJob?.cancel()
        _selectedUri.value = null
        _metadata.value = null
        _uiState.value = ImageUiState.Idle
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
        if (parsed != null && parsed > 0) {
            _targetSizeBytes.value = parsed
        }
    }

    fun setFormat(format: ImageOutputFormat) {
        _selectedFormat.value = format
    }

    fun setResizePreset(preset: ResizePreset) {
        _resizePreset.value = preset
    }

    fun setPreserveAspectRatio(preserve: Boolean) {
        _preserveAspectRatio.value = preserve
    }

    fun setAllowDimensionDownscaling(allow: Boolean) {
        _allowDimensionDownscaling.value = allow
    }

    // Resize & Convert setters
    fun setResizeMode(mode: ImageResizeMode) {
        _resizeMode.value = mode
    }

    fun setScalePercentage(pct: Int) {
        _scalePercentage.value = pct.coerceIn(10, 200)
    }

    fun setCustomWidthText(text: String) {
        _customWidthText.value = text
        val meta = _metadata.value
        val w = text.toIntOrNull()
        if (_preserveAspectRatio.value && meta != null && w != null && w > 0 && meta.width > 0) {
            val h = ((w.toDouble() / meta.width.toDouble()) * meta.height).toInt().coerceAtLeast(1)
            _customHeightText.value = h.toString()
        }
    }

    fun setCustomHeightText(text: String) {
        _customHeightText.value = text
        val meta = _metadata.value
        val h = text.toIntOrNull()
        if (_preserveAspectRatio.value && meta != null && h != null && h > 0 && meta.height > 0) {
            val w = ((h.toDouble() / meta.height.toDouble()) * meta.width).toInt().coerceAtLeast(1)
            _customWidthText.value = w.toString()
        }
    }

    fun rotate90Clockwise() {
        _rotationDegrees.value = (_rotationDegrees.value + 90) % 360
    }

    fun setRotationDegrees(deg: Int) {
        _rotationDegrees.value = ((deg % 360) + 360) % 360
    }

    fun setConvertFormat(format: ImageOutputFormat) {
        _convertFormat.value = format
    }

    fun startCompression() {
        val uri = _selectedUri.value ?: return
        val parsedTarget = FormatUtils.parseTargetSize(_customSizeText.value, _isMbUnit.value)
        val target = parsedTarget ?: _targetSizeBytes.value
        if (target <= 0L) {
            _uiState.value = ImageUiState.Error("Please enter a valid target file size greater than 0.")
            return
        }
        _targetSizeBytes.value = target

        val options = ImageCompressOptions(
            targetMaxBytes = target,
            format = _selectedFormat.value,
            resizePreset = _resizePreset.value,
            preserveAspectRatio = _preserveAspectRatio.value,
            allowDimensionDownscaling = _allowDimensionDownscaling.value
        )

        compressionJob?.cancel()
        compressionJob = viewModelScope.launch {
            try {
                _uiState.value = ImageUiState.Compressing("Starting compression engine...", 0, 12)
                val result = engine.compressImage(uri, options) { step, iter, max ->
                    _uiState.value = ImageUiState.Compressing(step, iter, max)
                }

                repository.recordCompression(
                    CompressionRecord(
                        fileName = result.originalMetadata.fileName,
                        fileType = "IMAGE",
                        originalSizeBytes = result.originalMetadata.sizeBytes,
                        compressedSizeBytes = result.outputBytes,
                        targetSizeBytes = target,
                        isTargetMet = result.isTargetMet,
                        outputFilePath = result.outputFile.absolutePath,
                        mimeType = result.finalFormat.mimeType.ifEmpty { "image/jpeg" }
                    )
                )

                _uiState.value = ImageUiState.Finished(result)
            } catch (_: CancellationException) {
                _uiState.value = ImageUiState.Idle
            } catch (e: Exception) {
                _uiState.value = ImageUiState.Error(e.localizedMessage ?: "Compression failed unexpectedly.")
            }
        }
    }

    fun startResizeAndConvert() {
        val uri = _selectedUri.value ?: return
        val w = _customWidthText.value.toIntOrNull()
        val h = _customHeightText.value.toIntOrNull()

        if (_resizeMode.value == ImageResizeMode.PIXELS && ((w ?: 0) <= 0 || (h ?: 0) <= 0)) {
            _uiState.value = ImageUiState.Error("Please enter valid positive pixel width and height.")
            return
        }

        val options = ImageTransformOptions(
            resizeMode = _resizeMode.value,
            scalePercentage = _scalePercentage.value,
            targetWidth = w,
            targetHeight = h,
            preserveAspectRatio = _preserveAspectRatio.value,
            rotationDegrees = _rotationDegrees.value,
            outputFormat = _convertFormat.value,
            quality = 92
        )

        compressionJob?.cancel()
        compressionJob = viewModelScope.launch {
            try {
                _uiState.value = ImageUiState.Compressing("Transforming image...", 1, 3)
                val result = engine.resizeAndConvertImage(uri, options) { step ->
                    _uiState.value = ImageUiState.Compressing(step, 2, 3)
                }

                repository.recordCompression(
                    CompressionRecord(
                        fileName = result.originalMetadata.fileName,
                        fileType = "IMAGE",
                        originalSizeBytes = result.originalMetadata.sizeBytes,
                        compressedSizeBytes = result.outputBytes,
                        targetSizeBytes = result.outputBytes,
                        isTargetMet = true,
                        outputFilePath = result.outputFile.absolutePath,
                        mimeType = result.finalFormat.mimeType.ifEmpty { "image/jpeg" }
                    )
                )

                _uiState.value = ImageUiState.Finished(result)
            } catch (_: CancellationException) {
                _uiState.value = ImageUiState.Idle
            } catch (e: Exception) {
                _uiState.value = ImageUiState.Error(e.localizedMessage ?: "Image transformation failed.")
            }
        }
    }

    fun cancelCompression() {
        compressionJob?.cancel()
        _uiState.value = ImageUiState.Idle
    }

    fun saveOutputFileToUri(context: Context, destUri: Uri, result: ImageCompressResult): Boolean {
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

    fun createShareIntent(context: Context, result: ImageCompressResult): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, result.outputFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = result.finalFormat.mimeType.ifEmpty { "image/jpeg" }
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createOpenIntent(context: Context, result: ImageCompressResult): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, result.outputFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, result.finalFormat.mimeType.ifEmpty { "image/jpeg" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
