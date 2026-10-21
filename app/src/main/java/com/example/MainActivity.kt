package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CustomPresetRepository
import com.example.data.ThemePreferencesRepository
import com.example.ui.batch.BatchCompressScreen
import com.example.ui.batch.BatchCompressViewModel
import com.example.ui.checker.RequirementsCheckerScreen
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.image.ImageCompressScreen
import com.example.ui.image.ImageCompressViewModel
import com.example.ui.image.ImageResizeConvertScreen
import com.example.ui.image.ImageToolTab
import com.example.ui.pdf.PdfCompressScreen
import com.example.ui.pdf.PdfCompressViewModel
import com.example.ui.pdf.PdfToolTab
import com.example.ui.pdf.PdfUtilitiesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.XonvertTheme

enum class XonvertScreen {
    HOME,
    COMPRESS_IMAGE,
    IMAGE_RESIZE_CONVERT,
    COMPRESS_PDF,
    PDF_UTILITIES,
    BATCH_COMPRESS,
    REQUIREMENTS_CHECKER,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themeRepository = remember { ThemePreferencesRepository.getInstance(context) }
            val appearanceSettings by themeRepository.appearanceSettings.collectAsStateWithLifecycle()

            XonvertTheme(appearanceSettings = appearanceSettings) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 680.dp)
                        ) {
                            XonvertApp(themeRepository = themeRepository)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun XonvertApp(
    themeRepository: ThemePreferencesRepository = ThemePreferencesRepository.getInstance(LocalContext.current)
) {
    val context = LocalContext.current
    var currentScreen by rememberSaveable { mutableStateOf(XonvertScreen.HOME) }

    val appearanceSettings by themeRepository.appearanceSettings.collectAsStateWithLifecycle()
    val presetRepository = remember { CustomPresetRepository.getInstance(context) }
    val customPresets by presetRepository.presets.collectAsStateWithLifecycle()

    val homeViewModel: HomeViewModel = viewModel()
    val imageViewModel: ImageCompressViewModel = viewModel()
    val pdfViewModel: PdfCompressViewModel = viewModel()
    val batchViewModel: BatchCompressViewModel = viewModel()

    when (currentScreen) {
        XonvertScreen.HOME -> {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToImageCompress = {
                    imageViewModel.setActiveTab(ImageToolTab.COMPRESS)
                    currentScreen = XonvertScreen.COMPRESS_IMAGE
                },
                onNavigateToImageResizeConvert = {
                    imageViewModel.setActiveTab(ImageToolTab.RESIZE_CONVERT)
                    currentScreen = XonvertScreen.IMAGE_RESIZE_CONVERT
                },
                onNavigateToPdfCompress = {
                    pdfViewModel.setActiveTab(PdfToolTab.COMPRESS)
                    currentScreen = XonvertScreen.COMPRESS_PDF
                },
                onNavigateToPdfUtilities = {
                    pdfViewModel.setActiveTab(PdfToolTab.UTILITIES)
                    currentScreen = XonvertScreen.PDF_UTILITIES
                },
                onNavigateToBatchCompress = {
                    currentScreen = XonvertScreen.BATCH_COMPRESS
                },
                onNavigateToRequirementsChecker = {
                    currentScreen = XonvertScreen.REQUIREMENTS_CHECKER
                },
                onCompressRecordAgain = { record, uri ->
                    if (record.fileType.equals("PDF", ignoreCase = true)) {
                        pdfViewModel.prefillForCompressAgain(uri, record.targetSizeBytes)
                        currentScreen = XonvertScreen.COMPRESS_PDF
                    } else {
                        imageViewModel.prefillForCompressAgain(uri, record.targetSizeBytes)
                        currentScreen = XonvertScreen.COMPRESS_IMAGE
                    }
                },
                onNavigateToSettings = {
                    currentScreen = XonvertScreen.SETTINGS
                }
            )
        }

        XonvertScreen.COMPRESS_IMAGE -> {
            ImageCompressScreen(
                viewModel = imageViewModel,
                onNavigateBack = { currentScreen = XonvertScreen.HOME }
            )
        }

        XonvertScreen.IMAGE_RESIZE_CONVERT -> {
            ImageResizeConvertScreen(
                viewModel = imageViewModel,
                onNavigateBack = { currentScreen = XonvertScreen.HOME }
            )
        }

        XonvertScreen.COMPRESS_PDF -> {
            PdfCompressScreen(
                viewModel = pdfViewModel,
                onNavigateBack = { currentScreen = XonvertScreen.HOME }
            )
        }

        XonvertScreen.PDF_UTILITIES -> {
            PdfUtilitiesScreen(
                viewModel = pdfViewModel,
                onNavigateBack = { currentScreen = XonvertScreen.HOME }
            )
        }

        XonvertScreen.BATCH_COMPRESS -> {
            BatchCompressScreen(
                viewModel = batchViewModel,
                onNavigateBack = { currentScreen = XonvertScreen.HOME }
            )
        }

        XonvertScreen.REQUIREMENTS_CHECKER -> {
            RequirementsCheckerScreen(
                onNavigateBack = { currentScreen = XonvertScreen.HOME },
                onOpenInCompressor = { uri, isPdf, targetBytes ->
                    if (isPdf) {
                        pdfViewModel.prefillForCompressAgain(uri, targetBytes)
                        currentScreen = XonvertScreen.COMPRESS_PDF
                    } else {
                        imageViewModel.prefillForCompressAgain(uri, targetBytes)
                        currentScreen = XonvertScreen.COMPRESS_IMAGE
                    }
                }
            )
        }

        XonvertScreen.SETTINGS -> {
            SettingsScreen(
                appearanceSettings = appearanceSettings,
                onThemeModeChanged = { themeRepository.setThemeMode(it) },
                onDarkStyleChanged = { themeRepository.setDarkThemeStyle(it) },
                customPresets = customPresets,
                onSavePreset = { presetRepository.savePreset(it) },
                onDeletePreset = { presetRepository.deletePreset(it) },
                onResetPresets = { presetRepository.resetDefaults() },
                onNavigateBack = { currentScreen = XonvertScreen.HOME }
            )
        }
    }
}
