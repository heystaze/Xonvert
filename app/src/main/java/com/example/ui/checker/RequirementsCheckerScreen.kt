package com.example.ui.checker

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.ImageCompressionEngine
import com.example.engine.PdfCompressionEngine
import com.example.ui.components.CustomSizeInput
import com.example.ui.theme.ErrorRose
import com.example.ui.theme.SuccessGreen
import com.example.util.FormatUtils

data class RequirementCheckItem(
    val label: String,
    val actualValue: String,
    val requiredValue: String,
    val passed: Boolean
)

data class RequirementSpec(
    val maxSizeBytes: Long,
    val acceptedFormats: Set<String>, // e.g. "JPG", "PNG", "WEBP", "PDF"
    val requiredWidth: Int? = null,
    val requiredHeight: Int? = null
)

object RequirementsValidator {
    fun evaluateFile(
        actualSizeBytes: Long,
        detectedFormat: String, // "JPG", "PNG", "WEBP", "PDF", "OTHER"
        actualWidth: Int?,
        actualHeight: Int?,
        spec: RequirementSpec
    ): List<RequirementCheckItem> {
        val checks = mutableListOf<RequirementCheckItem>()

        // 1. File Size Check
        val sizePass = actualSizeBytes in 1..spec.maxSizeBytes
        checks.add(
            RequirementCheckItem(
                label = "Maximum File Size",
                actualValue = FormatUtils.formatBytes(actualSizeBytes),
                requiredValue = "≤ ${FormatUtils.formatBytes(spec.maxSizeBytes)}",
                passed = sizePass
            )
        )

        // 2. Format Check
        val normalizedFormat = if (detectedFormat.equals("JPEG", ignoreCase = true)) "JPG" else detectedFormat.uppercase()
        val formatPass = spec.acceptedFormats.any {
            it.equals(normalizedFormat, ignoreCase = true) ||
                    (it.equals("JPG", ignoreCase = true) && normalizedFormat == "JPEG")
        }
        checks.add(
            RequirementCheckItem(
                label = "File Format",
                actualValue = normalizedFormat,
                requiredValue = spec.acceptedFormats.joinToString(", "),
                passed = formatPass
            )
        )

        // 3. Optional Width & Height Check (for images)
        if (spec.requiredWidth != null && spec.requiredWidth > 0) {
            val wPass = actualWidth != null && actualWidth == spec.requiredWidth
            checks.add(
                RequirementCheckItem(
                    label = "Image Width",
                    actualValue = if (actualWidth != null) "${actualWidth}px" else "N/A (Not an image)",
                    requiredValue = "${spec.requiredWidth}px",
                    passed = wPass
                )
            )
        }

        if (spec.requiredHeight != null && spec.requiredHeight > 0) {
            val hPass = actualHeight != null && actualHeight == spec.requiredHeight
            checks.add(
                RequirementCheckItem(
                    label = "Image Height",
                    actualValue = if (actualHeight != null) "${actualHeight}px" else "N/A (Not an image)",
                    requiredValue = "${spec.requiredHeight}px",
                    passed = hPass
                )
            )
        }

        return checks
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RequirementsCheckerScreen(
    onNavigateBack: () -> Unit,
    onOpenInCompressor: (Uri, Boolean, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onNavigateBack() }

    val imageEngine = remember { ImageCompressionEngine(context) }
    val pdfEngine = remember { PdfCompressionEngine(context) }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var actualSizeBytes by remember { mutableStateOf(0L) }
    var detectedFormat by remember { mutableStateOf("JPG") }
    var actualWidth by remember { mutableStateOf<Int?>(null) }
    var actualHeight by remember { mutableStateOf<Int?>(null) }
    var pageCount by remember { mutableStateOf<Int?>(null) }

    var maxSizeText by remember { mutableStateOf("100") }
    var isMbUnit by remember { mutableStateOf(false) }
    var acceptedFormats by remember { mutableStateOf(setOf("JPG", "PNG", "PDF")) }
    var reqWidthText by remember { mutableStateOf("") }
    var reqHeightText by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
        }
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        fileName = FormatUtils.getFileName(context, uri)
        actualSizeBytes = FormatUtils.getFileSize(context, uri)
        val mime = FormatUtils.getMimeType(context, uri).lowercase()
        val isPdf = mime.contains("pdf") || fileName.lowercase().endsWith(".pdf")
        if (isPdf) {
            detectedFormat = "PDF"
            actualWidth = null
            actualHeight = null
            val meta = runCatching { pdfEngine.inspectPdf(uri) }.getOrNull()
            pageCount = meta?.pageCount
        } else {
            pageCount = null
            val meta = runCatching { imageEngine.inspectImage(uri) }.getOrNull()
            actualWidth = meta?.width
            actualHeight = meta?.height
            detectedFormat = when {
                mime.contains("png") || fileName.lowercase().endsWith(".png") -> "PNG"
                mime.contains("webp") || fileName.lowercase().endsWith(".webp") -> "WEBP"
                else -> "JPG"
            }
        }
    }

