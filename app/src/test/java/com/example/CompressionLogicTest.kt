package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CompressionRecord
import com.example.data.CustomPreset
import com.example.data.CustomPresetRepository
import com.example.data.DarkThemeStyle
import com.example.data.ThemeMode
import com.example.data.ThemePreferencesRepository
import com.example.ui.checker.RequirementSpec
import com.example.ui.checker.RequirementsValidator
import com.example.ui.home.FileActionOutcome
import com.example.ui.home.HomeViewModel
import com.example.util.FormatUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompressionLogicTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `test formatBytes output`() {
        assertEquals("0 B", FormatUtils.formatBytes(0))
        assertEquals("500 B", FormatUtils.formatBytes(500))
        assertEquals("100 KB", FormatUtils.formatBytes(100 * 1024))
        assertEquals("1 MB", FormatUtils.formatBytes(1024 * 1024))
        assertEquals("2.5 MB", FormatUtils.formatBytes((2.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `test parseTargetSize for KB and MB inputs`() {
        val kbResult = FormatUtils.parseTargetSize("100", isMb = false)
        assertNotNull(kbResult)
        assertEquals(102400L, kbResult)

        val mbResult = FormatUtils.parseTargetSize("2", isMb = true)
        assertNotNull(mbResult)
        assertEquals(2 * 1024 * 1024L, mbResult)

        val commaResult = FormatUtils.parseTargetSize("1,5", isMb = true)
        assertNotNull(commaResult)
        assertEquals((1.5 * 1024 * 1024).toLong(), commaResult)

        assertNull(FormatUtils.parseTargetSize("abc", isMb = false))
        assertNull(FormatUtils.parseTargetSize("0", isMb = false))
        assertNull(FormatUtils.parseTargetSize("-50", isMb = false))
        assertNull(FormatUtils.parseTargetSize("", isMb = false))
    }

    @Test
    fun `test calculateSavingsPercentage logic`() {
        assertEquals(80, FormatUtils.calculateSavingsPercentage(1000, 200))
        assertEquals(50, FormatUtils.calculateSavingsPercentage(2000, 1000))
        assertEquals(95, FormatUtils.calculateSavingsPercentage(10000, 500))

        assertEquals(0, FormatUtils.calculateSavingsPercentage(100, 150))
        assertEquals(0, FormatUtils.calculateSavingsPercentage(100, 100))
        assertEquals(0, FormatUtils.calculateSavingsPercentage(0, 50))
    }

    @Test
    fun `test CompressionRecord entity properties`() {
        val record = CompressionRecord(
            fileName = "passport_photo.jpg",
            fileType = "IMAGE",
            originalSizeBytes = 2 * 1024 * 1024L,
            compressedSizeBytes = 95 * 1024L,
            targetSizeBytes = 100 * 1024L,
            isTargetMet = true,
            outputFilePath = "/tmp/passport_photo_compressed.jpg",
            mimeType = "image/jpeg"
        )

        assertTrue(record.isTargetMet)
        assertTrue(record.savedBytes > 0)
        assertTrue(record.savingsPercentage >= 90)
        assertEquals("image/jpeg", record.mimeType)
    }

    @Test
    fun `test Room database insert and retrieval`() = runBlocking {
        val dao = database.compressionRecordDao()
        val record = CompressionRecord(
            fileName = "gov_form.pdf",
            fileType = "PDF",
            originalSizeBytes = 1024 * 1024L,
            compressedSizeBytes = 150 * 1024L,
            targetSizeBytes = 200 * 1024L,
            isTargetMet = true,
            outputFilePath = "/tmp/gov_form_compressed.pdf",
            mimeType = "application/pdf"
        )

        val id = dao.insertRecord(record)
        assertTrue(id > 0)

        val list = dao.getAllRecords().first()
        assertEquals(1, list.size)
        assertEquals("gov_form.pdf", list[0].fileName)
        assertTrue(list[0].isTargetMet)

        dao.deleteById(id)
        val afterDelete = dao.getAllRecords().first()
        assertEquals(0, afterDelete.size)
    }

    @Test
    fun `test ThemePreferencesRepository modes and dark styles`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ThemePreferencesRepository.getInstance(context)

        // Select Dark mode + Dracula style
        repo.setThemeMode(ThemeMode.DARK)
        repo.setDarkThemeStyle(DarkThemeStyle.DRACULA)
        assertEquals(ThemeMode.DARK, repo.appearanceSettings.value.themeMode)
        assertEquals(DarkThemeStyle.DRACULA, repo.appearanceSettings.value.darkThemeStyle)

        // Switch to Lavender
        repo.setDarkThemeStyle(DarkThemeStyle.LAVENDER)
        assertEquals(DarkThemeStyle.LAVENDER, repo.appearanceSettings.value.darkThemeStyle)

        // Switching to Light mode preserves the selected dark style
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.appearanceSettings.value.themeMode)
        assertEquals(DarkThemeStyle.LAVENDER, repo.appearanceSettings.value.darkThemeStyle)

        // Switching to System default preserves dark style
        repo.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, repo.appearanceSettings.value.themeMode)
        assertEquals(DarkThemeStyle.LAVENDER, repo.appearanceSettings.value.darkThemeStyle)
    }

    @Test
    fun `test CustomPresetRepository save edit delete and reset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = CustomPresetRepository.getInstance(context)
        repo.resetDefaults()

        val initialCount = repo.presets.value.size
        assertTrue(initialCount >= 4)

        // Reject invalid preset
        assertFalse(
            repo.savePreset(
                CustomPreset(
                    id = "bad",
                    name = "   ",
                    targetType = "IMAGE",
                    targetSizeBytes = 100 * 1024L
                )
            )
        )

        // Save valid custom preset
        val saved = repo.savePreset(
            CustomPreset(
                id = "custom_signature",
                name = "Portal Signature",
                targetType = "IMAGE",
                targetSizeBytes = 50 * 1024L,
                imageFormat = "JPEG"
            )
        )
        assertTrue(saved)
        assertTrue(repo.presets.value.any { it.id == "custom_signature" && it.name == "Portal Signature" })

        // Delete custom preset
        repo.deletePreset("custom_signature")
        assertFalse(repo.presets.value.any { it.id == "custom_signature" })
    }

    @Test
    fun `test Recent Compressions missing file and valid file share actions`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = HomeViewModel(app)

        val missingRecord = CompressionRecord(
            id = 10L,
            fileName = "missing_doc.pdf",
            fileType = "PDF",
            originalSizeBytes = 500_000L,
            compressedSizeBytes = 120_000L,
            targetSizeBytes = 150_000L,
            isTargetMet = true,
            outputFilePath = "/non/existent/path/missing_doc.pdf",
            mimeType = "application/pdf"
        )

        assertFalse(vm.isRecordFileAccessible(missingRecord))
        val missingShare = vm.buildShareAction(app, missingRecord)
        assertTrue(missingShare is FileActionOutcome.MissingFile)

        // Create a real file in cacheDir to verify FileProvider Share and Open intents
        val validFile = File(app.cacheDir, "test_compressed.jpg").apply {
            writeBytes(ByteArray(2048) { 0x42 })
        }
        val validRecord = CompressionRecord(
            id = 11L,
            fileName = "test_compressed.jpg",
            fileType = "IMAGE",
            originalSizeBytes = 10_000L,
            compressedSizeBytes = 2048L,
            targetSizeBytes = 5000L,
            isTargetMet = true,
            outputFilePath = validFile.absolutePath,
            mimeType = "image/jpeg"
        )

        assertTrue(vm.isRecordFileAccessible(validRecord))
        val shareOutcome = vm.buildShareAction(app, validRecord)
        assertTrue(shareOutcome is FileActionOutcome.ReadyIntent)

        val openOutcome = vm.buildOpenAction(app, validRecord)
        assertTrue(openOutcome is FileActionOutcome.ReadyIntent)

        val compressAgainOutcome = vm.buildCompressAgainAction(app, validRecord)
        assertTrue(compressAgainOutcome is FileActionOutcome.ReadyUri)

        validFile.delete()
    }

    @Test
    fun `test RequirementsValidator evaluation`() {
        val spec = RequirementSpec(
            maxSizeBytes = 100 * 1024L,
            acceptedFormats = setOf("JPG", "PNG"),
            requiredWidth = 600,
            requiredHeight = 600
        )

        val passChecks = RequirementsValidator.evaluateFile(
            actualSizeBytes = 85 * 1024L,
            detectedFormat = "JPEG",
            actualWidth = 600,
            actualHeight = 600,
            spec = spec
        )
        assertTrue(passChecks.all { it.passed })

        val failChecks = RequirementsValidator.evaluateFile(
            actualSizeBytes = 250 * 1024L,
            detectedFormat = "PDF",
            actualWidth = null,
            actualHeight = null,
            spec = spec
        )
        assertFalse(failChecks.all { it.passed })
    }

    @Test
    fun `test dedicated XonvertScreen navigation destinations exist without redundancy`() {
        val destinations = XonvertScreen.entries.map { it.name }.toSet()
        assertTrue(destinations.contains("HOME"))
        assertTrue(destinations.contains("COMPRESS_IMAGE"))
        assertTrue(destinations.contains("IMAGE_RESIZE_CONVERT"))
        assertTrue(destinations.contains("COMPRESS_PDF"))
        assertTrue(destinations.contains("PDF_UTILITIES"))
        assertTrue(destinations.contains("BATCH_COMPRESS"))
        assertTrue(destinations.contains("REQUIREMENTS_CHECKER"))
        assertTrue(destinations.contains("SETTINGS"))
    }
}
