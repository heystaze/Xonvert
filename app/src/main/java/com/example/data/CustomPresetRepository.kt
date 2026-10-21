package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class CustomPreset(
    val id: String,
    val name: String,
    val targetType: String, // "IMAGE" or "PDF"
    val targetSizeBytes: Long,
    val imageFormat: String = "AUTO", // AUTO, JPEG, WEBP, PNG
    val resizePreset: String = "ORIGINAL", // ORIGINAL, SCALE_1920, SCALE_1280, SCALE_800
    val pdfProfile: String = "BALANCED" // LIGHT, BALANCED, STRONG
)

class CustomPresetRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _presets = MutableStateFlow<List<CustomPreset>>(loadPresets())
    val presets: StateFlow<List<CustomPreset>> = _presets.asStateFlow()

    private fun loadPresets(): List<CustomPreset> {
        val raw = prefs.getString(KEY_PRESETS_JSON, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultPresets()
            saveToPrefs(defaults)
            return defaults
        }
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<CustomPreset>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val targetBytes = obj.optLong("targetSizeBytes", 100 * 1024L)
                val name = obj.optString("name", "").trim()
                if (name.isNotEmpty() && targetBytes > 0) {
                    result.add(
                        CustomPreset(
                            id = obj.optString("id", "preset_$i"),
                            name = name,
                            targetType = obj.optString("targetType", "IMAGE"),
                            targetSizeBytes = targetBytes,
                            imageFormat = obj.optString("imageFormat", "AUTO"),
                            resizePreset = obj.optString("resizePreset", "ORIGINAL"),
                            pdfProfile = obj.optString("pdfProfile", "BALANCED")
                        )
                    )
                }
            }
            result
        } catch (_: Exception) {
            defaultPresets()
        }
    }

    fun savePreset(preset: CustomPreset): Boolean {
        val cleanName = preset.name.trim()
        if (cleanName.isEmpty() || preset.targetSizeBytes <= 0L) return false

        val validPreset = preset.copy(name = cleanName)
        val current = _presets.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == validPreset.id }
        if (existingIdx >= 0) {
            current[existingIdx] = validPreset
        } else {
            current.add(validPreset)
        }
        saveToPrefs(current)
        _presets.value = current
        return true
    }

    fun deletePreset(id: String) {
        val current = _presets.value.filterNot { it.id == id }
        saveToPrefs(current)
        _presets.value = current
    }

    fun resetDefaults() {
        val defaults = defaultPresets()
        saveToPrefs(defaults)
        _presets.value = defaults
    }

    private fun saveToPrefs(list: List<CustomPreset>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("targetType", item.targetType)
                put("targetSizeBytes", item.targetSizeBytes)
                put("imageFormat", item.imageFormat)
                put("resizePreset", item.resizePreset)
                put("pdfProfile", item.pdfProfile)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PRESETS_JSON, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "xonvert_custom_presets"
        private const val KEY_PRESETS_JSON = "key_presets_json"

        fun defaultPresets(): List<CustomPreset> = listOf(
            CustomPreset(
                id = "default_passport_photo",
                name = "Passport Photo",
                targetType = "IMAGE",
                targetSizeBytes = 100 * 1024L,
                imageFormat = "JPEG",
                resizePreset = "SCALE_800"
            ),
            CustomPreset(
                id = "default_application_image",
                name = "Application Image",
                targetType = "IMAGE",
                targetSizeBytes = 200 * 1024L,
                imageFormat = "AUTO",
                resizePreset = "SCALE_1280"
            ),
            CustomPreset(
                id = "default_small_pdf",
                name = "Small PDF",
                targetType = "PDF",
                targetSizeBytes = 500 * 1024L,
                pdfProfile = "BALANCED"
            ),
            CustomPreset(
                id = "default_document_pdf",
                name = "Document PDF",
                targetType = "PDF",
                targetSizeBytes = 1024 * 1024L,
                pdfProfile = "LIGHT"
            )
        )

        @Volatile
        private var INSTANCE: CustomPresetRepository? = null

        fun getInstance(context: Context): CustomPresetRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CustomPresetRepository(context).also { INSTANCE = it }
            }
        }
    }
}
