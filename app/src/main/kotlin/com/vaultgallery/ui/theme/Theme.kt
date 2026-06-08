package com.vaultgallery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Modern Dark Palette - Deep Slate & Cyan Accents
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80DEEA),
    onPrimary = Color(0xFF00363C),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFFB2EBF2),
    secondary = Color(0xFFB0CCC0),
    onSecondary = Color(0xFF1B352C),
    secondaryContainer = Color(0xFF324B42),
    onSecondaryContainer = Color(0xFFCCE8DC),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBFC8C7),
    outline = Color(0xFF899391),
    error = Color(0xFFFFB4AB)
)

// Modern Light Palette - Clean White & Deep Teal Accents
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006972),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF93F1FC),
    onPrimaryContainer = Color(0xFF001F23),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E3),
    onSecondaryContainer = Color(0xFF05201C),
    background = Color(0xFFFBFDFA),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFBFDFA),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDAE5E3),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    error = Color(0xFFBA1A1A)
)

@Composable
fun VaultGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VaultTypography,
        content = content
    )
}
