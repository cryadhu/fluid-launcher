@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher.fluid

import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.example.launcher.data.SoundMode
import com.example.launcher.ui.Glyph

internal val tinyControls=setOf(FluidWidget.WIFI,FluidWidget.BLUETOOTH,FluidWidget.MOBILE_DATA,FluidWidget.SOUND,FluidWidget.FLASHLIGHT,FluidWidget.AIRPLANE)

@Composable internal fun TinyControl(kind:FluidWidget,state:FluidState,model:FluidModel,modifier:Modifier=Modifier) {
    val edit=LocalWidgetEdit.current
    val context=LocalContext.current
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it)model.toggle("torch")else model.message("Allow camera access to use the flashlight") }
    val (active,subtitle)=when(kind) {
        FluidWidget.WIFI -> state.device.wifi to (state.device.wifiName ?: if(state.device.wifi)"On"else"Off")
        FluidWidget.BLUETOOTH -> (state.device.bluetooth==true) to (if(state.device.bluetooth==true)"On"else"Off")
        FluidWidget.MOBILE_DATA -> (state.device.mobileData==true) to (if(state.device.mobileData==true)"On"else"Off")
        FluidWidget.SOUND -> (state.device.soundMode!=SoundMode.NORMAL) to state.device.soundMode.label
        FluidWidget.FLASHLIGHT -> state.device.torch to (if(state.device.torch)"On"else"Off")
        else -> state.device.airplaneMode to (if(state.device.airplaneMode)"On"else"Off")
    }
    val action=when(kind){FluidWidget.WIFI->"wifi";FluidWidget.BLUETOOTH->"bluetooth";FluidWidget.MOBILE_DATA->"mobile";FluidWidget.SOUND->"silent";FluidWidget.FLASHLIGHT->"torch";else->"airplane"}
    Column(modifier.fillMaxWidth().heightIn(min=when(state.config.sizes[kind]){"Compact"->84.dp;"Large"->130.dp;else->100.dp}).widgetGesture(kind,state,model).glass(24.dp)
        .combinedClickable(role=Role.Button,onClick={
            if(kind==FluidWidget.FLASHLIGHT && androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED)camera.launch(android.Manifest.permission.CAMERA)
            else model.toggle(action)
        },onLongClick={edit(kind)}).testTag("fluid-widget-${kind.name}").padding(horizontal=8.dp,vertical=14.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp,Alignment.CenterVertically)) {
        Glyph(if(kind==FluidWidget.SOUND && state.device.soundMode==SoundMode.VIBRATE)"vibrate"else kind.glyph,if(active)F.purple else F.muted,Modifier.size(24.dp))
        Text(kind.title,style=F.text(11,true),maxLines=1)
        Text(subtitle,style=F.text(10,muted=true),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable internal fun ControlWidgetRows(kinds:List<FluidWidget>,state:FluidState,model:FluidModel) {
    kinds.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            row.forEach { kind -> key(kind) { TinyControl(kind,state,model,Modifier.weight(1f)) } }
            repeat(3-row.size){Spacer(Modifier.weight(1f))}
        }
    }
}

@Composable internal fun BrightnessWidget(model:FluidModel) {
    val context=LocalContext.current
    val activity=context as? Activity
    var value by remember { mutableFloatStateOf(runCatching { Settings.System.getInt(context.contentResolver,Settings.System.SCREEN_BRIGHTNESS)/255f }.getOrDefault(.5f)) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Glyph("weather",F.purple,Modifier.size(24.dp))
        Slider(value,{value=it;activity?.window?.let { w -> w.attributes=w.attributes.apply { screenBrightness=it.coerceIn(.02f,1f) } }},
            Modifier.weight(1f).semantics { contentDescription="Brightness" },valueRange=.02f..1f,onValueChangeFinished={
                if(Settings.System.canWrite(context)) {
                    Settings.System.putInt(context.contentResolver,Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(context.contentResolver,Settings.System.SCREEN_BRIGHTNESS,(value*255).toInt())
                } else model.open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))
            })
    }
}

@Composable internal fun VolumeWidget() {
    val context=LocalContext.current
    val audio=remember { context.getSystemService(AudioManager::class.java) }
    val maximum=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var value by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Glyph("music",F.purple,Modifier.size(24.dp))
        Slider(value,{value=it;audio.setStreamVolume(AudioManager.STREAM_MUSIC,it.toInt(),0)},Modifier.weight(1f).semantics{contentDescription="Media volume"},valueRange=0f..maximum.toFloat())
    }
}
