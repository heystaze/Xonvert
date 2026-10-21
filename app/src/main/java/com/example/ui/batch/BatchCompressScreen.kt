package com.example.ui.batch

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CustomSizeInput
import com.example.ui.components.PresetSizeRow
import com.example.ui.components.SavingsBadge
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchCompressScreen(
    viewModel: BatchCompressViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onNavigateBack() }

    val items by viewModel.items.collectAsStateWithLifecycle()
    val targetSizeBytes by viewModel.targetSizeBytes.collectAsStateWithLifecycle()
    val customSizeText by viewModel.customSizeText.collectAsStateWithLifecycle()
    val isMbUnit by viewModel.isMbUnit.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()

    val batchImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(uris)
        }
    }

    var pendingSaveItem by remember { mutableStateOf<BatchItem?>(null) }
    val saveItemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val item = pendingSaveItem
        if (destUri != null && item != null) {
            val ok = viewModel.saveBatchItemToUri(context, destUri, item)
            if (ok) {
                Toast.makeText(context, "Saved ${item.fileName}!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to save file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val batchPresets = remember {
        listOf(
            "50 KB" to 50 * 1024L,
            "100 KB" to 100 * 1024L,
            "200 KB" to 200 * 1024L,
            "500 KB" to 500 * 1024L,
            "1 MB" to 1024 * 1024L
        )
    }

    val hasFailedItems = items.any {
        it.status == BatchItemStatus.FAILED || it.status == BatchItemStatus.CANCELLED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Batch Compress",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("batch_compress_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (items.isNotEmpty() && !isProcessing) {
                        IconButton(onClick = { viewModel.clear() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear List"
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
            if (items.isEmpty()) {
                item {
                    PickBatchPlaceholderCard(
                        onPickImages = { batchImagePickerLauncher.launch("image/*") },
                        onPickPdfs = { batchImagePickerLauncher.launch("application/pdf") }
                    )
                }
            } else {
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
                                Text(
                                    text = "Queue: ${items.size} File(s)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!isProcessing) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { batchImagePickerLauncher.launch("image/*") },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Images")
                                        }
                                        OutlinedButton(
                                            onClick = { batchImagePickerLauncher.launch("application/pdf") },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("PDFs")
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Shared Target Maximum Size:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            PresetSizeRow(
                                presets = batchPresets,
                                selectedSizeBytes = targetSizeBytes,
                                onPresetSelected = { viewModel.setTargetSizePreset(it) }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            CustomSizeInput(
                                value = customSizeText,
                                onValueChange = { viewModel.setCustomSize(it, isMbUnit) },
                                isMb = isMbUnit,
                                onUnitToggle = { viewModel.setCustomSize(customSizeText, it) }
                            )

                            if (isProcessing) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Processing file ${currentIndex + 1} of ${items.size}...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        ((currentIndex + 1).toFloat() / items.size.toFloat()).coerceIn(0.05f, 1f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                item {
                    if (isProcessing) {
                        OutlinedButton(
                            onClick = { viewModel.cancelBatch() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("cancel_batch_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel Batch")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startBatch() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("start_batch_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compress All", fontWeight = FontWeight.Bold)
                            }

                            if (hasFailedItems) {
                                OutlinedButton(
                                    onClick = { viewModel.retryFailedItems() },
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("retry_failed_batch_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry Failed")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Failed")
                                }
                            }
                        }
                    }
                }

                itemsIndexed(items) { index, item ->
                    BatchItemRow(
                        item = item,
                        index = index,
                        isBatchRunning = isProcessing,
                        onRetry = { viewModel.retrySingleItem(index) },
                        onShare = {
                            val shareIntent = viewModel.shareBatchItem(context, item)
                            if (shareIntent != null) {
                                context.startActivity(shareIntent)
                            } else {
                                Toast.makeText(context, "Output file unavailable.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSave = {
                            pendingSaveItem = item
                            val ext = item.outputFile?.extension ?: if (item.isPdf) "pdf" else "jpg"
                            val base = item.fileName.substringBeforeLast(".")
                            saveItemLauncher.launch("${base}_xonvert.$ext")
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PickBatchPlaceholderCard(
    onPickImages: () -> Unit,
    onPickPdfs: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pick_batch_card")
            .clickable(onClick = onPickImages),
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
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = "Pick Multiple Files",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Batch Compress Multiple Files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Select multiple images or PDFs and compress them sequentially to a shared target limit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPickImages,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Select Images")
                }

                OutlinedButton(
                    onClick = onPickPdfs,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Select PDFs")
                }
            }
        }
    }
}

@Composable
fun BatchItemRow(
    item: BatchItem,
    index: Int,
    isBatchRunning: Boolean,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.width(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = FormatUtils.formatBytes(item.originalSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (item.outputSizeBytes != null) {
                        Text(
                            text = " → ${FormatUtils.formatBytes(item.outputSizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        SavingsBadge(
                            percentage = FormatUtils.calculateSavingsPercentage(
                                item.originalSizeBytes,
                                item.outputSizeBytes
                            )
                        )
                    }
                }
                when (item.status) {
                    BatchItemStatus.SUCCESS_MET -> {
                        Text(
                            text = "Target reached",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen
                        )
                    }
                    BatchItemStatus.SUCCESS_UNMET -> {
                        Text(
                            text = "Best achievable size (exceeds target)",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                    BatchItemStatus.FAILED, BatchItemStatus.CANCELLED -> {
                        Text(
                            text = item.errorMessage ?: "Failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (item.outputFile != null &&
                (item.status == BatchItemStatus.SUCCESS_MET || item.status == BatchItemStatus.SUCCESS_UNMET)
            ) {
                IconButton(onClick = onSave, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save ${item.fileName}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share ${item.fileName}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if ((item.status == BatchItemStatus.FAILED || item.status == BatchItemStatus.CANCELLED) && !isBatchRunning) {
                IconButton(onClick = onRetry, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry ${item.fileName}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            when (item.status) {
                BatchItemStatus.PENDING -> {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = "Pending",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
                BatchItemStatus.PROCESSING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                BatchItemStatus.SUCCESS_MET -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Target Met",
                        tint = SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                BatchItemStatus.SUCCESS_UNMET -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Target Exceeded",
                        tint = WarningAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
                BatchItemStatus.FAILED, BatchItemStatus.CANCELLED -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
