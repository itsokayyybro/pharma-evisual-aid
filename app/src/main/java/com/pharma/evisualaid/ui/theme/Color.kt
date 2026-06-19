package com.pharma.evisualaid.ui.theme

import androidx.compose.ui.graphics.Color

// Core palette — "clinical precision meets pharmaceutical premium".
val Background = Color(0xFF0D0D1A)      // near-black navy
val Surface = Color(0xFF1A1A2E)         // deep blue-navy card
val SurfaceVariant = Color(0xFF16213E)
val PrimaryTeal = Color(0xFF00BCD4)     // default accent (medical teal)
val OnPrimary = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFE8EAF6)
val TextSecondary = Color(0xFF9E9E9E)
val Destructive = Color(0xFFEF5350)

// Accent glow used on card elevation: rgba(0,188,212,0.15)
val AccentGlow = Color(0x2600BCD4)

// Semi-transparent scrim behind the side index panel: #CC000000
val PanelScrim = Color(0xCC000000)

/** The six pharma-appropriate accent presets offered in Settings. */
data class AccentPreset(val label: String, val argb: Long) {
    val color: Color get() = Color(argb)
}

val AccentPresets = listOf(
    AccentPreset("Teal", 0xFF00BCD4),
    AccentPreset("Royal Blue", 0xFF2962FF),
    AccentPreset("Forest Green", 0xFF2E7D32),
    AccentPreset("Deep Purple", 0xFF7C4DFF),
    AccentPreset("Crimson Red", 0xFFE53935),
    AccentPreset("Amber", 0xFFFFB300),
)
