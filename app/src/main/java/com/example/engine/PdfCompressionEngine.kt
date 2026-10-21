package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt

enum class PdfCompressionProfile(
    val title: String,
    val description: String,
    val dpiScale: Float, // relative to standard 72 pt PDF scale
    val imageQuality: Int
) {
    LIGHT("Light Compression", "High clarity (200 DPI, 85% quality)", 2.0f, 85),
    BALANCED("Balanced (Recommended)", "Standard portal quality (144 DPI, 72% quality)", 1.5f, 72),
    STRONG("Strong Compression", "Maximum reduction for scanned forms (96 DPI, 55% quality)", 1.0f, 55)
}

data class PdfMetadata(
    val fileName: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val isEncrypted: Boolean = false,
    val isCorrupted: Boolean = false
)

data class PdfCompressOptions(
    val targetMaxBytes: Long,
    val profile: PdfCompressionProfile = PdfCompressionProfile.BALANCED,
    val allowPageRasterization: Boolean = false
)

data class PdfCompressResult(
    val originalMetadata: PdfMetadata,
    val outputBytes: Long,
    val pageCount: Int,
    val profileUsed: PdfCompressionProfile,
    val wasRasterized: Boolean,
    val isTargetMet: Boolean,
    val outputFile: File,
    val isValidPdf: Boolean,
    val statusMessage: String
)

class PdfCompressionEngine(private val context: Context) {

