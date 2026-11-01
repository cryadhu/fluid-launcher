package com.example.launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable

/** Only OS utilities retain the design's generic symbols. Never guess a brand from its label. */
internal fun usesBundledSystemIcon(systemApp: Boolean, glyph: String): Boolean = systemApp && glyph in setOf(
    "settings", "calendar", "clock", "camera", "photos", "mail", "contacts", "phone",
    "messages", "browser", "maps", "files", "calculator", "weather", "notes"
)

/** Rasterize off the UI thread. Includes adaptive backgrounds, legacy and layered drawables. */
internal fun installedIconBitmap(drawable: Drawable): Bitmap {
    val icon = drawable.mutate()
    val oldBounds = android.graphics.Rect(icon.bounds)
    return Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { bitmap ->
        try {
            icon.setBounds(0, 0, bitmap.width, bitmap.height)
            icon.draw(Canvas(bitmap))
        } finally { icon.bounds = oldBounds }
    }
}
