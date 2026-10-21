package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppearanceSettings
import com.example.data.CustomPreset
import com.example.data.DarkThemeStyle
import com.example.data.ThemeMode
import com.example.ui.theme.DraculaBg
import com.example.ui.theme.DraculaPink
import com.example.ui.theme.DraculaPurple
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GreenAppleBg
import com.example.ui.theme.GreenAppleLime
import com.example.ui.theme.GreenApplePrimary
import com.example.ui.theme.LavenderBg
import com.example.ui.theme.LavenderPrimary
import com.example.ui.theme.LavenderSecondary
import com.example.ui.theme.Slate900
import com.example.ui.theme.Teal300
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appearanceSettings: AppearanceSettings,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDarkStyleChanged: (DarkThemeStyle) -> Unit,
    customPresets: List<CustomPreset>,
    onSavePreset: (CustomPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onResetPresets: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    var editingPreset by remember { mutableStateOf<CustomPreset?>(null) }
    var showPresetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
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
                .padding(innerPadding)
                .testTag("settings_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PART 2: APPEARANCE SECTION
            item {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("appearance_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Theme Mode",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Theme Mode Options: System default, Light, Dark
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.values().forEach { mode ->
                                val selected = appearanceSettings.themeMode == mode
                                val icon = when (mode) {
                                    ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                                    ThemeMode.LIGHT -> Icons.Default.LightMode
                                    ThemeMode.DARK -> Icons.Default.DarkMode
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = { onThemeModeChanged(mode) },
                                    label = { Text(mode.displayName) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}"),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        // Dark Theme Styles when Dark mode is selected
                        AnimatedVisibility(visible = appearanceSettings.themeMode == ThemeMode.DARK) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "Dark Theme Style",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Select your preferred dark color palette",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                DarkThemeStyle.values().forEach { style ->
                                    val isSelected = appearanceSettings.darkThemeStyle == style
                                    DarkStyleOptionRow(
                                        style = style,
                                        isSelected = isSelected,
                                        onSelect = { onDarkStyleChanged(style) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // PART 7: CUSTOM COMPRESSION PRESETS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Compression Presets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row {
                        TextButton(
                            onClick = onResetPresets,
                            modifier = Modifier.testTag("reset_presets_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Default Presets",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = {
                                editingPreset = null
                                showPresetDialog = true
                            },
                            modifier = Modifier.testTag("add_preset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Preset",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (customPresets.isEmpty()) {
                            Text(
                                text = "No custom presets saved. Tap Add to create reusable targets.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            customPresets.forEachIndexed { idx, preset ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkBorder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${preset.name} — ${FormatUtils.formatBytes(preset.targetSizeBytes)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (preset.targetType == "PDF") {
                                                "PDF • Profile: ${preset.pdfProfile}"
                                            } else {
                                                "Image • Format: ${preset.imageFormat} • Resize: ${preset.resizePreset}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            editingPreset = preset
                                            showPresetDialog = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit ${preset.name}",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeletePreset(preset.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete ${preset.name}",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (idx < customPresets.lastIndex) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }

            // About & Open Source Section (Retained per Part 1 & Part 13)
            item {
                Text(
                    text = "About & Open Source",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Xonvert v1.0",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Licensed under the Apache License, Version 2.0. Free and open-source software for offline image and PDF file compression and document utilities.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Open-Source Libraries: Jetpack Compose Material 3, AndroidX Room, Kotlin Coroutines, Coil (all Apache-2.0).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Target: Android 16 (API 36) • Min: Android 7.0 (API 24)",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showPresetDialog) {
        PresetEditorDialog(
            initialPreset = editingPreset,
            onDismiss = { showPresetDialog = false },
            onSave = { preset ->
                onSavePreset(preset)
                showPresetDialog = false
            }
        )
    }
}

@Composable
private fun DarkStyleOptionRow(
    style: DarkThemeStyle,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val swatches = when (style) {
        DarkThemeStyle.DRACULA -> listOf(DraculaBg, DraculaPurple, DraculaPink)
        DarkThemeStyle.GREEN_APPLE -> listOf(GreenAppleBg, GreenApplePrimary, GreenAppleLime)
        DarkThemeStyle.LAVENDER -> listOf(LavenderBg, LavenderPrimary, LavenderSecondary)
        DarkThemeStyle.DYNAMIC -> listOf(Slate900, Emerald400, Teal300)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .testTag("dark_style_${style.name.lowercase()}"),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = style.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                swatches.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun PresetEditorDialog(
    initialPreset: CustomPreset?,
    onDismiss: () -> Unit,
    onSave: (CustomPreset) -> Unit
) {
    var name by remember { mutableStateOf(initialPreset?.name ?: "") }
    var targetType by remember { mutableStateOf(initialPreset?.targetType ?: "IMAGE") }
    var sizeKbText by remember {
        mutableStateOf(
            if (initialPreset != null) (initialPreset.targetSizeBytes / 1024).toString() else "100"
        )
    }
    var imageFormat by remember { mutableStateOf(initialPreset?.imageFormat ?: "AUTO") }
    var pdfProfile by remember { mutableStateOf(initialPreset?.pdfProfile ?: "BALANCED") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPreset == null) "New Custom Preset" else "Edit Preset",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text("Preset Name (e.g. Passport Photo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = targetType == "IMAGE",
                        onClick = { targetType = "IMAGE" },
                        label = { Text("Image") }
                    )
                    FilterChip(
                        selected = targetType == "PDF",
                        onClick = { targetType = "PDF" },
                        label = { Text("PDF") }
                    )
                }

                OutlinedTextField(
                    value = sizeKbText,
                    onValueChange = {
                        sizeKbText = it
                        errorText = null
                    },
                    label = { Text("Target Max Size (KB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (targetType == "IMAGE") {
                    Text(
                        text = "Output Format",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("AUTO", "JPEG", "WEBP", "PNG").forEach { fmt ->
                            FilterChip(
                                selected = imageFormat == fmt,
                                onClick = { imageFormat = fmt },
                                label = { Text(fmt) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "PDF Profile",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("LIGHT", "BALANCED", "STRONG").forEach { prof ->
                            FilterChip(
                                selected = pdfProfile == prof,
                                onClick = { pdfProfile = prof },
                                label = { Text(prof) }
                            )
                        }
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val kb = sizeKbText.trim().toLongOrNull()
                    if (name.isBlank()) {
                        errorText = "Please enter a preset name."
                    } else if (kb == null || kb <= 0L) {
                        errorText = "Please enter a valid size in KB (> 0)."
                    } else {
                        onSave(
                            CustomPreset(
                                id = initialPreset?.id ?: "custom_${System.currentTimeMillis()}",
                                name = name.trim(),
                                targetType = targetType,
                                targetSizeBytes = kb * 1024L,
                                imageFormat = imageFormat,
                                resizePreset = initialPreset?.resizePreset ?: "ORIGINAL",
                                pdfProfile = pdfProfile
                            )
                        )
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
