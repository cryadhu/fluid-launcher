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

internal val wallpaperStyles=listOf("Dawn","Original","Lavender")
internal fun isCustomWallpaper(style:String)=style.matches(Regex("Custom:[a-f0-9-]+\\.jpg"))
internal fun validWallpaper(style:String)=style in wallpaperStyles || isCustomWallpaper(style)
internal fun loadWallpaperImage(context:android.content.Context,style:String):ImageBitmap =
    if(isCustomWallpaper(style)) android.graphics.BitmapFactory.decodeFile(java.io.File(context.filesDir,style.removePrefix("Custom:")).path)?.asImageBitmap() ?: loadDawnWallpaper(context)
    else loadDawnWallpaper(context)

internal fun saveCustomWallpaper(context:android.content.Context,uri:android.net.Uri):String {
    val bitmap=if(android.os.Build.VERSION.SDK_INT>=28) {
        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver,uri)) { decoder,info,_ ->
            val scale=minOf(1f,2160f/maxOf(info.size.width,info.size.height))
            decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1))
            decoder.allocator=android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
        context.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it,null,bounds) }
        check(bounds.outWidth>0 && bounds.outHeight>0)
        val options=android.graphics.BitmapFactory.Options().apply { inSampleSize=1;while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize>2160)inSampleSize*=2 }
        context.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it,null,options) } ?: error("Invalid image")
    }
    val name="${java.util.UUID.randomUUID()}.jpg"
    val file=java.io.File(context.filesDir,name)
    try { file.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,it)) } }
    catch(e:Exception){file.delete();throw e}
    finally { bitmap.recycle() }
    return "Custom:$name"
}

private var dawnWallpaper:ImageBitmap?=null
@Synchronized internal fun loadDawnWallpaper(context:android.content.Context):ImageBitmap = dawnWallpaper ?: android.graphics.BitmapFactory.decodeResource(context.resources,com.example.launcher.R.drawable.wallpaper_dawn).asImageBitmap().also { dawnWallpaper=it }

@Composable internal fun WallpaperSettings(state:FluidState,model:FluidModel) {
    val context=LocalContext.current
    val dawn=remember { loadDawnWallpaper(context) }
    val scene=LocalGlass.current
    val applying=state.wallpaperApplying
    listOf(false,true).forEach { lock ->
        val picker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)model.importWallpaper(uri,lock) }
        Text(if(lock) "Lock screen wallpaper" else "Home screen wallpaper",style=F.text(20,true))
        val selected=if(lock)state.config.lockWallpaper else state.config.homeWallpaper
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            wallpaperStyles.forEach { style ->
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(164.dp).glass(24.dp)
                        .clickable(enabled=!applying,role=Role.RadioButton) {
                            model.applyWallpaper(style,lock,scene.dark)
                        }.semantics { contentDescription="${if(lock)"Lock"else"Home"} wallpaper: $style";this.selected=style==selected }) {
                        wallpaper(scene.copy(size=size,frost=null,wallpaper=style,wallpaperImage=dawn))
                    }
                    Text(if(selected==style)"✓ $style"else style,style=F.text(12,true),modifier=Modifier.padding(top=10.dp))
                }
            }
        }
        GlassButton(if(isCustomWallpaper(selected))"Change custom photo"else"Choose custom photo",Modifier.fillMaxWidth(),glyph="image") { if(!applying)picker.launch(arrayOf("image/*")) }
        if(isCustomWallpaper(selected)) {
            val image=remember(selected){loadWallpaperImage(context,selected)}
            Image(image,"Selected custom wallpaper",Modifier.fillMaxWidth().height(140.dp),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
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
            wallpaper(GlassScene(size,false,wallpaper=style,wallpaperImage=loadWallpaperImage(context,style)))
        }
        manager.setBitmap(bitmap,null,true,if(lock)WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM)
    } finally { bitmap.recycle() }
}
