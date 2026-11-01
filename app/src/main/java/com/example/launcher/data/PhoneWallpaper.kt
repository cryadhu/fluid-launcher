package com.example.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun wallpaperColor(name: String): Int = when (name) {
    "Midnight" -> 0xFF0D1728.toInt()
    "Charcoal" -> 0xFF171A1D.toInt()
    "Plum" -> 0xFF211725.toInt()
    "Paper" -> 0xFFF3EFE6.toInt()
    else -> 0xFF0E1A19.toInt()
}

suspend fun applyPhoneWallpaper(context: Context, name: String) = withContext(Dispatchers.IO) {
    val manager = WallpaperManager.getInstance(context)
    check(manager.isWallpaperSupported && manager.isSetWallpaperAllowed) { "Wallpaper changes are blocked on this phone" }
    val metrics = context.resources.displayMetrics
    val bitmap = Bitmap.createBitmap(metrics.widthPixels.coerceIn(1, 2048), metrics.heightPixels.coerceIn(1, 2560), Bitmap.Config.ARGB_8888)
    try {
        bitmap.eraseColor(wallpaperColor(name))
        check(manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) != 0) { "Phone could not apply the wallpaper" }
    } finally { bitmap.recycle() }
}

suspend fun applyPhotoWallpaper(context: Context, uri: android.net.Uri) = withContext(Dispatchers.IO) {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot read photo" }
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2560) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("Cannot read photo")
    try {
        val manager = WallpaperManager.getInstance(context)
        check(manager.isWallpaperSupported && manager.isSetWallpaperAllowed)
        val temp = java.io.File(context.filesDir, "home_photo.tmp")
        temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
        check(manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) != 0)
        check(temp.renameTo(java.io.File(context.filesDir, "home_photo.jpg")))
    } finally { bitmap.recycle() }
}
