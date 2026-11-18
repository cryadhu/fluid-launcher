package com.example.launcher.data

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationBadges {
    internal val mutable = MutableStateFlow<Set<String>>(emptySet())
    val packages = mutable.asStateFlow()
}
class MediaAccessService : NotificationListenerService() {
    // Reusing the same label and bitmap lets unchanged reconciles compare equal and skip recomposition.
    private val apps = HashMap<String, Pair<String, android.graphics.Bitmap?>>()
    private fun app(packageName: String) = apps.getOrPut(packageName) {
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
        label to runCatching { installedIconBitmap(packageManager.getApplicationIcon(packageName)) }.getOrNull()
    }
    private fun publish() {
        val active = runCatching { activeNotifications.orEmpty().toList() }.getOrDefault(emptyList())
        NotificationBadges.mutable.value = active.map { it.packageName }.toSet()
        apps.keys.retainAll(NotificationBadges.mutable.value)
        com.example.launcher.fluid.FluidNotifications.mutable.value = active.filter { it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0 }.sortedByDescending { it.postTime }.map { n ->
            val (app, icon) = app(n.packageName)
            val extras = n.notification.extras
            com.example.launcher.fluid.FluidNotification(n.key, app, extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: app,
                extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty(), n.postTime, icon, n.notification.contentIntent, n.isClearable)
        }
    }
    override fun onListenerConnected() {
        com.example.launcher.fluid.FluidNotifications.cancel = { cancelNotification(it) }
        com.example.launcher.fluid.FluidNotifications.refresh = ::publish
        publish()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { publish() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { publish() }
    override fun onListenerDisconnected() { NotificationBadges.mutable.value = emptySet(); com.example.launcher.fluid.FluidNotifications.mutable.value = emptyList(); com.example.launcher.fluid.FluidNotifications.cancel = null; com.example.launcher.fluid.FluidNotifications.refresh = null }
    override fun onDestroy() { onListenerDisconnected(); super.onDestroy() }
}
