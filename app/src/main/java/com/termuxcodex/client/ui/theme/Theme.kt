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

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2D7),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4B635B),
    secondaryContainer = Color(0xFFCDE8DE),
    tertiary = Color(0xFF416276),
    tertiaryContainer = Color(0xFFC4E7FF),
    background = Color(0xFFF7FBF8),
    surface = Color(0xFFF7FBF8),
    surfaceVariant = Color(0xFFDBE5E0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5BC),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFF9EF2D7),
    secondary = Color(0xFFB1CCC2),
    secondaryContainer = Color(0xFF344B44),
    tertiary = Color(0xFFA8CBE2),
    tertiaryContainer = Color(0xFF284B5D),
    background = Color(0xFF0F1513),
    surface = Color(0xFF0F1513),
    surfaceVariant = Color(0xFF3F4945),
)

@Composable
fun TermuxCodexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
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
