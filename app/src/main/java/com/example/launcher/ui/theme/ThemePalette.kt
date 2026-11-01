package com.example.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.launcher.data.HomeCustomization

data class ThemePalette(val dark: Boolean, val ground: Color, val surface: Color, val raised: Color, val text: Color, val muted: Color,
    val amber: Color, val mint: Color, val coral: Color, val sky: Color, val onAccent: Color)
val tonalPalette = ThemePalette(true, Color(0xFF0E1A19), Color(0xFF1A2A28), Color(0xFF243836), Color(0xFFF1F5F2), Color(0xFFB4C3BE), Color(0xFFF2B84B), Color(0xFF7FD6B8), Color(0xFFF08A6B), Color(0xFF8EC5F0), Color(0xFF0E1A19))
fun themePalette(name: String, background: Int = 0xFF0E1A19.toInt(), accent: Int = 0xFFF2B84B.toInt()): ThemePalette = when (name) {
    "Solar" -> ThemePalette(false, Color(0xFFFFCF35), Color(0xFFFFDA62), Color(0xFFFFE590), Color(0xFF35120D), Color(0xFF613321), Color(0xFFCA221F), Color(0xFF612314), Color(0xFFCC2922), Color(0xFF75400C), Color.White)
    "Ember" -> ThemePalette(true, Color(0xFF19110E), Color(0xFF2B1E18), Color(0xFF382720), Color(0xFFF5EBE5), Color(0xFFCDB3A3), Color(0xFFFFB852), Color(0xFF7ED4B6), Color(0xFFF4786B), Color(0xFFECB16B), Color(0xFF19110E))
    "Sand" -> ThemePalette(false, Color(0xFFF3EFE6), Color(0xFFFFFDFA), Color(0xFFEAE3D3), Color(0xFF232D29), Color(0xFF646B63), Color(0xFFAD6B0D), Color(0xFF16856C), Color(0xFFC6482D), Color(0xFF2D70AA), Color.White)
    "Custom" -> {
        val bg = Color(background); val primary = Color(accent); val dark = bg.luminance() < 0.4f
        val base = if (dark) tonalPalette else themePalette("Sand")
        val toward = if (dark) Color.White else Color.Black
        base.copy(ground = bg, surface = lerp(bg, toward, 0.07f), raised = lerp(bg, toward, 0.13f), amber = primary,
            onAccent = if (primary.luminance() > 0.4f) Color(0xFF161616) else Color.White)
    }
    else -> tonalPalette
}
fun resolvedTheme(config: HomeCustomization, systemDark: Boolean): ThemePalette {
    val base = themePalette(if (config.followSystemTheme) { if (systemDark) "Tonal" else "Sand" } else config.theme, config.customBackground, config.customAccent)
    val accent = config.preferences.accentColor?.let { Color(it) } ?: return base
    return base.copy(amber = accent, onAccent = if (accent.luminance() > 0.4f) Color(0xFF161616) else Color.White)
}
