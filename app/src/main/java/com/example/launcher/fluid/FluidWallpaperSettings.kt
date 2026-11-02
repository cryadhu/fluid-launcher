package com.example.launcher.fluid

import android.app.WallpaperManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.material3.Text
import kotlinx.coroutines.*

@Composable internal fun WallpaperSettings(state:FluidState,model:FluidModel) {
    val scene=LocalGlass.current
    val applying=state.wallpaperApplying
    listOf(false,true).forEach { lock ->
        Text(if(lock) "Lock screen wallpaper" else "Home screen wallpaper",style=F.text(20,true))
        val selected=if(lock)state.config.lockWallpaper else state.config.homeWallpaper
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            listOf("Original","Lavender").forEach { style ->
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(200.dp).glass(24.dp)
                        .clickable(enabled=!applying,role=Role.RadioButton) {
                            model.applyWallpaper(style,lock,scene.dark)
                        }.semantics { contentDescription="${if(lock)"Lock"else"Home"} wallpaper: $style";this.selected=style==selected }) {
                        wallpaper(GlassScene(size,scene.dark,wallpaper=style))
                    }
                    Text(if(selected==style)"✓ $style"else style,style=F.text(12,true),modifier=Modifier.padding(top=10.dp))
                }
            }
        }
    }
    if(applying)Text("Applying wallpaper…",style=F.text(12,muted=true))
}

internal suspend fun applySystemWallpaper(context:android.content.Context,style:String,lock:Boolean,dark:Boolean) = withContext(Dispatchers.IO) {
    val manager=WallpaperManager.getInstance(context)
    check(manager.isWallpaperSupported && manager.isSetWallpaperAllowed) { "Wallpaper changes are unavailable on this phone" }
    val metrics=context.resources.displayMetrics
    val width=metrics.widthPixels.coerceAtLeast(1)
    val height=metrics.heightPixels.coerceAtLeast(1)
    val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
    try {
        val size=Size(width.toFloat(),height.toFloat())
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(bitmap.asImageBitmap()),size) {
            wallpaper(GlassScene(size,dark,wallpaper=style))
        }
        manager.setBitmap(bitmap,null,true,if(lock)WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM)
    } finally { bitmap.recycle() }
}
