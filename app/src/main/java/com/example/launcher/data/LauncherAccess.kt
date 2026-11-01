package com.example.launcher.data

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationBadges {
    internal val mutable = MutableStateFlow<Set<String>>(emptySet())
    val packages = mutable.asStateFlow()
}
class MediaAccessService : NotificationListenerService() {
    private fun publish() {
        val active = runCatching { activeNotifications.orEmpty().toList() }.getOrDefault(emptyList())
        NotificationBadges.mutable.value = active.map { it.packageName }.toSet()
        com.example.launcher.fluid.FluidNotifications.mutable.value = active.filter { it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0 }.sortedByDescending { it.postTime }.map { n ->
            val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(n.packageName, 0)).toString() }.getOrDefault(n.packageName)
            val icon = runCatching { installedIconBitmap(packageManager.getApplicationIcon(n.packageName)) }.getOrNull()
            val extras = n.notification.extras
            com.example.launcher.fluid.FluidNotification(n.key, app, extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: app,
                extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty(), n.postTime, icon, n.notification.contentIntent, n.isClearable)
        }
    }
    override fun onListenerConnected() { com.example.launcher.fluid.FluidNotifications.cancel = { cancelNotification(it) }; publish() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { publish() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { publish() }
    override fun onListenerDisconnected() { NotificationBadges.mutable.value = emptySet(); com.example.launcher.fluid.FluidNotifications.mutable.value = emptyList(); com.example.launcher.fluid.FluidNotifications.cancel = null }
    override fun onDestroy() { onListenerDisconnected(); super.onDestroy() }
}
/** No window-content access. Only the explicit double-tap lock gesture invokes it. */
class LauncherLockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    companion object {
        private var instance: LauncherLockService? = null
        fun available() = instance != null && Build.VERSION.SDK_INT >= 28
        fun lock(): Boolean = Build.VERSION.SDK_INT >= 28 && instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
    }
}
