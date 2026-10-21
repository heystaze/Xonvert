package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.AppearanceSettings
import com.example.data.DarkThemeStyle
import com.example.data.ThemeMode

val DraculaDarkScheme: ColorScheme = darkColorScheme(
    primary = DraculaPurple,
    onPrimary = Color(0xFF181226),
    primaryContainer = DraculaPurpleDark,
    onPrimaryContainer = Color(0xFFEDE3FF),
    secondary = DraculaPink,
    onSecondary = Color(0xFF280D1B),
    secondaryContainer = DraculaSurfaceVariant,
    onSecondaryContainer = DraculaCyan,
    tertiary = DraculaCyan,
    onTertiary = Color(0xFF0A2229),
    background = DraculaBg,
    onBackground = DraculaForeground,
    surface = DraculaSurface,
    onSurface = DraculaForeground,
    surfaceVariant = DraculaSurfaceVariant,
    onSurfaceVariant = DraculaSubtleText,
    outline = DraculaComment,
    error = ErrorRose
)

val GreenAppleDarkScheme: ColorScheme = darkColorScheme(
    primary = GreenApplePrimary,
    onPrimary = Color(0xFF052912),
    primaryContainer = GreenAppleContainer,
    onPrimaryContainer = GreenAppleOnContainer,
    secondary = GreenAppleLime,
    onSecondary = Color(0xFF1A2905),
    secondaryContainer = GreenAppleSurfaceVariant,
    onSecondaryContainer = GreenAppleLime,
    tertiary = Emerald300,
    onTertiary = Slate950,
    background = GreenAppleBg,
    onBackground = GreenAppleText,
    surface = GreenAppleSurface,
    onSurface = GreenAppleText,
    surfaceVariant = GreenAppleSurfaceVariant,
    onSurfaceVariant = GreenAppleSubtleText,
    outline = GreenAppleOutline,
    error = ErrorRose
)

val LavenderDarkScheme: ColorScheme = darkColorScheme(
    primary = LavenderPrimary,
    onPrimary = Color(0xFF1E143B),
    primaryContainer = LavenderContainer,
    onPrimaryContainer = LavenderOnContainer,
    secondary = LavenderSecondary,
    onSecondary = Color(0xFF1B1136),
    secondaryContainer = LavenderSurfaceVariant,
    onSecondaryContainer = LavenderSubtleText,
    tertiary = LavenderTertiary,
    onTertiary = Color(0xFF2E0B1D),
    background = LavenderBg,
    onBackground = LavenderText,
    surface = LavenderSurface,
    onSurface = LavenderText,
    surfaceVariant = LavenderSurfaceVariant,
    onSurfaceVariant = LavenderSubtleText,
    outline = LavenderOutline,
    error = ErrorRose
)

val FallbackDynamicDarkScheme: ColorScheme = darkColorScheme(
    primary = Emerald400,
    onPrimary = Slate950,
    primaryContainer = Emerald900,
    onPrimaryContainer = Emerald200,
    secondary = Teal300,
    onSecondary = Slate950,
    secondaryContainer = Slate800,
    onSecondaryContainer = Teal300,
    tertiary = Emerald300,
    background = Slate900,
    onBackground = Slate100,
    surface = Slate850,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    outline = Slate600,
    error = ErrorRose
)

val XonvertLightColorScheme: ColorScheme = lightColorScheme(
    primary = Emerald600,
    onPrimary = Color.White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald900,
    secondary = Teal700,
    onSecondary = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate800,
    tertiary = Emerald700,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outline = Slate300,
    error = ErrorRose
)

@Composable
fun XonvertTheme(
    appearanceSettings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (appearanceSettings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = if (useDarkTheme) {
        when (appearanceSettings.darkThemeStyle) {
            DarkThemeStyle.DRACULA -> DraculaDarkScheme
            DarkThemeStyle.GREEN_APPLE -> GreenAppleDarkScheme
            DarkThemeStyle.LAVENDER -> LavenderDarkScheme
            DarkThemeStyle.DYNAMIC -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicDarkColorScheme(context)
                } else {
                    FallbackDynamicDarkScheme
                }
            }
        }
    } else {
        if (appearanceSettings.darkThemeStyle == DarkThemeStyle.DYNAMIC &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            dynamicLightColorScheme(context)
        } else {
            XonvertLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = XonvertTheme(
    appearanceSettings = AppearanceSettings(
        themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
        darkThemeStyle = if (dynamicColor) DarkThemeStyle.DYNAMIC else DarkThemeStyle.GREEN_APPLE
    ),
    content = content
)
