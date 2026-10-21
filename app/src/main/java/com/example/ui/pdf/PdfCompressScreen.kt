package com.example.ui.pdf

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.PdfCompressResult
import com.example.engine.PdfCompressionProfile
import com.example.ui.components.CustomSizeInput
import com.example.ui.components.PresetSizeRow
import com.example.ui.components.SavingsBadge
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfCompressScreen(
    viewModel: PdfCompressViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onNavigateBack() }

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val utilityMode by viewModel.utilityMode.collectAsStateWithLifecycle()
    val customPresets by viewModel.customPdfPresets.collectAsStateWithLifecycle()
    val selectedUri by viewModel.selectedUri.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val targetSizeBytes by viewModel.targetSizeBytes.collectAsStateWithLifecycle()
    val customSizeText by viewModel.customSizeText.collectAsStateWithLifecycle()
    val isMbUnit by viewModel.isMbUnit.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val allowPageRasterization by viewModel.allowPageRasterization.collectAsStateWithLifecycle()

    val mergePdfUris by viewModel.mergePdfUris.collectAsStateWithLifecycle()
    val imagesForPdfUris by viewModel.imagesForPdfUris.collectAsStateWithLifecycle()
    val pageRangeInput by viewModel.pageRangeInput.collectAsStateWithLifecycle()
    val pageSequence by viewModel.pageSequence.collectAsStateWithLifecycle()
    val pageRotations by viewModel.pageRotations.collectAsStateWithLifecycle()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.onPdfSelected(uri)
        }
    }

    val multiPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onMergePdfsSelected(uris)
        }
    }

    val multiImageForPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onImagesForPdfSelected(uris)
        }
    }

    var pendingSaveResult by remember { mutableStateOf<PdfCompressResult?>(null) }
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val res = pendingSaveResult
        if (destUri != null && res != null) {
            val ok = viewModel.saveOutputFileToUri(context, destUri, res)
            if (ok) {
                Toast.makeText(context, "Saved PDF to selected location!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to save PDF file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pdfPresets = remember {
        listOf(
            "100 KB" to 100 * 1024L,
            "200 KB" to 200 * 1024L,
            "500 KB" to 500 * 1024L,
            "1 MB" to 1024 * 1024L,
            "2 MB" to 2 * 1024 * 1024L
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (activeTab == PdfToolTab.COMPRESS) "Compress PDF" else "PDF Utilities",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("pdf_compress_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if ((selectedUri != null || mergePdfUris.isNotEmpty() || imagesForPdfUris.isNotEmpty()) &&
                        uiState !is PdfUiState.Compressing
                    ) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Selection"
                            )
                        }
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
            // Mode Switcher Tabs
            item {
                TabRow(
                    selectedTabIndex = activeTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = activeTab == PdfToolTab.COMPRESS,
                        onClick = { viewModel.setActiveTab(PdfToolTab.COMPRESS) },
                        text = { Text("Compress PDF", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("tab_pdf_compress")
                    )
                    Tab(
                        selected = activeTab == PdfToolTab.UTILITIES,
                        onClick = { viewModel.setActiveTab(PdfToolTab.UTILITIES) },
                        text = { Text("PDF Utilities", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("tab_pdf_utilities")
                    )
                }
            }

            if (uiState is PdfUiState.Compressing) {
                val state = uiState as PdfUiState.Compressing
                item {
                    PdfCompressingCard(
                        currentPage = state.currentPage,
                        totalPages = state.totalPages,
                        stage = state.stage,
                        onCancel = { viewModel.cancelCompression() }
                    )
                }
            } else if (uiState is PdfUiState.Finished) {
                val state = uiState as PdfUiState.Finished
                item {
                    PdfResultCard(
                        result = state.result,
                        onOpen = {
                            runCatching {
                                val openIntent = viewModel.createOpenIntent(context, state.result)
                                context.startActivity(Intent.createChooser(openIntent, "Open PDF"))
                            }.onFailure {
                                Toast.makeText(context, "No PDF viewer app found.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSave = {
                            pendingSaveResult = state.result
                            val base = state.result.originalMetadata.fileName.substringBeforeLast(".")
                            saveFileLauncher.launch("${base}_xonvert.pdf")
                        },
                        onShare = {
                            val intent = viewModel.createShareIntent(context, state.result)
                            context.startActivity(Intent.createChooser(intent, "Share PDF"))
                        },
                        onCompressAnother = { viewModel.clearSelection() }
                    )
                }
            } else if (uiState is PdfUiState.Error) {
                val state = uiState as PdfUiState.Error
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PDF Operation Failed",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.clearSelection() },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Select Another File")
                            }
                        }
                    }
                }
            }

            if (activeTab == PdfToolTab.COMPRESS) {
                if (selectedUri == null) {
                    item {
                        PickPdfPlaceholderCard(onPickPdf = { pdfPickerLauncher.launch("application/pdf") })
                    }
                } else if (uiState !is PdfUiState.Finished && uiState !is PdfUiState.Compressing) {
                    item {
                        metadata?.let { meta ->
                            SelectedPdfCard(
                                metadata = meta,
                                onChangePdf = { pdfPickerLauncher.launch("application/pdf") }
                            )
                        }
                    }

                    // Saved PDF Presets (Part 7)
                    if (customPresets.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.BookmarkBorder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Saved PDF Presets",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        TextButton(onClick = { showSavePresetDialog = true }) {
                                            Icon(
                                                imageVector = Icons.Default.BookmarkAdd,
                                                contentDescription = "Save Preset",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Save Current", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        customPresets.forEach { preset ->
                                            FilterChip(
                                                selected = targetSizeBytes == preset.targetSizeBytes,
                                                onClick = { viewModel.applyCustomPreset(preset) },
                                                label = {
                                                    Text("${preset.name} (${FormatUtils.formatBytes(preset.targetSizeBytes)})")
                                                },
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Target Maximum Size",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                PresetSizeRow(
                                    presets = pdfPresets,
                                    selectedSizeBytes = targetSizeBytes,
                                    onPresetSelected = { viewModel.setTargetPreset(it) }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                CustomSizeInput(
                                    value = customSizeText,
                                    onValueChange = { viewModel.setCustomSize(it, isMbUnit) },
                                    isMb = isMbUnit,
                                    onUnitToggle = { viewModel.setCustomSize(customSizeText, it) }
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Compression Mode & Integrity",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                // Explicit Rasterization Fallback Option with Warning (Part 5)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Rasterize Scanned Pages",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (allowPageRasterization) {
                                                "Renders pages as compressed images for large scanned PDFs."
                                            } else {
                                                "Off (Default): Runs lossless structural cleanup keeping text & vectors intact."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = allowPageRasterization,
                                        onCheckedChange = { viewModel.setAllowPageRasterization(it) },
                                        modifier = Modifier.testTag("rasterize_pdf_switch")
                                    )
                                }

                                if (allowPageRasterization) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = WarningAmber.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("rasterization_warning_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Warning",
                                                tint = WarningAmber,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Warning: Page rasterization converts PDF pages into compressed images. It may remove selectable text, hyperlinks, annotations, form fields, vector graphics, accessibility tags, metadata, and invalidates digital signatures.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Rasterization Quality Profile",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    PdfCompressionProfile.values().forEach { profile ->
                                        val isSelected = selectedProfile == profile
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { viewModel.setProfile(profile) },
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { viewModel.setProfile(profile) }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = profile.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = profile.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.startCompression() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_pdf_compress_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "Compress PDF (Target: ${FormatUtils.formatBytes(targetSizeBytes)})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (uiState !is PdfUiState.Finished && uiState !is PdfUiState.Compressing) {
                // PART 9: PDF UTILITIES TAB
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdfUtilityMode.values().forEach { mode ->
                            FilterChip(
                                selected = utilityMode == mode,
                                onClick = { viewModel.setUtilityMode(mode) },
                                label = { Text(mode.title) },
                                modifier = Modifier.testTag("pdf_util_${mode.name.lowercase()}"),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                when (utilityMode) {
                    PdfUtilityMode.MERGE -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.MergeType,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Merge Multiple PDFs",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Combine 2 or more PDF files in sequence into a single PDF document.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedButton(
                                        onClick = { multiPdfPickerLauncher.launch("application/pdf") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            if (mergePdfUris.isEmpty()) "Select PDFs to Merge"
                                            else "Selected ${mergePdfUris.size} PDFs (Tap to Reselect)"
                                        )
                                    }

                                    if (mergePdfUris.size >= 2) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { viewModel.runMergePdfs() },
                                            modifier = Modifier.fillMaxWidth().height(50.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Merge ${mergePdfUris.size} PDFs Now", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    PdfUtilityMode.SPLIT_EXTRACT -> {
                        item {
                            if (selectedUri == null) {
                                PickPdfPlaceholderCard(onPickPdf = { pdfPickerLauncher.launch("application/pdf") })
                            } else {
                                metadata?.let { meta ->
                                    SelectedPdfCard(
                                        metadata = meta,
                                        onChangePdf = { pdfPickerLauncher.launch("application/pdf") }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(18.dp)) {
                                            Text(
                                                text = "Split / Extract Pages (1 to ${meta.pageCount})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Enter page ranges or comma-separated page numbers (e.g. 1-3, 5).",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            OutlinedTextField(
                                                value = pageRangeInput,
                                                onValueChange = { viewModel.setPageRangeInput(it) },
                                                label = { Text("Pages to Extract") },
                                                placeholder = { Text("e.g. 1-2, 4") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("pdf_page_range_input")
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Button(
                                                onClick = { viewModel.runSplitOrExtractPages() },
                                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Extract Selected Pages", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    PdfUtilityMode.REORDER_ROTATE -> {
                        item {
                            if (selectedUri == null) {
                                PickPdfPlaceholderCard(onPickPdf = { pdfPickerLauncher.launch("application/pdf") })
                            } else {
                                metadata?.let { meta ->
                                    SelectedPdfCard(
                                        metadata = meta,
                                        onChangePdf = { pdfPickerLauncher.launch("application/pdf") }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(18.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Reorder & Rotate Pages",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                TextButton(onClick = { viewModel.rotateAllPages90() }) {
                                                    Icon(
                                                        imageVector = Icons.Default.RotateRight,
                                                        contentDescription = "Rotate all pages",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Rotate All +90°")
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))

                                            pageSequence.forEachIndexed { pos, srcPageIdx ->
                                                val rot = pageRotations[srcPageIdx] ?: 0
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Page ${srcPageIdx + 1}",
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        if (rot != 0) {
                                                            Text(
                                                                text = "${rot}°",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.padding(end = 8.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { viewModel.movePageInSequence(pos, -1) },
                                                            enabled = pos > 0,
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowUpward,
                                                                contentDescription = "Move Up",
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { viewModel.movePageInSequence(pos, 1) },
                                                            enabled = pos < pageSequence.lastIndex,
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowDownward,
                                                                contentDescription = "Move Down",
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { viewModel.rotatePageAt(srcPageIdx) },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.RotateRight,
                                                                contentDescription = "Rotate Page",
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))
                                            Button(
                                                onClick = { viewModel.runReorderAndRotatePages() },
                                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Apply Page Order & Rotation", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    PdfUtilityMode.IMAGES_TO_PDF -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Collections,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Convert Images into PDF",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Select one or more photos or document scans to bundle into a clean multi-page PDF.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedButton(
                                        onClick = { multiImageForPdfLauncher.launch("image/*") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            if (imagesForPdfUris.isEmpty()) "Select Images"
                                            else "Selected ${imagesForPdfUris.size} Image(s) (Tap to Change)"
                                        )
                                    }

                                    if (imagesForPdfUris.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { viewModel.runImagesToPdf() },
                                            modifier = Modifier.fillMaxWidth().height(50.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Create PDF (${imagesForPdfUris.size} Pages)", fontWeight = FontWeight.Bold)
                                        }
                                    }
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

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Custom PDF Preset", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Save Target: ${FormatUtils.formatBytes(targetSizeBytes)} • Profile: ${selectedProfile.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        label = { Text("Preset Name") },
                        placeholder = { Text("e.g. Scanned Certificate 500 KB") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (viewModel.saveCurrentAsCustomPreset(newPresetName)) {
                            newPresetName = ""
                            showSavePresetDialog = false
                            Toast.makeText(context, "PDF preset saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Enter a valid preset name.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PickPdfPlaceholderCard(onPickPdf: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pick_pdf_card")
            .clickable(onClick = onPickPdf),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "Pick PDF",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select a PDF Document",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Optimize PDF streams or downsample multi-page scanned forms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onPickPdf,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Browse PDF")
            }
        }
    }
}

@Composable
fun SelectedPdfCard(
    metadata: com.example.engine.PdfMetadata,
    onChangePdf: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${FormatUtils.formatBytes(metadata.sizeBytes)} • ${metadata.pageCount} pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            OutlinedButton(
                onClick = onChangePdf,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Change")
            }
        }
    }
}

@Composable
fun PdfCompressingCard(
    currentPage: Int,
    totalPages: Int,
    stage: String,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Processing PDF Document...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { (currentPage.toFloat() / totalPages.toFloat()).coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Step $currentPage of $totalPages",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun PdfResultCard(
    result: PdfCompressResult,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCompressAnother: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf_result_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (result.isTargetMet) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (result.isTargetMet) SuccessGreen else WarningAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (result.isTargetMet) "PDF Ready" else "Best Achievable Size",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (result.isTargetMet) SuccessGreen else WarningAmber
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = result.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Original",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = FormatUtils.formatBytes(result.originalMetadata.sizeBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${result.originalMetadata.pageCount} pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    SavingsBadge(
                        percentage = FormatUtils.calculateSavingsPercentage(
                            result.originalMetadata.sizeBytes,
                            result.outputBytes
                        )
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Output PDF",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = FormatUtils.formatBytes(result.outputBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${result.pageCount} pages (Verified)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_compressed_pdf_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save PDF to Device", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open")
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }

                OutlinedButton(
                    onClick = onCompressAnother,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("New")
                }
            }
        }
    }
}
