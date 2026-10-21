package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val displayName: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class DarkThemeStyle(val displayName: String, val subtitle: String) {
    DRACULA("Dracula", "Deep purple-black with purple, pink & cyan accents"),
    GREEN_APPLE("Green Apple", "Dark charcoal with fresh green & lime accents"),
    LAVENDER("Lavender", "Dark violet surfaces with soft lavender accents"),
    DYNAMIC("Dynamic", "Wallpaper-derived Material You colors with fallback")
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val darkThemeStyle: DarkThemeStyle = DarkThemeStyle.GREEN_APPLE
)

class ThemePreferencesRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _appearanceSettings = MutableStateFlow(loadSettings())
    val appearanceSettings: StateFlow<AppearanceSettings> = _appearanceSettings.asStateFlow()

    private fun loadSettings(): AppearanceSettings {
        val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val styleName = prefs.getString(KEY_DARK_STYLE, DarkThemeStyle.GREEN_APPLE.name) ?: DarkThemeStyle.GREEN_APPLE.name

        val mode = runCatching { ThemeMode.valueOf(modeName) }.getOrDefault(ThemeMode.SYSTEM)
        val style = runCatching { DarkThemeStyle.valueOf(styleName) }.getOrDefault(DarkThemeStyle.GREEN_APPLE)

        return AppearanceSettings(themeMode = mode, darkThemeStyle = style)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _appearanceSettings.value = _appearanceSettings.value.copy(themeMode = mode)
    }

    fun setDarkThemeStyle(style: DarkThemeStyle) {
        prefs.edit().putString(KEY_DARK_STYLE, style.name).apply()
        _appearanceSettings.value = _appearanceSettings.value.copy(darkThemeStyle = style)
    }

    companion object {
        private const val PREFS_NAME = "xonvert_appearance_prefs"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_DARK_STYLE = "key_dark_style"

        @Volatile
        private var INSTANCE: ThemePreferencesRepository? = null

        fun getInstance(context: Context): ThemePreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemePreferencesRepository(context).also { INSTANCE = it }
            }
        }
    }
}
