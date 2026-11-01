package com.example.launcher.ui.theme

import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.launcher.data.HomeCustomization
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.example.launcher.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object Tokens {
    internal var palette by mutableStateOf(tonalPalette)
    var preferences by mutableStateOf(com.example.launcher.data.HomePreferences())
    var badgePackages by mutableStateOf<Set<String>>(emptySet())
    val ground get() = palette.ground
    val decorA get() = androidx.compose.ui.graphics.lerp(palette.ground, palette.raised, 0.45f)
    val decorB get() = androidx.compose.ui.graphics.lerp(palette.ground, palette.raised, 0.3f)
    val surface get() = palette.surface
    val surfaceRaised get() = palette.raised
    val text get() = palette.text
    val muted get() = palette.muted
    val dotInactive get() = androidx.compose.ui.graphics.lerp(palette.ground, palette.muted, 0.5f)
    val handle get() = dotInactive
    val amber get() = palette.amber
    val mint get() = palette.mint
    val coral get() = palette.coral
    val sky get() = palette.sky
    val onAccent get() = palette.onAccent
    val notificationDot = Color(0xFF449CFF)
    // One bundled variable font, resolved at the three specified weights.
    val manrope = FontFamily(
        Font(R.font.manrope, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
        Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
    )
    private fun style(size: Int, weight: FontWeight = FontWeight.Medium, color: Color = text) = TextStyle(fontFamily = manrope, fontWeight = weight, fontSize = size.sp, color = color)
    val clock get() = style(104, FontWeight.Light).copy(lineHeight = 104.sp, letterSpacing = (-4).sp)
    val pageTitle get() = style(34, FontWeight.Light).copy(letterSpacing = (-1).sp)
    val subtitle get() = style(15, color = muted)
    val homeDate get() = style(17, color = muted)
    val body get() = style(15)
    val sectionLabel get() = style(11, FontWeight.Bold, amber).copy(letterSpacing = 1.5.sp)
    val caption get() = style(12, color = muted)
    val appLabel get() = caption
    val drawerLabel get() = style(13, FontWeight.Bold, muted).copy(letterSpacing = 1.5.sp)
    val appTile = RoundedCornerShape(20.dp)
    val iconShape get() = when (preferences.iconShape) { "Circle" -> RoundedCornerShape(50); "Square" -> RoundedCornerShape(6.dp); else -> RoundedCornerShape(20.dp) }
    val widgetCard = RoundedCornerShape(28.dp)
    val storyCard = RoundedCornerShape(24.dp)
    val searchPill = RoundedCornerShape(26.dp)
    val dock = RoundedCornerShape(34.dp)
    val toggle = RoundedCornerShape(22.dp)
}

@Composable fun LauncherTheme(config: HomeCustomization = HomeCustomization(), badgePackages: Set<String> = emptySet(), content: @Composable () -> Unit) {
    val palette = resolvedTheme(config, isSystemInDarkTheme())
    Tokens.palette = palette
    Tokens.preferences = config.preferences
    Tokens.badgePackages = badgePackages
    val activity = LocalContext.current as? android.app.Activity
    val view = LocalView.current
    SideEffect { activity?.let { WindowCompat.getInsetsController(it.window, view).apply { isAppearanceLightStatusBars = !palette.dark; isAppearanceLightNavigationBars = !palette.dark } } }
    val colors = if (palette.dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = colors.copy(primary = Tokens.amber, onPrimary = Tokens.onAccent, secondary = Tokens.mint, onSecondary = Tokens.onAccent, tertiary = Tokens.sky, onTertiary = Tokens.onAccent, background = Tokens.ground, onBackground = Tokens.text, surface = Tokens.surface, onSurface = Tokens.text, surfaceVariant = Tokens.surfaceRaised, onSurfaceVariant = Tokens.muted, error = Tokens.coral, onError = Tokens.onAccent),
        typography = Typography(bodyLarge = Tokens.body, bodyMedium = Tokens.body, bodySmall = Tokens.caption, titleLarge = Tokens.pageTitle, labelSmall = Tokens.sectionLabel),
        shapes = Shapes(extraSmall = Tokens.appTile, small = Tokens.toggle, medium = Tokens.storyCard, large = Tokens.widgetCard, extraLarge = Tokens.dock),
        content = content
    )
}
