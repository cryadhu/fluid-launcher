package com.example.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.launcher.ui.theme.Tokens as T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun PhotoBackground(key: String) {
    val context = LocalContext.current
    var image by remember(key) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(key) { image = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(java.io.File(context.filesDir,"home_photo.jpg").path) } }
    image?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    Box(Modifier.fillMaxSize().background(if (T.palette.dark) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.4f)))
}
