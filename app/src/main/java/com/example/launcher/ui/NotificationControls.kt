package com.example.launcher.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.launcher.LauncherViewModel
import com.example.launcher.data.DeviceState
import com.example.launcher.data.MediaAccessService
import com.example.launcher.ui.theme.Tokens as T

@Composable fun NotificationControls(device: DeviceState, model: LauncherViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refreshDevice() }
    val prefs = remember { context.getSharedPreferences("permission_prompts", Context.MODE_PRIVATE) }
    CardBlock {
        Label("NOTIFICATIONS")
        Text(if (device.notificationsEnabled) "Notifications allowed" else "Notifications are off", style = T.body)
        ActionPill(if (device.notificationsEnabled) "Notification settings" else "Allow notifications", {
            val missing = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (missing && (!prefs.getBoolean("notifications_asked", false) || ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, Manifest.permission.POST_NOTIFICATIONS))) {
                prefs.edit().putBoolean("notifications_asked", true).apply()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else model.action { model.device.open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
        }, Modifier.fillMaxWidth())
    }
}

@Composable fun MediaAccessButton(model: LauncherViewModel) {
    val context = LocalContext.current
    var help by remember { mutableStateOf(false) }
    ActionPill("Enable media access", { help = true })
    if (help) AlertDialog(onDismissRequest = { help = false }, containerColor = T.surface,
        title = { Text("Music controls access", color = T.text) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Allow notification access so the launcher can show and control your current music session. This access is separate from permission to send notifications.", style = T.subtitle)
            Text("If Android disables the switch after APK installation: open notification access once, then App info → ⋮ → Allow restricted settings. Return to notification access and enable the music-controls service. Menu names may differ on ColorOS.", style = T.subtitle)
            ActionPill("Open notification access", {
                model.action {
                    val detail = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, MediaAccessService::class.java).flattenToString()) else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    try { model.device.open(detail) } catch (_: android.content.ActivityNotFoundException) { model.device.open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                }
            }, Modifier.fillMaxWidth())
            ActionPill("App info", { model.action { model.device.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) } }, Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { help = false }) { Text("Done", color = T.amber) } })
}
