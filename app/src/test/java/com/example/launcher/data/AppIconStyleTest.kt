package com.example.launcher.data

import androidx.compose.ui.graphics.vector.PathParser
import com.example.launcher.ui.iconPaths
import org.junit.Assert.*
import org.junit.Test

class AppIconStyleTest {
    @Test fun designAppsUseTheirSuppliedGlyphsAndAccents() {
        val expected = mapOf(
            "com.google.android.apps.photos" to AppIconStyle("photos", AppAccent.AMBER),
            "com.google.android.apps.youtube.music" to AppIconStyle("music", AppAccent.MINT),
            "com.google.android.apps.maps" to AppIconStyle("maps", AppAccent.CORAL),
            "com.google.android.gm" to AppIconStyle("mail", AppAccent.SKY),
            "com.google.android.calendar" to AppIconStyle("calendar", AppAccent.AMBER),
            "com.google.android.deskclock" to AppIconStyle("clock", AppAccent.MINT),
            "com.google.android.keep" to AppIconStyle("notes", AppAccent.CORAL),
            "com.android.settings" to AppIconStyle("settings", AppAccent.SKY)
        )
        expected.forEach { (pkg, style) -> assertEquals(pkg, style, appIconStyle(pkg, if (pkg.endsWith(".gm")) "Gmail" else "")) }
    }
    @Test fun packageMappingWorksWithLocalizedLabels() {
        assertEquals("camera", appIconStyle("com.android.camera", "相机").glyph)
        assertEquals("phone", appIconStyle("com.google.android.dialer", "Téléphone").glyph)
        assertEquals("music", appIconStyle("com.spotify.music", "Spotify").glyph)
    }
    @Test fun unfamiliarAppsUseTheirDeclaredCategory() {
        assertEquals("game", appIconStyle("org.example.unfamiliar", "Aster", AppCategory.GAME).glyph)
        assertEquals("photos", appIconStyle("org.example.unfamiliar", "Aster", AppCategory.IMAGE).glyph)
        assertEquals("video", appIconStyle("org.example.unfamiliar", "Aster", AppCategory.VIDEO).glyph)
    }
    @Test fun unknownAppsAlwaysHaveAStableMatchingFallback() {
        val first = appIconStyle("org.example.unfamiliar", "Aster")
        assertEquals("app", first.glyph)
        assertEquals(first, appIconStyle("org.example.unfamiliar", "別の名前"))
        assertTrue(first.glyph in iconPaths)
    }
    @Test fun bundledPathsAndAllCategoryFallbacksAreValid() {
        iconPaths.forEach { (name, path) -> assertTrue(name, PathParser().parsePathString(path).toNodes().isNotEmpty()) }
        AppCategory.entries.forEach { category -> assertTrue(appIconStyle("org.example.unfamiliar", "Aster", category).glyph in iconPaths) }
    }
}