    val maxSizeBytes = FormatUtils.parseTargetSize(maxSizeText, isMbUnit) ?: (100 * 1024L)
    val spec = RequirementSpec(
        maxSizeBytes = maxSizeBytes,
        acceptedFormats = acceptedFormats.ifEmpty { setOf("JPG") },
        requiredWidth = reqWidthText.trim().toIntOrNull(),
        requiredHeight = reqHeightText.trim().toIntOrNull()
    )

    val checkResults = remember(selectedUri, actualSizeBytes, detectedFormat, actualWidth, actualHeight, spec) {
        if (selectedUri == null) emptyList()
        else RequirementsValidator.evaluateFile(
            actualSizeBytes = actualSizeBytes,
            detectedFormat = detectedFormat,
            actualWidth = actualWidth,
            actualHeight = actualHeight,
            spec = spec
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Requirements Checker",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("checker_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Verify whether an image or PDF meets your custom size, format, and pixel dimension rules before uploading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. Enter Requirements Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "1. Specify Target Requirements",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        CustomSizeInput(
                            value = maxSizeText,
                            onValueChange = { maxSizeText = it },
                            isMb = isMbUnit,
                            onUnitToggle = { isMbUnit = it }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Accepted Formats",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("JPG", "PNG", "WEBP", "PDF").forEach { fmt ->
                                val selected = acceptedFormats.contains(fmt)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        acceptedFormats = if (selected && acceptedFormats.size > 1) {
                                            acceptedFormats - fmt
                                        } else {
                                            acceptedFormats + fmt
                                        }
                                    },
                                    label = { Text(fmt) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Optional Exact Image Dimensions (Leave blank if any)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = reqWidthText,
                                onValueChange = { reqWidthText = it },
                                label = { Text("Width (px)") },
                                placeholder = { Text("Optional") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = reqHeightText,
                                onValueChange = { reqHeightText = it },
                                label = { Text("Height (px)") },
                                placeholder = { Text("Optional") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Select File to Check
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checker_pick_file_card")
                        .clickable { filePickerLauncher.launch("*/*") },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = "Select File",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (selectedUri == null) "2. Select File to Inspect" else fileName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedUri == null) {
                                    "Tap to browse an image or PDF document"
                                } else {
                                    buildString {
                                        append("${FormatUtils.formatBytes(actualSizeBytes)} • $detectedFormat")
                                        if (actualWidth != null && actualHeight != null) {
                                            append(" • ${actualWidth}×${actualHeight}px")
                                        }
                                        if (pageCount != null) {
                                            append(" • $pageCount pages")
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (selectedUri == null) "Browse" else "Change",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 3. Check Results Report
            if (selectedUri != null && checkResults.isNotEmpty()) {
                val allPassed = checkResults.all { it.passed }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checker_results_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (allPassed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (allPassed) SuccessGreen else ErrorRose,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (allPassed) "All Specified Requirements Met" else "Requirements Not Met",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (allPassed) SuccessGreen else ErrorRose
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            checkResults.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (item.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = if (item.passed) "Pass" else "Fail",
                                            tint = if (item.passed) SuccessGreen else ErrorRose,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Actual: ${item.actualValue}  |  Required: ${item.requiredValue}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = if (item.passed) "PASS" else "FAIL",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.passed) SuccessGreen else ErrorRose
                                        )
                                    }
                                }
                            }

                            if (!allPassed) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        onOpenInCompressor(
                                            selectedUri!!,
                                            detectedFormat == "PDF",
                                            maxSizeBytes
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Fix in Compressor", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
