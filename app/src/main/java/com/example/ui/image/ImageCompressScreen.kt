package com.example.ui.image

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.engine.ImageCompressResult
import com.example.engine.ImageOutputFormat
import com.example.engine.ImageResizeMode
import com.example.engine.ResizePreset
import com.example.ui.components.CustomSizeInput
import com.example.ui.components.PresetSizeRow
import com.example.ui.components.ResultSecondaryActionsRow
import com.example.ui.components.SavingsBadge
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.util.FormatUtils

/**
 * Dedicated screen for Target Size Image Compression (opened from "Compress Image" on Home).
 * No redundant tab switcher is shown.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageCompressScreen(
    viewModel: ImageCompressViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onNavigateBack() }

    val customPresets by viewModel.customImagePresets.collectAsStateWithLifecycle()
    val selectedUri by viewModel.selectedUri.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val targetSizeBytes by viewModel.targetSizeBytes.collectAsStateWithLifecycle()
    val customSizeText by viewModel.customSizeText.collectAsStateWithLifecycle()
    val isMbUnit by viewModel.isMbUnit.collectAsStateWithLifecycle()
    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
    val resizePreset by viewModel.resizePreset.collectAsStateWithLifecycle()
    val preserveAspectRatio by viewModel.preserveAspectRatio.collectAsStateWithLifecycle()
    val allowDimensionDownscaling by viewModel.allowDimensionDownscaling.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.onImageSelected(uri)
        }
    }

    var pendingSaveResult by remember { mutableStateOf<ImageCompressResult?>(null) }
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*")
    ) { destUri ->
        val res = pendingSaveResult
        if (destUri != null && res != null) {
            val ok = viewModel.saveOutputFileToUri(context, destUri, res)
            if (ok) {
                Toast.makeText(context, "Saved image to selected location!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to save image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imagePresets = remember {
        listOf(
            "20 KB" to 20 * 1024L,
            "50 KB" to 50 * 1024L,
            "100 KB" to 100 * 1024L,
            "200 KB" to 200 * 1024L,
            "500 KB" to 500 * 1024L,
            "1 MB" to 1024 * 1024L
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Compress Image",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("image_compress_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                    }
                },
                actions = {
                    if (selectedUri != null && uiState !is ImageUiState.Compressing) {
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
                .padding(innerPadding)
                .testTag("image_compress_content"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState is ImageUiState.Compressing) {
                val state = uiState as ImageUiState.Compressing
                item {
                    CompressingCard(
                        step = state.step,
                        iteration = state.iteration,
                        maxIterations = state.maxIterations,
                        onCancel = { viewModel.cancelCompression() }
                    )
                }
            } else if (uiState is ImageUiState.Finished) {
                val state = uiState as ImageUiState.Finished
                item {
                    ImageResultCard(
                        result = state.result,
                        onOpen = {
                            runCatching {
                                val openIntent = viewModel.createOpenIntent(context, state.result)
                                context.startActivity(Intent.createChooser(openIntent, "Open Output Image"))
                            }.onFailure {
                                Toast.makeText(context, "No viewer app found for this image.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSave = {
                            pendingSaveResult = state.result
                            val ext = state.result.finalFormat.extension.ifEmpty { "jpg" }
                            val base = state.result.originalMetadata.fileName.substringBeforeLast(".")
                            saveFileLauncher.launch("${base}_xonvert.$ext")
                        },
                        onShare = {
                            val intent = viewModel.createShareIntent(context, state.result)
                            context.startActivity(Intent.createChooser(intent, "Share Image"))
                        },
                        onCompressAnother = { viewModel.clearSelection() }
                    )
                }
            } else if (uiState is ImageUiState.Error) {
                val state = uiState as ImageUiState.Error
                item {
                    ImageErrorCard(
                        message = state.message,
                        onReset = { viewModel.clearSelection() }
                    )
                }
            }

            if (selectedUri == null) {
                item {
                    PickImagePlaceholderCard(
                        title = "Select an Image to Compress",
                        subtitle = "Supports JPEG, PNG, WebP • Target exact KB limits",
                        onPickImage = { imagePickerLauncher.launch("image/*") }
                    )
                }
            } else if (uiState !is ImageUiState.Finished && uiState !is ImageUiState.Compressing) {
                item {
                    metadata?.let { meta ->
                        SelectedImagePreviewCard(
                            uri = selectedUri!!,
                            metadata = meta,
                            onChangeImage = { imagePickerLauncher.launch("image/*") }
                        )
                    }
                }

                // Saved Custom Presets Card
                if (customPresets.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BookmarkBorder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Saved Presets",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { showSavePresetDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BookmarkAdd,
                                            contentDescription = "Save Current Preset",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Save Current",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    customPresets.forEach { preset ->
                                        FilterChip(
                                            selected = targetSizeBytes == preset.targetSizeBytes,
                                            onClick = { viewModel.applyCustomPreset(preset) },
                                            label = {
                                                Text(
                                                    text = "${preset.name} (${FormatUtils.formatBytes(preset.targetSizeBytes)})",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Target Size Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PhotoSizeSelectLarge,
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
                                presets = imagePresets,
                                selectedSizeBytes = targetSizeBytes,
                                onPresetSelected = { viewModel.setTargetSizePreset(it) }
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

                // Advanced Resize & Format Controls
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
                                    text = "Resolution & Format Controls",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Resolution Ceiling",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    ResizePreset.ORIGINAL to "Original",
                                    ResizePreset.SCALE_1920 to "1920p",
                                    ResizePreset.SCALE_1280 to "1280p",
                                    ResizePreset.SCALE_800 to "800p"
                                ).forEach { (preset, label) ->
                                    FilterChip(
                                        selected = resizePreset == preset,
                                        onClick = { viewModel.setResizePreset(preset) },
                                        label = { Text(label, maxLines = 1, softWrap = false) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Preserve Aspect Ratio",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Prevents distortion when resizing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Switch(
                                    checked = preserveAspectRatio,
                                    onCheckedChange = { viewModel.setPreserveAspectRatio(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Adaptive Downscaling",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Reduce resolution if quality alone cannot reach target",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Switch(
                                    checked = allowDimensionDownscaling,
                                    onCheckedChange = { viewModel.setAllowDimensionDownscaling(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Output Format",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    ImageOutputFormat.AUTO to "Auto",
                                    ImageOutputFormat.WEBP to "WebP",
                                    ImageOutputFormat.JPEG to "JPEG",
                                    ImageOutputFormat.PNG to "PNG"
                                ).forEach { (fmt, label) ->
                                    FilterChip(
                                        selected = selectedFormat == fmt,
                                        onClick = { viewModel.setFormat(fmt) },
                                        label = { Text(label, maxLines = 1, softWrap = false) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
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
                            .heightIn(min = 54.dp)
                            .testTag("start_compress_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "Compress to ${FormatUtils.formatBytes(targetSizeBytes)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
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
            title = { Text("Save Custom Image Preset", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Save Target: ${FormatUtils.formatBytes(targetSizeBytes)} • Format: ${selectedFormat.name} • Resize: ${resizePreset.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        label = { Text("Preset Name") },
                        placeholder = { Text("e.g. Exam Portal Photo") },
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
                            Toast.makeText(context, "Preset saved!", Toast.LENGTH_SHORT).show()
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

/**
 * Dedicated screen for Image Resize, Rotation & Format Conversion
 * (opened directly from "Resize & Convert" on the Home screen, without redundant tabs).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageResizeConvertScreen(
    viewModel: ImageCompressViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onNavigateBack() }

    val selectedUri by viewModel.selectedUri.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val preserveAspectRatio by viewModel.preserveAspectRatio.collectAsStateWithLifecycle()
    val resizeMode by viewModel.resizeMode.collectAsStateWithLifecycle()
    val scalePercentage by viewModel.scalePercentage.collectAsStateWithLifecycle()
    val customWidthText by viewModel.customWidthText.collectAsStateWithLifecycle()
    val customHeightText by viewModel.customHeightText.collectAsStateWithLifecycle()
    val rotationDegrees by viewModel.rotationDegrees.collectAsStateWithLifecycle()
    val convertFormat by viewModel.convertFormat.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.onImageSelected(uri)
        }
    }

    var pendingSaveResult by remember { mutableStateOf<ImageCompressResult?>(null) }
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*")
    ) { destUri ->
        val res = pendingSaveResult
        if (destUri != null && res != null) {
            val ok = viewModel.saveOutputFileToUri(context, destUri, res)
            if (ok) {
                Toast.makeText(context, "Saved transformed image!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to save image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Resize & Convert",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("image_resize_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                    }
                },
                actions = {
                    if (selectedUri != null && uiState !is ImageUiState.Compressing) {
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
                .padding(innerPadding)
                .testTag("image_resize_convert_content"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState is ImageUiState.Compressing) {
                val state = uiState as ImageUiState.Compressing
                item {
                    CompressingCard(
                        step = state.step,
                        iteration = state.iteration,
                        maxIterations = state.maxIterations,
                        onCancel = { viewModel.cancelCompression() }
                    )
                }
            } else if (uiState is ImageUiState.Finished) {
                val state = uiState as ImageUiState.Finished
                item {
                    ImageResultCard(
                        result = state.result,
                        onOpen = {
                            runCatching {
                                val openIntent = viewModel.createOpenIntent(context, state.result)
                                context.startActivity(Intent.createChooser(openIntent, "Open Output Image"))
                            }.onFailure {
                                Toast.makeText(context, "No viewer app found for this image.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSave = {
                            pendingSaveResult = state.result
                            val ext = state.result.finalFormat.extension.ifEmpty { "jpg" }
                            val base = state.result.originalMetadata.fileName.substringBeforeLast(".")
                            saveFileLauncher.launch("${base}_xonvert.$ext")
                        },
                        onShare = {
                            val intent = viewModel.createShareIntent(context, state.result)
                            context.startActivity(Intent.createChooser(intent, "Share Image"))
                        },
                        onCompressAnother = { viewModel.clearSelection() }
                    )
                }
            } else if (uiState is ImageUiState.Error) {
                val state = uiState as ImageUiState.Error
                item {
                    ImageErrorCard(
                        message = state.message,
                        onReset = { viewModel.clearSelection() }
                    )
                }
            }

            if (selectedUri == null) {
                item {
                    PickImagePlaceholderCard(
                        title = "Select an Image to Resize or Convert",
                        subtitle = "Scale by % or exact pixels, rotate, and convert between JPEG, PNG & WebP",
                        onPickImage = { imagePickerLauncher.launch("image/*") }
                    )
                }
            } else if (uiState !is ImageUiState.Finished && uiState !is ImageUiState.Compressing) {
                item {
                    metadata?.let { meta ->
                        SelectedImagePreviewCard(
                            uri = selectedUri!!,
                            metadata = meta,
                            onChangeImage = { imagePickerLauncher.launch("image/*") }
                        )
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
                                    imageVector = Icons.Default.CropRotate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Resize, Rotate & Format Conversion",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = resizeMode == ImageResizeMode.PERCENTAGE,
                                    onClick = { viewModel.setResizeMode(ImageResizeMode.PERCENTAGE) },
                                    label = { Text("By Percentage", maxLines = 1, softWrap = false) }
                                )
                                FilterChip(
                                    selected = resizeMode == ImageResizeMode.PIXELS,
                                    onClick = { viewModel.setResizeMode(ImageResizeMode.PIXELS) },
                                    label = { Text("By Pixels (W × H)", maxLines = 1, softWrap = false) }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (resizeMode == ImageResizeMode.PERCENTAGE) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(25, 50, 75, 100).forEach { pct ->
                                        FilterChip(
                                            selected = scalePercentage == pct,
                                            onClick = { viewModel.setScalePercentage(pct) },
                                            label = { Text("$pct%", maxLines = 1, softWrap = false) }
                                        )
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = customWidthText,
                                        onValueChange = { viewModel.setCustomWidthText(it) },
                                        label = { Text("Width (px)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = customHeightText,
                                        onValueChange = { viewModel.setCustomHeightText(it) },
                                        label = { Text("Height (px)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Lock Aspect Ratio",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                                    )
                                    Switch(
                                        checked = preserveAspectRatio,
                                        onCheckedChange = { viewModel.setPreserveAspectRatio(it) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Rotation Controls
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Rotation: ${rotationDegrees}°",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                OutlinedButton(
                                    onClick = { viewModel.rotate90Clockwise() },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("rotate_image_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                        contentDescription = "Rotate 90 degrees",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Rotate +90°", maxLines = 1, softWrap = false)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Convert Output Format",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    ImageOutputFormat.JPEG to "JPEG (.jpg)",
                                    ImageOutputFormat.PNG to "PNG (.png)",
                                    ImageOutputFormat.WEBP to "WebP (.webp)",
                                    ImageOutputFormat.AUTO to "Keep Original"
                                ).forEach { (fmt, label) ->
                                    FilterChip(
                                        selected = convertFormat == fmt,
                                        onClick = { viewModel.setConvertFormat(fmt) },
                                        label = { Text(label, maxLines = 1, softWrap = false) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.startResizeAndConvert() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .testTag("start_resize_convert_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "Apply Resize & Convert",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ImageErrorCard(
    message: String,
    onReset: () -> Unit
) {
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
                    text = "Operation Failed",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onReset,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Select Another Image")
            }
        }
    }
}

@Composable
fun PickImagePlaceholderCard(
    title: String = "Select an Image",
    subtitle: String = "Supports JPEG, PNG, WebP • Preserves EXIF & Transparency",
    onPickImage: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pick_image_card")
            .clickable(onClick = onPickImage),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Pick Image",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onPickImage,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Browse Image")
            }
        }
    }
}

@Composable
fun SelectedImagePreviewCard(
    uri: android.net.Uri,
    metadata: com.example.engine.ImageMetadata,
    onChangeImage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Selected image preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = metadata.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${FormatUtils.formatBytes(metadata.sizeBytes)} • ${metadata.width}×${metadata.height}px • ${if (metadata.hasAlpha) "Alpha" else "Opaque"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                OutlinedButton(
                    onClick = onChangeImage,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Change", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
fun CompressingCard(
    step: String,
    iteration: Int,
    maxIterations: Int,
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
                text = "Processing Image...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = step,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { (iteration.toFloat() / maxIterations.toFloat()).coerceIn(0.1f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary
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
fun ImageResultCard(
    result: ImageCompressResult,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCompressAnother: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("image_result_card"),
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
                    text = if (result.isTargetMet) "Output Ready" else "Best Achievable Size",
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

            Spacer(modifier = Modifier.height(14.dp))

            // Output Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = result.outputFile,
                    contentDescription = "Compressed output preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Original",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = FormatUtils.formatBytes(result.originalMetadata.sizeBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${result.originalMetadata.width}×${result.originalMetadata.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }

                    SavingsBadge(
                        percentage = FormatUtils.calculateSavingsPercentage(
                            result.originalMetadata.sizeBytes,
                            result.outputBytes
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Output",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = FormatUtils.formatBytes(result.outputBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text = "${result.outputWidth}×${result.outputHeight} (${result.finalFormat.extension.uppercase()})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("save_compressed_file_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save to Device", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }

            Spacer(modifier = Modifier.height(12.dp))

            ResultSecondaryActionsRow(
                onOpen = onOpen,
                onShare = onShare,
                onNew = onCompressAnother
            )
        }
    }
}
