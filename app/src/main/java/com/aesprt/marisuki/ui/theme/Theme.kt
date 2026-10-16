package com.aesprt.marisuki.ui.theme

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ProduceGreenLight,
    onPrimary = Color(0xFF153B28),
    primaryContainer = Color(0xFF28553B),
    onPrimaryContainer = Color(0xFFD0EBD8),
    secondary = Color(0xFFB8CFBE),
    onSecondary = Color(0xFF263C2C),
    secondaryContainer = Color(0xFF294638),
    onSecondaryContainer = Color(0xFFD7EBDD),
    tertiary = Color(0xFFF2C078),
    onTertiary = Color(0xFF422C05),
    tertiaryContainer = Color(0xFF624412),
    onTertiaryContainer = Color(0xFFFFDEA8),
    background = DarkCanvas,
    surface = DarkSurface,
    surfaceDim = Color(0xFF101815),
    surfaceBright = Color(0xFF343F39),
    surfaceContainerLowest = Color(0xFF111A16),
    surfaceContainerLow = Color(0xFF1B2521),
    surfaceContainer = Color(0xFF202B26),
    surfaceContainerHigh = Color(0xFF2A3530),
    surfaceContainerHighest = Color(0xFF35413B),
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceVariant = DarkSurfaceVariant,
    outline = Color(0xFF89958B),
    outlineVariant = Color(0xFF414B43),
)

private val LightColorScheme = lightColorScheme(
    primary = ProduceGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF2E7),
    onPrimaryContainer = Color(0xFF0F3D27),
    secondary = LeafGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF2E7),
    onSecondaryContainer = Color(0xFF173E2B),
    tertiary = WarmAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE9C2),
    onTertiaryContainer = Color(0xFF4A3208),
    background = WarmCanvas,
    surface = Color.White,
    surfaceDim = Color(0xFFD8DED9),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F7F5),
    surfaceContainer = Color(0xFFEEF2EF),
    surfaceContainerHigh = Color(0xFFE8EEEA),
    surfaceContainerHighest = Color(0xFFE1E8E3),
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceVariant = LightSurfaceVariant,
    outline = Color(0xFF748477),
    outlineVariant = Color(0xFFD5DED7),
)

private val MariSukiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun MariSukiTheme(
    themePreference: String = "SYSTEM",
    darkTheme: Boolean = when (themePreference.uppercase()) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    },
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
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

    val activity = LocalActivity.current
    SideEffect {
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MariSukiShapes,
        content = content
    )
}
