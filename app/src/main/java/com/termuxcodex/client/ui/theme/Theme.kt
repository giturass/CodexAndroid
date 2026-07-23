package com.termuxcodex.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.termuxcodex.client.AppThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = Color(0xFF0349B4),
    secondary = Color(0xFF57606A),
    secondaryContainer = Color(0xFFDDF4FF),
    tertiary = Color(0xFF1F6FEB),
    tertiaryContainer = Color(0xFFDDF4FF),
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFF6F8FA),
    surfaceVariant = Color(0xFFD0D7DE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF0D419D),
    onPrimaryContainer = Color(0xFFCAEAFF),
    secondary = Color(0xFF8C959F),
    secondaryContainer = Color(0xFF1F3D64),
    tertiary = Color(0xFF79C0FF),
    tertiaryContainer = Color(0xFF1F3D64),
    background = Color(0xFF0D1117),
    surface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFF30363D),
)

@Composable
fun TermuxCodexTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
