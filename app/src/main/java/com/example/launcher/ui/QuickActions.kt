package com.example.launcher.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.launcher.LauncherViewModel
import com.example.launcher.ui.theme.Tokens as T
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.documentscanner.*

@Composable fun QuickActions(model: LauncherViewModel, torch: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    var scannedText by remember { mutableStateOf<String?>(null) }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri?.let { uri ->
                model.action { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            }
        }
    }
    fun open(intent: Intent) = model.action { model.device.open(intent) }
    fun runAction(key: String) {
        when (key) {
            "scan" -> {
                val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(10).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { document.launch(IntentSenderRequest.Builder(it).build()) }
                    .addOnFailureListener { model.message("Document scanning needs Google Play services. Opening Camera."); open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
            }
            "translate" -> open(Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.google.com/")))
            "calculator" -> {
                val app = model.uiState.value.apps.firstOrNull { it.component.packageName.contains("calculator", true) || it.label.contains("calculator", true) }
                if (app != null) model.launch(app) else open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR))
            }
            "timer" -> open(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_SKIP_UI, false))
            "qr" -> GmsBarcodeScanning.getClient(activity).startScan().addOnSuccessListener { scannedText = it.rawValue ?: "No text in this code" }.addOnFailureListener { model.message("QR scanning needs Google Play services on this phone") }
            "mic" -> open(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION))
            "airplane" -> open(Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            "flashlight" -> if (model.uiState.value.device.hasTorch) torch() else model.message("No flashlight on this device")
        }
    }
    val actions = listOf("scan" to "Scan", "translate" to "Translate", "calculator" to "Calculator", "clock" to "Timer", "qr" to "QR code", "mic" to "Voice memo", "airplane" to "Airplane mode", "flashlight" to "Flashlight")
    CardBlock {
        Label("QUICK ACTIONS")
        actions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (glyph, title) ->
                    val accent = listOf(T.amber, T.mint, T.coral, T.sky, T.sky, T.amber, T.mint, T.coral)[actions.indexOf(glyph to title)]
                    Column(Modifier.weight(1f).clip(T.appTile).clickable(role = Role.Button) { model.action { runAction(if (glyph == "clock") "timer" else glyph) } }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.sizeIn(maxWidth = 64.dp).fillMaxWidth().aspectRatio(1f).background(T.surfaceRaised, T.appTile), contentAlignment = Alignment.Center) { Glyph(glyph, accent, Modifier.size(28.dp)) }
                        Text(title, style = T.caption, textAlign = TextAlign.Center, minLines = 2)
                    }
                }
            }
        }
    }
    scannedText?.let { text ->
        AlertDialog(onDismissRequest = { scannedText = null }, containerColor = T.surface, title = { Text("Scanned code", color = T.text) }, text = { androidx.compose.foundation.text.selection.SelectionContainer { Text(text, style = T.body) } },
            confirmButton = { TextButton(onClick = { val clipboard = context.getSystemService(android.content.ClipboardManager::class.java); clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Scanned code", text)); scannedText = null }) { Text("Copy", color = T.amber) } },
            dismissButton = { TextButton(onClick = { scannedText = null }) { Text("Close", color = T.muted) } })
    }
}
