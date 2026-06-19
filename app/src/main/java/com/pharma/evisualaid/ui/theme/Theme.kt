package com.pharma.evisualaid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * App theme. The catalog viewer is always dark (premium medical-device aesthetic), so we
 * only ship a dark color scheme. [accent] overrides the primary color and is driven by the
 * user's choice in Settings.
 */
@Composable
fun EVisualAidTheme(
    accent: Color = PrimaryTeal,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = OnPrimary,
        primaryContainer = accent.copy(alpha = 0.18f),
        onPrimaryContainer = TextPrimary,
        secondary = accent,
        onSecondary = OnPrimary,
        background = Background,
        onBackground = TextPrimary,
        surface = Surface,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = TextSecondary,
        error = Destructive,
        onError = OnPrimary,
        outline = TextSecondary.copy(alpha = 0.4f)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
