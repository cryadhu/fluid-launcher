package com.example.launcher.data

import com.example.launcher.ui.theme.*
import com.example.launcher.ui.parseThemeColor
import org.junit.Test
import org.junit.Assert.*

class ThemePreferencesTest {
    @Test fun themesAndCustomColorsPersistWithPagesAndWidgets() {
        val config = HomeCustomization(theme = "Custom", followSystemTheme = true, customBackground = 0xFFF0EEDD.toInt(), customAccent = 0xFF225588.toInt(), wallpaper = "Theme")
        assertEquals(config, decodeCustomization(encodeCustomization(config)))
        assertEquals("Tonal", decodeCustomization("{}").theme)
        assertFalse(decodeCustomization("{}").followSystemTheme)
    }
    @Test fun systemModeAndCustomPaletteUseSavedColors() {
        val config = HomeCustomization(theme = "Ember", followSystemTheme = true)
        assertEquals(themePalette("Sand"), resolvedTheme(config, false))
        assertEquals(themePalette("Tonal"), resolvedTheme(config, true))
        val custom = config.copy(theme = "Custom", followSystemTheme = false, customBackground = 0xFFF0EEDD.toInt(), customAccent = 0xFF225588.toInt())
        val palette = resolvedTheme(custom, true)
        assertFalse(palette.dark)
        assertEquals(androidx.compose.ui.graphics.Color(custom.customBackground), palette.ground)
        assertEquals(androidx.compose.ui.graphics.Color(custom.customAccent), palette.amber)
    }
    @Test fun customColorInputAcceptsOnlyOpaqueHexRgb() {
        assertEquals(0xFF12AB34.toInt(), parseThemeColor(" #12ab34 "))
        assertNull(parseThemeColor("red"))
        assertNull(parseThemeColor("#123"))
        assertNull(parseThemeColor("#00112233"))
    }
}
