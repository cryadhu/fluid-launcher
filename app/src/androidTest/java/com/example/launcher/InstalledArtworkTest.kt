package com.example.launcher

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.toPixelMap
import com.example.launcher.data.*
import com.example.launcher.ui.*
import com.example.launcher.ui.theme.LauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InstalledArtworkTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun installedLegacyAndAdaptiveIconsKeepTheirArtworkAndBounds() {
        val legacy = ColorDrawable(Color.RED).apply { setBounds(1, 2, 20, 30) }
        val before = android.graphics.Rect(legacy.bounds)
        val bitmap = installedIconBitmap(legacy)
        assertEquals(before, legacy.bounds)
        assertEquals(Color.RED, bitmap.getPixel(48, 48))
        val adaptive = installedIconBitmap(AdaptiveIconDrawable(ColorDrawable(Color.BLUE), ColorDrawable(Color.GREEN)))
        assertEquals(Color.GREEN, adaptive.getPixel(48, 48))
        assertEquals(96, adaptive.width)
    }

    @Test fun brandArtworkRendersInHomeCellsAndFolderPreviews() {
        val app = InstalledApp(ComponentName("com.phonepe.app", "Main"), "PhonePe",
            appIconStyle("com.phonepe.app", "PhonePe"), installedIconBitmap(ColorDrawable(Color.RED)))
        compose.runOnUiThread { compose.activity.setContent { LauncherTheme { Column {
            AppCell(app, {}, {})
            FolderPreview(HomeEntry("folder:test", "Payments", listOf(app.component.flattenToString())), listOf(app), Modifier)
        } } } }
        val icons = compose.onAllNodesWithTag("installed-icon:${app.component.flattenToString()}", useUnmergedTree = true)
        icons.assertCountEquals(2)
        for (index in 0..1) {
            val pixels = icons[index].captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue("Original brand colour must survive", center.red > 0.95f && center.green < 0.05f)
        }
    }
}
