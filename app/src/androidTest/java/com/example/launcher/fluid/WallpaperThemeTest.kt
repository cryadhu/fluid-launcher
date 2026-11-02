package com.example.launcher.fluid

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WallpaperThemeTest {
    @Test fun importedPhotoPersistsAndAllWallpapersKeepTheirColorsAcrossThemes() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val input=File.createTempFile("wallpaper-import-",".png",context.cacheDir)
        var style:String?=null
        try {
            val source=Bitmap.createBitmap(64,128,Bitmap.Config.ARGB_8888)
            source.eraseColor(android.graphics.Color.rgb(100,140,210))
            input.outputStream().use { source.compress(Bitmap.CompressFormat.PNG,100,it) }
            source.recycle()
            val imported=saveCustomWallpaper(context,Uri.fromFile(input))
            style=imported
            assertTrue(isCustomWallpaper(imported))
            assertTrue(File(context.filesDir,imported.removePrefix("Custom:")).isFile)
            (wallpaperStyles+imported).forEach { name ->
                val image=loadWallpaperImage(context,name)
                val light=renderFrost(GlassScene(Size(320f,640f),false,wallpaper=name,wallpaperImage=image)).asAndroidBitmap()
                val dark=renderFrost(GlassScene(Size(320f,640f),true,wallpaper=name,wallpaperImage=image)).asAndroidBitmap()
                val a=IntArray(light.width*light.height);val b=IntArray(dark.width*dark.height)
                light.getPixels(a,0,light.width,0,0,light.width,light.height)
                dark.getPixels(b,0,dark.width,0,0,dark.width,dark.height)
                assertArrayEquals("Wallpaper colors changed for $name",a,b)
            }
        } finally {
            input.delete()
            style?.let { File(context.filesDir,it.removePrefix("Custom:")).delete() }
        }
    }
}
