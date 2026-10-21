package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

enum class ImageOutputFormat(val displayName: String, val extension: String, val mimeType: String) {
    AUTO("Auto (Recommended)", "", ""),
    JPEG("JPEG (.jpg)", "jpg", "image/jpeg"),
    WEBP("WebP (.webp)", "webp", "image/webp"),
    PNG("PNG (.png)", "png", "image/png")
}

enum class ResizePreset(val displayName: String, val maxDimension: Int?) {
    ORIGINAL("Keep Original Dimensions", null),
    SCALE_1920("Max 1920px (Full HD)", 1920),
    SCALE_1280("Max 1280px (HD)", 1280),
    SCALE_800("Max 800px (Web/Document)", 800),
    CUSTOM("Custom Dimensions", null)
}

data class ImageMetadata(
    val fileName: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val sizeBytes: Long,
    val hasAlpha: Boolean
)

data class ImageCompressOptions(
    val targetMaxBytes: Long,
    val format: ImageOutputFormat = ImageOutputFormat.AUTO,
    val resizePreset: ResizePreset = ResizePreset.ORIGINAL,
    val customMaxWidth: Int? = null,
    val customMaxHeight: Int? = null,
    val preserveAspectRatio: Boolean = true,
    val allowDimensionDownscaling: Boolean = true
)

data class ImageCompressResult(
    val originalMetadata: ImageMetadata,
    val outputBytes: Long,
    val outputWidth: Int,
    val outputHeight: Int,
    val finalQuality: Int,
    val finalFormat: ImageOutputFormat,
    val isTargetMet: Boolean,
    val outputFile: File,
    val iterationsCount: Int,
    val statusMessage: String,
    val formatConversionNotice: String? = null
)

enum class ImageResizeMode {
    PERCENTAGE,
    PIXELS
}

data class ImageTransformOptions(
    val resizeMode: ImageResizeMode = ImageResizeMode.PERCENTAGE,
    val scalePercentage: Int = 100,
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    val preserveAspectRatio: Boolean = true,
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val outputFormat: ImageOutputFormat = ImageOutputFormat.AUTO,
    val quality: Int = 92
)

class ImageCompressionEngine(private val context: Context) {

    suspend fun inspectImage(uri: Uri): ImageMetadata = withContext(Dispatchers.IO) {
        val fileName = FormatUtils.getFileName(context, uri)
        val sizeBytes = FormatUtils.getFileSize(context, uri)
        val mimeType = FormatUtils.getMimeType(context, uri)

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IllegalArgumentException("Selected file is not a valid or supported image.")
        }

        val orientation = getExifOrientation(uri)
        val isRotated = orientation == 90 || orientation == 270
        val width = if (isRotated) options.outHeight else options.outWidth
        val height = if (isRotated) options.outWidth else options.outHeight

