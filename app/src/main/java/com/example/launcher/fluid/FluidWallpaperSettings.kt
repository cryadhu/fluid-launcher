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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.*

internal val wallpaperStyles=listOf("Pastel Glass","Default","Dawn","Lavender")
internal fun isCustomWallpaper(style:String)=style.matches(Regex("Custom:[a-f0-9-]+\\.jpg"))
internal fun validWallpaper(style:String)=style in wallpaperStyles || isCustomWallpaper(style)
internal fun loadWallpaperImage(context:android.content.Context,style:String):ImageBitmap = when {
    style=="Pastel Glass" -> loadPastelGlassWallpaper(context)
    isCustomWallpaper(style) -> android.graphics.BitmapFactory.decodeFile(java.io.File(context.filesDir,style.removePrefix("Custom:")).path)?.asImageBitmap() ?: loadDawnWallpaper(context)
    else -> loadDawnWallpaper(context)
}

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
private var pastelGlassWallpaper:ImageBitmap?=null
@Synchronized internal fun loadPastelGlassWallpaper(context:android.content.Context):ImageBitmap = pastelGlassWallpaper ?: android.graphics.BitmapFactory.decodeResource(context.resources,com.example.launcher.R.drawable.wallpaper_pastel_glass).asImageBitmap().also { pastelGlassWallpaper=it }

@Composable internal fun WallpaperSettings(state:FluidState,model:FluidModel) {
    val context=LocalContext.current
    val scene=LocalGlass.current
    val applying=state.wallpaperApplying
    listOf(false,true).forEach { lock ->
        val picker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)model.importWallpaper(uri,lock) }
        Text(if(lock) "Lock screen wallpaper" else "Home screen wallpaper",style=F.text(20,true))
        val selected=if(lock)state.config.lockWallpaper else state.config.homeWallpaper
        val blur=if(lock)state.config.lockWallpaperBlur else state.config.homeWallpaperBlur
        val tiles=wallpaperStyles+"Custom photo"
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    row.forEach { style ->
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                            if(style=="Custom photo") {
                                Box(Modifier.fillMaxWidth().height(164.dp).glass(24.dp).clickable(enabled=!applying,role=Role.Button){picker.launch(arrayOf("image/*"))}.semantics { contentDescription="${if(lock)"Lock"else"Home"} wallpaper: Custom photo";this.selected=isCustomWallpaper(selected) },contentAlignment=Alignment.Center) {
                                    Column(horizontalAlignment=Alignment.CenterHorizontally) { Glyph("image",F.purple,Modifier.size(28.dp));Text("Choose photo",style=F.text(12,true),modifier=Modifier.padding(top=10.dp)) }
                                }
                            } else {
                                val image=remember(style){loadWallpaperImage(context,style)}
                                Canvas(Modifier.fillMaxWidth().height(164.dp).glass(24.dp).clickable(enabled=!applying,role=Role.RadioButton){model.applyWallpaper(style,lock,scene.dark)}.semantics { contentDescription="${if(lock)"Lock"else"Home"} wallpaper: $style";this.selected=style==selected }) {
                                    wallpaper(scene.copy(size=size,frost=null,wallpaper=style,wallpaperImage=image,wallpaperBlur=false))
                                }
                            }
                            Text(if((style=="Custom photo" && isCustomWallpaper(selected)) || selected==style)"✓ $style"else style,style=F.text(12,true),modifier=Modifier.padding(top=10.dp),maxLines=1)
                        }
                    }
                    repeat(3-row.size){Spacer(Modifier.weight(1f))}
                }
            }
            Row(Modifier.fillMaxWidth().glass(22.dp).padding(horizontal=16.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Blur wallpaper",style=F.text(13,true));Text("Apply a soft blur to this screen",style=F.text(10,muted=true),modifier=Modifier.padding(top=3.dp)) }
                Switch(blur,{model.setWallpaperBlur(lock,it)},enabled=!applying,colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=F.purple,uncheckedTrackColor=F.muted.copy(alpha=.2f),uncheckedBorderColor=Color.Transparent))
            }
        }
    }
    if(applying)Text("Applying wallpaper…",style=F.text(12,muted=true))
}

internal suspend fun applySystemWallpaper(context:android.content.Context,style:String,lock:Boolean,blur:Boolean) = withContext(Dispatchers.IO) {
    val manager=WallpaperManager.getInstance(context)
    check(manager.isWallpaperSupported && manager.isSetWallpaperAllowed) { "Wallpaper changes are unavailable on this phone" }
    val metrics=context.resources.displayMetrics
    val width=metrics.widthPixels.coerceAtLeast(1)
    val height=metrics.heightPixels.coerceAtLeast(1)
    var bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
    try {
        val size=Size(width.toFloat(),height.toFloat())
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(bitmap.asImageBitmap()),size) {
            wallpaper(GlassScene(size,false,wallpaper=style,wallpaperImage=loadWallpaperImage(context,style)))
        }
        if(blur) { val blurred=blurWallpaper(bitmap);bitmap.recycle();bitmap=blurred }
        manager.setBitmap(bitmap,null,true,if(lock)WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM)
    } finally { bitmap.recycle() }
}

/** Downsample before blurring so applying a wallpaper remains fast on high-density displays. */
private fun blurWallpaper(source:android.graphics.Bitmap):android.graphics.Bitmap {
    val width=source.width;val height=source.height;val sampleWidth=minOf(320,width);val sampleHeight=(height*sampleWidth.toFloat()/width).toInt().coerceAtLeast(1)
    val sample=android.graphics.Bitmap.createScaledBitmap(source,sampleWidth,sampleHeight,true)
    val pixels=IntArray(sampleWidth*sampleHeight);sample.getPixels(pixels,0,sampleWidth,0,0,sampleWidth,sampleHeight)
    repeat(3){blurPass(pixels,sampleWidth,sampleHeight,9)}
    sample.setPixels(pixels,0,sampleWidth,0,0,sampleWidth,sampleHeight)
    val result=android.graphics.Bitmap.createScaledBitmap(sample,width,height,true);sample.recycle();return result
}