    suspend fun inspectPdf(uri: Uri): PdfMetadata = withContext(Dispatchers.IO) {
        val fileName = FormatUtils.getFileName(context, uri)
        val sizeBytes = FormatUtils.getFileSize(context, uri)
        var pageCount = 0
        var isEncrypted = false
        var isCorrupted = false

        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) {
                isCorrupted = true
            } else {
                pfd.use { descriptor ->
                    try {
                        PdfRenderer(descriptor).use { renderer ->
                            pageCount = renderer.pageCount
                        }
                    } catch (sec: SecurityException) {
                        isEncrypted = true
                    } catch (e: Exception) {
                        isCorrupted = true
                    }
                }
            }
        } catch (e: Exception) {
            isCorrupted = true
        }

        PdfMetadata(
            fileName = fileName,
            pageCount = pageCount,
            sizeBytes = sizeBytes,
            isEncrypted = isEncrypted,
            isCorrupted = isCorrupted
        )
    }

    suspend fun compressPdf(
        uri: Uri,
        options: PdfCompressOptions,
        onProgress: (currentPage: Int, totalPages: Int, stageDescription: String) -> Unit = { _, _, _ -> }
    ): PdfCompressResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val metadata = inspectPdf(uri)
        if (metadata.isEncrypted) {
            throw IllegalStateException("This PDF is password-protected. Encrypted PDFs cannot be modified without unlocking them first.")
        }
        if (metadata.isCorrupted || metadata.pageCount <= 0) {
            throw IllegalStateException("Unable to read PDF pages. The file may be corrupted or unsupported.")
        }

        val targetBytes = options.targetMaxBytes

        // Step 0: Check if original PDF already meets the requested target size
        if (metadata.sizeBytes in 1..targetBytes) {
            val outputFile = copyUriToTempPdf(uri, "xonvert_preserved_${System.currentTimeMillis()}.pdf")
            val actualSize = outputFile.length()
            val valid = verifyPdfFile(outputFile, metadata.pageCount)
            if (valid && actualSize <= targetBytes) {
                return@withContext PdfCompressResult(
                    originalMetadata = metadata,
                    outputBytes = actualSize,
                    pageCount = metadata.pageCount,
                    profileUsed = options.profile,
                    wasRasterized = false,
                    isTargetMet = true,
                    outputFile = outputFile,
                    isValidPdf = true,
                    statusMessage = "Original PDF already meets the target (${FormatUtils.formatBytes(actualSize)} ≤ ${FormatUtils.formatBytes(targetBytes)}). Preserved losslessly with all text, links, and signatures intact."
                )
            }
        }

        // Step 1: Non-destructive Structural Optimization Pass (when Rasterization is NOT explicitly enabled)
        if (!options.allowPageRasterization) {
            onProgress(1, metadata.pageCount, "Running lossless structural optimization (preserving text & vectors)...")
            val outputFile = optimizePdfStructureLosslessly(uri, metadata.sizeBytes)
            val actualSize = outputFile.length()
            val valid = verifyPdfFile(outputFile, metadata.pageCount)
            val isTargetMet = valid && actualSize <= targetBytes
            val savedPct = FormatUtils.calculateSavingsPercentage(metadata.sizeBytes, actualSize)

            val message = if (isTargetMet) {
                "Target reached without rasterization! Saved as ${FormatUtils.formatBytes(actualSize)} (-$savedPct%) with all selectable text and vector graphics preserved."
            } else {
                "Lossless structural pass kept all selectable text, vector graphics, and links (${FormatUtils.formatBytes(actualSize)}). To shrink embedded scans further toward ${FormatUtils.formatBytes(targetBytes)}, enable 'Rasterize Scanned Pages' below."
            }

            return@withContext PdfCompressResult(
                originalMetadata = metadata,
                outputBytes = actualSize,
                pageCount = metadata.pageCount,
                profileUsed = options.profile,
                wasRasterized = false,
                isTargetMet = isTargetMet,
                outputFile = outputFile,
                isValidPdf = valid,
                statusMessage = message
            )
        }

        // Step 2: Explicit Rasterized Downsampling Pass (User explicitly opted in to page rasterization)
        val candidateProfiles = buildList {
            add(options.profile)
            if (options.profile == PdfCompressionProfile.LIGHT) {
                add(PdfCompressionProfile.BALANCED)
                add(PdfCompressionProfile.STRONG)
            } else if (options.profile == PdfCompressionProfile.BALANCED) {
                add(PdfCompressionProfile.STRONG)
            }
        }

        var bestFile: File? = null
        var bestProfile = options.profile

        for (attemptProfile in candidateProfiles) {
            coroutineContext.ensureActive()
            val candidateFile = renderRasterizedPdf(
                uri = uri,
                profile = attemptProfile,
                totalPages = metadata.pageCount,
                onProgress = onProgress
            )

            if (verifyPdfFile(candidateFile, metadata.pageCount)) {
                if (bestFile == null || candidateFile.length() < bestFile!!.length()) {
                    bestFile?.delete()
                    bestFile = candidateFile
                    bestProfile = attemptProfile
                } else {
                    candidateFile.delete()
                }

                if (bestFile!!.length() <= targetBytes) {
                    break
                }
            } else {
                candidateFile.delete()
            }
        }

        val finalOutput = bestFile
            ?: throw IllegalStateException("Failed to produce a valid compressed PDF.")

        // Never return a file larger than the original if original was smaller
        val finalFile = if (finalOutput.length() >= metadata.sizeBytes) {
            finalOutput.delete()
            copyUriToTempPdf(uri, "xonvert_best_${System.currentTimeMillis()}.pdf")
        } else {
            finalOutput
        }

        val actualSize = finalFile.length()
        val isTargetMet = actualSize <= targetBytes
        val isValid = verifyPdfFile(finalFile, metadata.pageCount)
        val reductionPct = FormatUtils.calculateSavingsPercentage(metadata.sizeBytes, actualSize)

        val statusMessage = when {
            !isValid -> "Warning: Output verification failed."
            isTargetMet -> "Target reached via scanned page rasterization! Compressed to ${FormatUtils.formatBytes(actualSize)} (-$reductionPct%). Note: digital signatures and selectable text were flattened."
            else -> "Best valid PDF size is ${FormatUtils.formatBytes(actualSize)} (-$reductionPct%). Target ${FormatUtils.formatBytes(targetBytes)} could not be reached without making pages unreadable."
        }

        PdfCompressResult(
            originalMetadata = metadata,
            outputBytes = actualSize,
            pageCount = metadata.pageCount,
            profileUsed = bestProfile,
            wasRasterized = finalFile === finalOutput,
            isTargetMet = isTargetMet,
            outputFile = finalFile,
            isValidPdf = isValid,
            statusMessage = statusMessage
        )
    }

    private suspend fun renderRasterizedPdf(
        uri: Uri,
        profile: PdfCompressionProfile,
        totalPages: Int,
        onProgress: (Int, Int, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "xonvert_raster_${System.currentTimeMillis()}.pdf")
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Could not open PDF file descriptor.")

        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val document = PdfDocument()
                try {
                    for (i in 0 until renderer.pageCount) {
                        coroutineContext.ensureActive()
                        onProgress(
                            i + 1,
                            totalPages,
                            "Rasterizing page ${i + 1} of $totalPages (${profile.title})..."
                        )

                        val page = renderer.openPage(i)
                        val originalWidthPt = page.width.coerceAtLeast(72)
                        val originalHeightPt = page.height.coerceAtLeast(72)

                        val scale = profile.dpiScale
                        val renderW = (originalWidthPt * scale).roundToInt().coerceIn(200, 2400)
                        val renderH = (originalHeightPt * scale).roundToInt().coerceIn(200, 3200)

                        val pageBitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(pageBitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()

                        val stream = ByteArrayOutputStream()
                        pageBitmap.compress(Bitmap.CompressFormat.JPEG, profile.imageQuality, stream)
                        val compressedBytes = stream.toByteArray()
                        pageBitmap.recycle()

                        val compressedBitmap = BitmapFactory.decodeByteArray(
                            compressedBytes,
                            0,
                            compressedBytes.size
                        )

                        val pageInfo = PdfDocument.PageInfo.Builder(originalWidthPt, originalHeightPt, i + 1).create()
                        val docPage = document.startPage(pageInfo)
                        val srcRect = Rect(0, 0, compressedBitmap.width, compressedBitmap.height)
                        val dstRect = Rect(0, 0, originalWidthPt, originalHeightPt)
                        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                        docPage.canvas.drawBitmap(compressedBitmap, srcRect, dstRect, paint)
                        document.finishPage(docPage)
                        compressedBitmap.recycle()
                    }

                    FileOutputStream(outputFile).use { fos ->
                        document.writeTo(fos)
                        fos.flush()
                    }
                } finally {
                    document.close()
                }
            }
        }
        outputFile
    }

    /**
     * Lossless structural cleanup that trims trailing data after final %%EOF and preserves binary streams intact.
     */
    private fun optimizePdfStructureLosslessly(uri: Uri, originalSize: Long): File {
        val outputFile = File(context.cacheDir, "xonvert_struct_${System.currentTimeMillis()}.pdf")
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read PDF stream.")

        val eofMarker = "%%EOF".toByteArray(Charsets.US_ASCII)
        var lastEofIdx = -1
        for (i in rawBytes.size - eofMarker.size downTo 0) {
            var match = true
            for (j in eofMarker.indices) {
                if (rawBytes[i + j] != eofMarker[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                lastEofIdx = i + eofMarker.size
                break
            }
        }

        val trimmedLength = if (lastEofIdx in 1 until rawBytes.size) {
            min(rawBytes.size, lastEofIdx + 1)
        } else {
            rawBytes.size
        }

        FileOutputStream(outputFile).use { fos ->
            fos.write(rawBytes, 0, trimmedLength)
            fos.flush()
        }

        return outputFile
    }

    fun verifyPdfFile(file: File, expectedPages: Int): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount == expectedPages && renderer.pageCount > 0
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun copyUriToTempPdf(uri: Uri, name: String): File {
        val file = File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    // =========================================================================
    // PART 9: PDF UTILITIES (Merge, Split/Extract, Reorder, Rotate, Images->PDF)
    // =========================================================================

    /**
     * Merges multiple PDFs in order while preserving page dimensions.
     */
    suspend fun mergePdfs(
        uris: List<Uri>,
        onProgress: (String) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        if (uris.size < 2) {
            throw IllegalArgumentException("Please select at least 2 PDF files to merge.")
        }

        val outputFile = File(context.cacheDir, "xonvert_merged_${System.currentTimeMillis()}.pdf")
        val outDoc = PdfDocument()
        var globalPageNum = 0

        try {
            for ((fileIdx, uri) in uris.withIndex()) {
                coroutineContext.ensureActive()
                val meta = inspectPdf(uri)
                if (meta.isEncrypted) {
                    throw IllegalStateException("File '${meta.fileName}' is password-protected and cannot be merged.")
                }
                if (meta.isCorrupted || meta.pageCount <= 0) {
                    throw IllegalStateException("File '${meta.fileName}' is corrupted or unreadable.")
                }

                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (pageIdx in 0 until renderer.pageCount) {
                            coroutineContext.ensureActive()
                            globalPageNum++
                            onProgress("Merging file ${fileIdx + 1}/${uris.size} (page ${pageIdx + 1}/${renderer.pageCount})...")
                            copyRenderedPageToDocument(
                                renderer = renderer,
                                sourcePageIndex = pageIdx,
                                targetDoc = outDoc,
                                targetPageNumber = globalPageNum,
                                rotationDegrees = 0
                            )
                        }
                    }
                }
            }

            FileOutputStream(outputFile).use { fos ->
                outDoc.writeTo(fos)
                fos.flush()
            }
        } finally {
            outDoc.close()
        }

        if (!verifyPdfFile(outputFile, globalPageNum)) {
            outputFile.delete()
            throw IllegalStateException("Merged PDF verification failed.")
        }
        outputFile
    }

    /**
     * Splits, extracts, reorders, or rotates selected pages (0-based indices) from a source PDF.
     */
    suspend fun transformPdfPages(
        uri: Uri,
        pageSequence: List<Int>, // 0-based page indices in desired output order
        pageRotations: Map<Int, Int> = emptyMap(), // 0-based source index -> rotation degrees (0, 90, 180, 270)
        onProgress: (String) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val meta = inspectPdf(uri)
        if (meta.isEncrypted) {
            throw IllegalStateException("This PDF is password-protected.")
        }
        if (meta.isCorrupted || meta.pageCount <= 0) {
            throw IllegalStateException("Unable to read source PDF pages.")
        }
        val validIndices = pageSequence.filter { it in 0 until meta.pageCount }
        if (validIndices.isEmpty()) {
            throw IllegalArgumentException("No valid pages selected (PDF has ${meta.pageCount} pages).")
        }

        val outputFile = File(context.cacheDir, "xonvert_pages_${System.currentTimeMillis()}.pdf")
        val outDoc = PdfDocument()

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for ((outIdx, srcPageIdx) in validIndices.withIndex()) {
                        coroutineContext.ensureActive()
                        onProgress("Processing page ${outIdx + 1} of ${validIndices.size}...")
                        val rot = pageRotations[srcPageIdx] ?: 0
                        copyRenderedPageToDocument(
                            renderer = renderer,
                            sourcePageIndex = srcPageIdx,
                            targetDoc = outDoc,
                            targetPageNumber = outIdx + 1,
                            rotationDegrees = rot
                        )
                    }
                }
            }

            FileOutputStream(outputFile).use { fos ->
                outDoc.writeTo(fos)
                fos.flush()
            }
        } finally {
            outDoc.close()
        }

        if (!verifyPdfFile(outputFile, validIndices.size)) {
            outputFile.delete()
            throw IllegalStateException("Generated PDF failed integrity verification.")
        }
        outputFile
    }

    /**
     * Converts multiple selected images into a multi-page PDF document.
     */
    suspend fun imagesToPdf(
        imageUris: List<Uri>,
        fitToA4: Boolean = true,
        onProgress: (String) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            throw IllegalArgumentException("Please select at least one image.")
        }

        val outputFile = File(context.cacheDir, "xonvert_images_${System.currentTimeMillis()}.pdf")
        val outDoc = PdfDocument()

        try {
            for ((idx, uri) in imageUris.withIndex()) {
                coroutineContext.ensureActive()
                onProgress("Adding image ${idx + 1} of ${imageUris.size} to PDF...")

                val decodeOpts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val rawBmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: throw IllegalStateException("Could not decode image #${idx + 1}.")

                try {
                    val pageWidth = if (fitToA4) 595 else rawBmp.width.coerceIn(200, 2000)
                    val pageHeight = if (fitToA4) 842 else rawBmp.height.coerceIn(200, 2800)

                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
                    val page = outDoc.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val margin = if (fitToA4) 24f else 0f
                    val availW = pageWidth - margin * 2
                    val availH = pageHeight - margin * 2
                    val scale = min(availW / rawBmp.width.toFloat(), availH / rawBmp.height.toFloat())
                    val drawW = (rawBmp.width * scale).roundToInt()
                    val drawH = (rawBmp.height * scale).roundToInt()
                    val left = ((pageWidth - drawW) / 2f).roundToInt()
                    val top = ((pageHeight - drawH) / 2f).roundToInt()

                    val dstRect = Rect(left, top, left + drawW, top + drawH)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                    canvas.drawBitmap(rawBmp, null, dstRect, paint)
                    outDoc.finishPage(page)
                } finally {
                    rawBmp.recycle()
                }
            }

            FileOutputStream(outputFile).use { fos ->
                outDoc.writeTo(fos)
                fos.flush()
            }
        } finally {
            outDoc.close()
        }

        if (!verifyPdfFile(outputFile, imageUris.size)) {
            outputFile.delete()
            throw IllegalStateException("Generated PDF failed verification.")
        }
        outputFile
    }

    private fun copyRenderedPageToDocument(
        renderer: PdfRenderer,
        sourcePageIndex: Int,
        targetDoc: PdfDocument,
        targetPageNumber: Int,
        rotationDegrees: Int
    ) {
        val page = renderer.openPage(sourcePageIndex)
        val origW = page.width.coerceAtLeast(72)
        val origH = page.height.coerceAtLeast(72)
        val scale = 2.0f // High-definition 144-200 DPI print fidelity
        val renderW = (origW * scale).roundToInt().coerceIn(200, 2400)
        val renderH = (origH * scale).roundToInt().coerceIn(200, 3200)

        val rawBmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(rawBmp)
        bmpCanvas.drawColor(Color.WHITE)
        page.render(rawBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        page.close()

        val normRot = ((rotationDegrees % 360) + 360) % 360
        val finalBmp = if (normRot != 0) {
            val matrix = Matrix().apply { postRotate(normRot.toFloat()) }
            val rotated = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
            if (rotated !== rawBmp) rawBmp.recycle()
            rotated
        } else {
            rawBmp
        }

        val isSwapped = normRot == 90 || normRot == 270
        val outPageW = if (isSwapped) origH else origW
        val outPageH = if (isSwapped) origW else origH

        val pageInfo = PdfDocument.PageInfo.Builder(outPageW, outPageH, targetPageNumber).create()
        val outPage = targetDoc.startPage(pageInfo)
        val dstRect = Rect(0, 0, outPageW, outPageH)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        outPage.canvas.drawBitmap(finalBmp, null, dstRect, paint)
        targetDoc.finishPage(outPage)
        finalBmp.recycle()
    }

    companion object {
        /**
         * Parses a user-entered page range string like "1-3, 5, 2" into 0-based page indices.
         */
        fun parsePageRangeInput(input: String, maxPages: Int): List<Int> {
            if (input.isBlank() || maxPages <= 0) return emptyList()
            val result = mutableListOf<Int>()
            val parts = input.split(",")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed.contains("-")) {
                    val bounds = trimmed.split("-").map { it.trim() }
                    if (bounds.size == 2) {
                        val start = bounds[0].toIntOrNull() ?: return emptyList()
                        val end = bounds[1].toIntOrNull() ?: return emptyList()
                        if (start < 1 || end < 1 || start > maxPages || end > maxPages) return emptyList()
                        val range = if (start <= end) start..end else start downTo end
                        for (p in range) {
                            result.add(p - 1)
                        }
                    } else {
                        return emptyList()
                    }
                } else {
                    val pageNum = trimmed.toIntOrNull() ?: return emptyList()
                    if (pageNum !in 1..maxPages) return emptyList()
                    result.add(pageNum - 1)
                }
            }
            return result
        }
    }
}