        // Sample a small bitmap to accurately detect alpha channel presence
        val sampleOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, 256, 256)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampledAlpha = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, sampleOptions)?.let { bmp ->
                val alpha = bmp.hasAlpha()
                bmp.recycle()
                alpha
            }
        } ?: false

        val hasAlpha = sampledAlpha && (
                options.outMimeType?.contains("png", ignoreCase = true) == true ||
                        options.outMimeType?.contains("webp", ignoreCase = true) == true ||
                        mimeType.contains("png", ignoreCase = true) ||
                        mimeType.contains("webp", ignoreCase = true)
                )

        ImageMetadata(
            fileName = fileName,
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            mimeType = options.outMimeType ?: mimeType,
            sizeBytes = sizeBytes,
            hasAlpha = hasAlpha
        )
    }

    suspend fun compressImage(
        uri: Uri,
        options: ImageCompressOptions,
        onProgress: (step: String, iteration: Int, maxIterations: Int) -> Unit = { _, _, _ -> }
    ): ImageCompressResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val metadata = inspectImage(uri)
        val targetBytes = options.targetMaxBytes
        if (targetBytes <= 0L) {
            throw IllegalArgumentException("Target size must be greater than 0 bytes.")
        }

        val sourceFormat = getFormatFromMime(metadata.mimeType)
        val isSameFormatRequested =
            options.format == ImageOutputFormat.AUTO || options.format == sourceFormat

        // 1. Check whether the original file already meets the target without modifications
        if (metadata.sizeBytes in 1..targetBytes &&
            options.resizePreset == ResizePreset.ORIGINAL &&
            isSameFormatRequested
        ) {
            val ext = getExtensionFromMime(metadata.mimeType)
            val cachedFile = copyUriToTemp(uri, "xonvert_orig_${System.currentTimeMillis()}.$ext")
            val actualLen = cachedFile.length()
            if (actualLen in 1..targetBytes) {
                return@withContext ImageCompressResult(
                    originalMetadata = metadata,
                    outputBytes = actualLen,
                    outputWidth = metadata.width,
                    outputHeight = metadata.height,
                    finalQuality = 100,
                    finalFormat = sourceFormat,
                    isTargetMet = true,
                    outputFile = cachedFile,
                    iterationsCount = 0,
                    statusMessage = "Original image already meets the target (${FormatUtils.formatBytes(actualLen)} ≤ ${FormatUtils.formatBytes(targetBytes)}). Preserved without quality loss."
                )
            }
        }

        onProgress("Decoding image with EXIF orientation...", 1, 10)
        val sourceBitmap = loadOrientedBitmap(uri, maxAllowedDimension = 4096)
            ?: throw IllegalStateException("Failed to decode image from selected file.")

        try {
            val actualHasAlpha = metadata.hasAlpha || sourceBitmap.hasAlpha()
            val (targetFormat, conversionNotice) = resolveOutputFormatWithNotice(
                requested = options.format,
                hasAlpha = actualHasAlpha,
                sourceMime = metadata.mimeType
            )

            // Calculate initial target bounds based on resize preset
            val (initW, initH) = calculateTargetBounds(
                sourceWidth = sourceBitmap.width,
                sourceHeight = sourceBitmap.height,
                preset = options.resizePreset,
                customMaxWidth = options.customMaxWidth,
                customMaxHeight = options.customMaxHeight,
                preserveAspectRatio = options.preserveAspectRatio
            )

            var currentBitmap = if (initW != sourceBitmap.width || initH != sourceBitmap.height) {
                Bitmap.createScaledBitmap(sourceBitmap, initW, initH, true)
            } else {
                sourceBitmap
            }

            val compressFormat = getCompressFormat(targetFormat)
            var bestQuality = 90
            var bestBytes: ByteArray? = null
            var smallestBytes: ByteArray? = null
            var smallestQuality = 15
            var iterations = 0
            val maxTotalIterations = 12

            // Stage 1: Bounded binary search on quality (when format supports quality adjustment)
            if (targetFormat != ImageOutputFormat.PNG) {
                onProgress("Optimizing encoding quality...", 2, maxTotalIterations)
                var lowQ = 12
                var highQ = 96

                while (lowQ <= highQ && iterations < 6) {
                    coroutineContext.ensureActive()
                    iterations++
                    val midQ = (lowQ + highQ) / 2
                    onProgress("Testing quality ($midQ%)...", iterations, maxTotalIterations)

                    val currentBytes = encodeBitmap(currentBitmap, compressFormat, midQ)
                    if (smallestBytes == null || currentBytes.size < smallestBytes.size) {
                        smallestBytes = currentBytes
                        smallestQuality = midQ
                    }

                    if (currentBytes.size <= targetBytes) {
                        bestBytes = currentBytes
                        bestQuality = midQ
                        lowQ = midQ + 1 // Try higher quality while staying under target
                    } else {
                        highQ = midQ - 1
                    }
                }
            } else {
                // PNG is lossless; quality parameter is ignored by Bitmap.compress(PNG)
                coroutineContext.ensureActive()
                iterations++
                onProgress("Encoding lossless PNG...", iterations, maxTotalIterations)
                val pngBytes = encodeBitmap(currentBitmap, Bitmap.CompressFormat.PNG, 100)
                smallestBytes = pngBytes
                smallestQuality = 100
                if (pngBytes.size <= targetBytes) {
                    bestBytes = pngBytes
                    bestQuality = 100
                }
            }

            // Stage 2: Progressive dimension reduction if quality alone is insufficient and allowed
            val dimensionScales = listOf(0.85f, 0.72f, 0.58f, 0.45f, 0.32f, 0.22f)
            var scaleIndex = 0

            while ((bestBytes == null || bestBytes.size > targetBytes) &&
                options.allowDimensionDownscaling &&
                scaleIndex < dimensionScales.size &&
                iterations < maxTotalIterations
            ) {
                coroutineContext.ensureActive()
                val scale = dimensionScales[scaleIndex]
                scaleIndex++

                val newW = (initW * scale).roundToInt().coerceAtLeast(64)
                val newH = (initH * scale).roundToInt().coerceAtLeast(64)
                if (newW == currentBitmap.width && newH == currentBitmap.height) continue

                onProgress("Scaling dimensions to ${newW}×${newH}px...", iterations + 1, maxTotalIterations)
                val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, newW, newH, true)
                if (currentBitmap !== sourceBitmap) {
                    currentBitmap.recycle()
                }
                currentBitmap = scaledBitmap

                if (targetFormat == ImageOutputFormat.PNG) {
                    iterations++
                    val pngBytes = encodeBitmap(currentBitmap, Bitmap.CompressFormat.PNG, 100)
                    if (smallestBytes == null || pngBytes.size < smallestBytes.size) {
                        smallestBytes = pngBytes
                    }
                    if (pngBytes.size <= targetBytes) {
                        bestBytes = pngBytes
                        bestQuality = 100
                        break
                    }
                } else {
                    // Mini binary search on quality for this resolution
                    var lowQ = 25
                    var highQ = 86
                    var foundAtScale = false
                    for (step in 0 until 3) {
                        coroutineContext.ensureActive()
                        if (lowQ > highQ) break
                        iterations++
                        val midQ = (lowQ + highQ) / 2
                        val testBytes = encodeBitmap(currentBitmap, compressFormat, midQ)
                        if (smallestBytes == null || testBytes.size < smallestBytes.size) {
                            smallestBytes = testBytes
                            smallestQuality = midQ
                        }
                        if (testBytes.size <= targetBytes) {
                            bestBytes = testBytes
                            bestQuality = midQ
                            foundAtScale = true
                            lowQ = midQ + 1
                        } else {
                            highQ = midQ - 1
                        }
                    }
                    if (foundAtScale) break
                }
            }

            val finalBytes = bestBytes ?: smallestBytes ?: encodeBitmap(currentBitmap, compressFormat, 35)
            val finalQualityUsed = if (bestBytes != null) bestQuality else smallestQuality
            val finalW = currentBitmap.width
            val finalH = currentBitmap.height

            if (currentBitmap !== sourceBitmap) {
                currentBitmap.recycle()
            }

            val ext = targetFormat.extension.ifEmpty { "jpg" }
            val outputFile = File(context.cacheDir, "xonvert_${System.currentTimeMillis()}.$ext")
            FileOutputStream(outputFile).use { fos ->
                fos.write(finalBytes)
                fos.flush()
            }

            val actualOutputSize = outputFile.length()
            val isMet = actualOutputSize in 1..targetBytes
            val reductionPct = FormatUtils.calculateSavingsPercentage(metadata.sizeBytes, actualOutputSize)

            val statusMessage = buildString {
                if (isMet) {
                    append("Target met! Saved as ${FormatUtils.formatBytes(actualOutputSize)} (-$reductionPct%).")
                } else {
                    append("Smallest achievable output is ${FormatUtils.formatBytes(actualOutputSize)} for target ${FormatUtils.formatBytes(targetBytes)}. ")
                    if (!options.allowDimensionDownscaling) {
                        append("Enable Adaptive Downscaling to reach smaller sizes.")
                    } else if (targetFormat == ImageOutputFormat.PNG) {
                        append("PNG is lossless; try WebP or Auto format for much smaller file sizes.")
                    } else {
                        append("Try a lower resolution preset or slightly higher target.")
                    }
                }
                if (conversionNotice != null) {
                    append(" ")
                    append(conversionNotice)
                }
            }

            ImageCompressResult(
                originalMetadata = metadata,
                outputBytes = actualOutputSize,
                outputWidth = finalW,
                outputHeight = finalH,
                finalQuality = finalQualityUsed,
                finalFormat = targetFormat,
                isTargetMet = isMet,
                outputFile = outputFile,
                iterationsCount = iterations,
                statusMessage = statusMessage,
                formatConversionNotice = conversionNotice
            )
        } finally {
            if (!sourceBitmap.isRecycled) {
                sourceBitmap.recycle()
            }
        }
    }

    /**
     * Part 8: Dedicated Image Resizing, Rotation & Format Conversion without forcing target-size loss.
     */
    suspend fun resizeAndConvertImage(
        uri: Uri,
        options: ImageTransformOptions,
        onProgress: (String) -> Unit = {}
    ): ImageCompressResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val metadata = inspectImage(uri)
        onProgress("Decoding source image...")
        val sourceBitmap = loadOrientedBitmap(uri, maxAllowedDimension = 4096)
            ?: throw IllegalStateException("Failed to decode image.")

        try {
            // 1. Apply rotation if requested
            val rotatedBitmap = if (options.rotationDegrees % 360 != 0) {
                onProgress("Rotating image by ${options.rotationDegrees}°...")
                val matrix = Matrix().apply { postRotate(options.rotationDegrees.toFloat()) }
                Bitmap.createBitmap(
                    sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true
                )
            } else {
                sourceBitmap
            }

            // 2. Calculate target dimensions
            val baseW = rotatedBitmap.width
            val baseH = rotatedBitmap.height
            val (targetW, targetH) = when (options.resizeMode) {
                ImageResizeMode.PERCENTAGE -> {
                    val pct = options.scalePercentage.coerceIn(5, 200) / 100f
                    Pair(
                        (baseW * pct).roundToInt().coerceAtLeast(16),
                        (baseH * pct).roundToInt().coerceAtLeast(16)
                    )
                }
                ImageResizeMode.PIXELS -> {
                    val reqW = (options.targetWidth ?: baseW).coerceIn(16, 8192)
                    val reqH = (options.targetHeight ?: baseH).coerceIn(16, 8192)
                    if (options.preserveAspectRatio) {
                        calculateAspectFit(baseW, baseH, reqW, reqH)
                    } else {
                        Pair(reqW, reqH)
                    }
                }
            }

            onProgress("Resizing to ${targetW}×${targetH}px...")
            val scaledBitmap = if (targetW != baseW || targetH != baseH) {
                Bitmap.createScaledBitmap(rotatedBitmap, targetW, targetH, true)
            } else {
                rotatedBitmap
            }

            if (rotatedBitmap !== sourceBitmap && rotatedBitmap !== scaledBitmap) {
                rotatedBitmap.recycle()
            }

            // 3. Resolve format and handle transparency cleanly
            val hasAlpha = metadata.hasAlpha || scaledBitmap.hasAlpha()
            val sourceFormat = getFormatFromMime(metadata.mimeType)
            val finalFormat = when (options.outputFormat) {
                ImageOutputFormat.AUTO -> sourceFormat
                else -> options.outputFormat
            }

            val bitmapToEncode = if (hasAlpha && finalFormat == ImageOutputFormat.JPEG) {
                // Composite transparent pixels over white instead of turning black
                val opaque = Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(opaque)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                opaque
            } else {
                scaledBitmap
            }

            onProgress("Encoding as ${finalFormat.extension.uppercase()}...")
            val compressFormat = getCompressFormat(finalFormat)
            val quality = if (finalFormat == ImageOutputFormat.PNG) 100 else options.quality.coerceIn(10, 100)
            val encodedBytes = encodeBitmap(bitmapToEncode, compressFormat, quality)

            if (bitmapToEncode !== scaledBitmap) {
                bitmapToEncode.recycle()
            }
            if (scaledBitmap !== sourceBitmap) {
                scaledBitmap.recycle()
            }

            val ext = finalFormat.extension.ifEmpty { "jpg" }
            val outputFile = File(context.cacheDir, "xonvert_converted_${System.currentTimeMillis()}.$ext")
            FileOutputStream(outputFile).use { fos ->
                fos.write(encodedBytes)
                fos.flush()
            }

            val conversionNotice = if (finalFormat != sourceFormat) {
                "Converted from ${sourceFormat.extension.uppercase()} to ${finalFormat.extension.uppercase()}."
            } else null

            val actualSize = outputFile.length()
            ImageCompressResult(
                originalMetadata = metadata,
                outputBytes = actualSize,
                outputWidth = targetW,
                outputHeight = targetH,
                finalQuality = quality,
                finalFormat = finalFormat,
                isTargetMet = true,
                outputFile = outputFile,
                iterationsCount = 1,
                statusMessage = buildString {
                    append("Output ready: ${targetW}×${targetH}px (${FormatUtils.formatBytes(actualSize)}).")
                    if (conversionNotice != null) append(" $conversionNotice")
                },
                formatConversionNotice = conversionNotice
            )
        } finally {
            if (!sourceBitmap.isRecycled) {
                sourceBitmap.recycle()
            }
        }
    }

    private fun encodeBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return stream.toByteArray()
    }

    private fun loadOrientedBitmap(uri: Uri, maxAllowedDimension: Int = 4096): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                maxAllowedDimension,
                maxAllowedDimension
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val rotation = getExifOrientation(uri)
        if (rotation == 0) return rawBitmap

        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        if (rotated !== rawBitmap) {
            rawBitmap.recycle()
        }
        return rotated
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    private fun getExifOrientation(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun resolveOutputFormatWithNotice(
        requested: ImageOutputFormat,
        hasAlpha: Boolean,
        sourceMime: String
    ): Pair<ImageOutputFormat, String?> {
        val sourceFormat = getFormatFromMime(sourceMime)
        return when (requested) {
            ImageOutputFormat.AUTO -> {
                if (hasAlpha) {
                    val notice = if (sourceFormat != ImageOutputFormat.WEBP) {
                        "Converted to WebP to preserve alpha transparency while reducing size."
                    } else null
                    Pair(ImageOutputFormat.WEBP, notice)
                } else {
                    val notice = if (sourceFormat != ImageOutputFormat.JPEG) {
                        "Converted from ${sourceFormat.extension.uppercase()} to JPEG for target size efficiency."
                    } else null
                    Pair(ImageOutputFormat.JPEG, notice)
                }
            }
            ImageOutputFormat.JPEG -> {
                if (hasAlpha) {
                    // Never silently convert a transparent PNG to JPEG!
                    Pair(
                        ImageOutputFormat.WEBP,
                        "Transparent image kept as WebP instead of JPEG to preserve alpha channel."
                    )
                } else {
                    val notice = if (sourceFormat != ImageOutputFormat.JPEG) {
                        "Converted to JPEG."
                    } else null
                    Pair(ImageOutputFormat.JPEG, notice)
                }
            }
            ImageOutputFormat.WEBP -> {
                val notice = if (sourceFormat != ImageOutputFormat.WEBP) "Converted to WebP." else null
                Pair(ImageOutputFormat.WEBP, notice)
            }
            ImageOutputFormat.PNG -> {
                val notice = if (sourceFormat != ImageOutputFormat.PNG) "Converted to PNG." else null
                Pair(ImageOutputFormat.PNG, notice)
            }
        }
    }

    private fun getCompressFormat(format: ImageOutputFormat): Bitmap.CompressFormat {
        return when (format) {
            ImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageOutputFormat.WEBP, ImageOutputFormat.AUTO -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }
    }

    fun calculateTargetBounds(
        sourceWidth: Int,
        sourceHeight: Int,
        preset: ResizePreset,
        customMaxWidth: Int?,
        customMaxHeight: Int?,
        preserveAspectRatio: Boolean
    ): Pair<Int, Int> {
        val maxDim = when (preset) {
            ResizePreset.ORIGINAL -> return Pair(sourceWidth, sourceHeight)
            ResizePreset.SCALE_1920 -> 1920
            ResizePreset.SCALE_1280 -> 1280
            ResizePreset.SCALE_800 -> 800
            ResizePreset.CUSTOM -> {
                val cw = customMaxWidth ?: sourceWidth
                val ch = customMaxHeight ?: sourceHeight
                if (!preserveAspectRatio) return Pair(cw.coerceAtLeast(10), ch.coerceAtLeast(10))
                return calculateAspectFit(sourceWidth, sourceHeight, cw, ch)
            }
        }

        if (sourceWidth <= maxDim && sourceHeight <= maxDim) {
            return Pair(sourceWidth, sourceHeight)
        }

        return if (sourceWidth >= sourceHeight) {
            val scale = maxDim.toFloat() / sourceWidth.toFloat()
            Pair(maxDim, (sourceHeight * scale).roundToInt().coerceAtLeast(1))
        } else {
            val scale = maxDim.toFloat() / sourceHeight.toFloat()
            Pair((sourceWidth * scale).roundToInt().coerceAtLeast(1), maxDim)
        }
    }

    private fun calculateAspectFit(srcW: Int, srcH: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
        val ratio = minOf(maxW.toFloat() / srcW, maxH.toFloat() / srcH)
        return Pair(
            (srcW * ratio).roundToInt().coerceAtLeast(1),
            (srcH * ratio).roundToInt().coerceAtLeast(1)
        )
    }

    private fun copyUriToTemp(uri: Uri, tempName: String): File {
        val tempFile = File(context.cacheDir, tempName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private fun getExtensionFromMime(mime: String): String {
        return when {
            mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> "jpg"
            mime.contains("png", ignoreCase = true) -> "png"
            mime.contains("webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
    }

    fun getFormatFromMime(mime: String): ImageOutputFormat {
        return when {
            mime.contains("png", ignoreCase = true) -> ImageOutputFormat.PNG
            mime.contains("webp", ignoreCase = true) -> ImageOutputFormat.WEBP
            else -> ImageOutputFormat.JPEG
        }
    }
}
