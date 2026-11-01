package com.example.launcher.fluid

import android.app.PendingIntent
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FluidNotification(val key: String, val app: String, val title: String, val text: String, val time: Long,
    val icon: Bitmap?, val intent: PendingIntent?, val clearable: Boolean)
/** In-memory only. No notification text or sender is written to storage. */
object FluidNotifications {
    internal val mutable = MutableStateFlow<List<FluidNotification>>(emptyList())
    val items = mutable.asStateFlow()
    internal var cancel: ((String) -> Unit)? = null
    fun dismiss(key: String) { cancel?.invoke(key) }
    fun clear() { items.value.filter { it.clearable }.forEach { dismiss(it.key) } }
}
