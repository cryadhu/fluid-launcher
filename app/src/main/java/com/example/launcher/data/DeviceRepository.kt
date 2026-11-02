@file:Suppress("DEPRECATION")
package com.example.launcher.data

import android.Manifest
import android.app.NotificationManager
import android.app.NotificationChannel
import android.net.Uri
import android.provider.Settings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.media.AudioManager
import android.media.session.*
import android.net.wifi.WifiManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import android.location.LocationManager
import android.os.*
import android.provider.CalendarContract
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.text.Collator


data class InstalledApp(val component: ComponentName, val label: String, val icon: AppIconStyle, val artwork: android.graphics.Bitmap? = null, val firstInstalledAt: Long = 0)
data class AgendaEvent(val title: String, val times: String)
data class FavoriteContact(val name:String,val phone:String)
enum class ConnectivityType { WIFI, BLUETOOTH }
data class PairedDevice(val name: String, val address: String)
data class WifiNetwork(val ssid: String, val capabilities: String, val level: Int)
enum class SoundMode(val label: String) { NORMAL("Normal"), VIBRATE("Vibrate"), SILENT("Silent");
    fun next() = when (this) { NORMAL -> SILENT; SILENT -> VIBRATE; VIBRATE -> NORMAL }
}
data class DeviceState(val wifi: Boolean = false, val bluetooth: Boolean? = null, val torch: Boolean = false, val hasTorch: Boolean = false, val silent: Boolean = false, val calendarGranted: Boolean = false, val events: List<AgendaEvent> = emptyList(), val calendarError: Boolean = false, val mediaAccess: Boolean = false, val mediaTitle: String? = null, val playing: Boolean = false, val defaultHome: Boolean = false,
    val wifiConnected: Boolean = false, val wifiName: String? = null, val bluetoothAvailable: Boolean = true, val bluetoothGranted: Boolean = false, val pairedDevices: List<PairedDevice> = emptyList(),
    val wifiNetworks: List<WifiNetwork> = emptyList(), val wifiScanGranted: Boolean = false, val locationEnabled: Boolean = false,
    val soundMode: SoundMode = SoundMode.NORMAL, val mobileData: Boolean? = null, val mobileAvailable: Boolean = false,
    val nearbyBluetooth: List<PairedDevice> = emptyList(), val bluetoothScanning: Boolean = false,
    val notificationsEnabled: Boolean = false, val airplaneMode: Boolean = false, val favorites:List<FavoriteContact> = emptyList())

class DeviceRepository(private val context: Context) {
    private val camera = context.getSystemService(CameraManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val media = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaAccessService::class.java)
    private var controller: MediaController? = null
    private var torchOn = false
    private val nearbyBluetooth = java.util.concurrent.ConcurrentHashMap<String, PairedDevice>()
    private var discoveryRegistered = false
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                val found = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                try { found?.let { if (it.bondState != BluetoothDevice.BOND_BONDED) nearbyBluetooth[it.address] = PairedDevice(it.name ?: "Bluetooth device", it.address) } } catch (_: SecurityException) { }
            }
            onChange?.invoke()
        }
    }
    private val cameraId get() = runCatching { camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && camera.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } }.getOrNull()
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) { if (id == cameraId) { torchOn = enabled; onChange?.invoke() } }
    }
    private val playbackCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { onChange?.invoke() }
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) { onChange?.invoke() }
        override fun onSessionDestroyed() { onChange?.invoke() }
    }
    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { onChange?.invoke() }
    var onChange: (() -> Unit)? = null
    init {
        runCatching { camera.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("launcher", "Launcher notifications", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun close() { stopBluetoothScan(); runCatching { camera.unregisterTorchCallback(callback) }; runCatching { media.removeOnActiveSessionsChangedListener(sessionListener) }; controller?.unregisterCallback(playbackCallback) }
    suspend fun apps(nativeIcons: Boolean = false): List<InstalledApp> = withContext(Dispatchers.IO) {
        val installTimes = mutableMapOf<String, Long>()
        val pm = context.packageManager
        val collator = Collator.getInstance()
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
            .mapNotNull { info -> runCatching {
                val label = info.loadLabel(pm).toString()
                val category = when (info.activityInfo.applicationInfo.category) {
                    ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
                    ApplicationInfo.CATEGORY_AUDIO -> AppCategory.AUDIO
                    ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
                    ApplicationInfo.CATEGORY_IMAGE -> AppCategory.IMAGE
                    ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
                    ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
                    ApplicationInfo.CATEGORY_MAPS -> AppCategory.MAPS
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
                    else -> AppCategory.OTHER
                }
                val style = appIconStyle(info.activityInfo.packageName, label, category)
                val systemApp = info.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                // Keep the supplied utility glyphs; brands use their own activity icon, including
                // preinstalled Indian apps. ResolveInfo respects activity aliases and icon updates.
                val artwork = if (!nativeIcons && usesBundledSystemIcon(systemApp, style.glyph)) null else
                    runCatching { installedIconBitmap(info.loadIcon(pm)) }.getOrNull()
                val installedAt = installTimes.getOrPut(info.activityInfo.packageName) { runCatching { pm.getPackageInfo(info.activityInfo.packageName, 0).firstInstallTime }.getOrDefault(0) }
                InstalledApp(ComponentName(info.activityInfo.packageName, info.activityInfo.name), label, style, artwork, installedAt)
            }.getOrNull() }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
    suspend fun state(): DeviceState = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val wifiConnected = runCatching { connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }.getOrDefault(false)
        val wifiName = runCatching { wifi.connectionInfo?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } }.getOrNull()
        val scanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val location = context.getSystemService(LocationManager::class.java)
        val locationEnabled = if (Build.VERSION.SDK_INT >= 28) location.isLocationEnabled else location.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networks = if (scanGranted && locationEnabled) runCatching { wifi.scanResults.filter { it.SSID.isNotBlank() }.sortedByDescending { it.level }.distinctBy { it.SSID }.map { WifiNetwork(it.SSID, it.capabilities, it.level) } }.getOrDefault(emptyList()) else emptyList()
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val bluetooth = context.getSystemService(BluetoothManager::class.java).adapter
        val bluetoothGranted = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val paired = if (bluetoothGranted) runCatching { bluetooth?.bondedDevices.orEmpty().map { PairedDevice(it.name ?: "Bluetooth device", it.address) }.sortedBy { it.name.lowercase() } }.getOrDefault(emptyList()) else emptyList()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val calendar = if (granted) runCatching { calendar() } else Result.success(emptyList())
        val contacts=if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED) runCatching { favorites() }.getOrDefault(emptyList()) else emptyList()
        val access = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val sessions = if (access) runCatching { media.getActiveSessions(listener) }.getOrDefault(emptyList()) else emptyList()
        val active = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        withContext(Dispatchers.Main) {
            if (controller?.sessionToken != active?.sessionToken) { controller?.unregisterCallback(playbackCallback); controller = active; controller?.registerCallback(playbackCallback) }
            runCatching { media.removeOnActiveSessionsChangedListener(sessionListener) }
            if (access) runCatching { media.addOnActiveSessionsChangedListener(sessionListener, listener, Handler(Looper.getMainLooper())) }
        }
        DeviceState(
            wifi = runCatching { wifi.isWifiEnabled }.getOrDefault(false),
            bluetooth = runCatching { bluetooth?.isEnabled }.getOrNull(),
            torch = torchOn, hasTorch = cameraId != null, silent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL,
            calendarGranted = granted, events = calendar.getOrDefault(emptyList()), calendarError = calendar.isFailure,
            mediaAccess = access, mediaTitle = active?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: active?.let { "Media session" },
            playing = active?.playbackState?.state == PlaybackState.STATE_PLAYING,
            defaultHome = HomeSetup.isDefaultHome(context), wifiConnected = wifiConnected, wifiName = wifiName,
            bluetoothAvailable = bluetooth != null, bluetoothGranted = bluetoothGranted, pairedDevices = paired,
            wifiNetworks = networks, wifiScanGranted = scanGranted, locationEnabled = locationEnabled,
            soundMode = when (audio.ringerMode) { AudioManager.RINGER_MODE_SILENT -> SoundMode.SILENT; AudioManager.RINGER_MODE_VIBRATE -> SoundMode.VIBRATE; else -> SoundMode.NORMAL },
            mobileAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
            mobileData = runCatching { telephony.isDataEnabled }.getOrNull(),
            nearbyBluetooth = nearbyBluetooth.values.filter { candidate -> paired.none { it.address == candidate.address } }.sortedBy { it.name },
            bluetoothScanning = runCatching { bluetooth?.isDiscovering == true }.getOrDefault(false),
            notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
            airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0, favorites=contacts
        )
    }
    private fun calendar(): List<AgendaEvent> {
        val now = System.currentTimeMillis()
        val end = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, now); ContentUris.appendId(it, end) }.build()
        val result = mutableListOf<AgendaEvent>()
        context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY), "${CalendarContract.Instances.END} > ? AND ${CalendarContract.Instances.VISIBLE} = 1", arrayOf(now.toString()), "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext() && result.size < 30) {
                fun time(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                result += AgendaEvent(cursor.getString(0) ?: "Untitled event", if (cursor.getInt(3) == 1) "All day" else "${time(cursor.getLong(1))} – ${time(cursor.getLong(2))}")
            }
        }
        return result
    }
    private fun favorites():List<FavoriteContact> {
        val result=mutableListOf<FavoriteContact>()
        context.contentResolver.query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),"${android.provider.ContactsContract.CommonDataKinds.Phone.STARRED} = 1",null,"${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { cursor ->
            while(cursor.moveToNext() && result.size<4) { val name=cursor.getString(0).orEmpty();val phone=cursor.getString(1).orEmpty();if(name.isNotBlank()&&phone.isNotBlank()&&result.none{it.phone==phone})result+=FavoriteContact(name,phone) }
        };return result
    }
    fun launch(app: InstalledApp) {
        if (app.component.packageName == "com.truecaller") { open(truecallerDialIntent()); return }
        context.getSystemService(LauncherApps::class.java).startMainActivity(app.component, Process.myUserHandle(), null, appLaunchOptions())
    }
    fun torch() { camera.setTorchMode(cameraId ?: error("No flashlight on this device"), !torchOn) }
    fun silent() {
        if (!context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) { open(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); return }
        audio.ringerMode = when (audio.ringerMode) { AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_SILENT; AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_VIBRATE; else -> AudioManager.RINGER_MODE_NORMAL }
    }
    fun scanWifi(): Boolean = context.applicationContext.getSystemService(WifiManager::class.java).startScan()
    fun scanBluetooth(): Boolean {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return false
        if (Build.VERSION.SDK_INT < 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter ?: return false
        if (!discoveryRegistered) {
            ContextCompat.registerReceiver(context, discoveryReceiver, IntentFilter().apply { addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) }, ContextCompat.RECEIVER_EXPORTED)
            discoveryRegistered = true
        }
        if (adapter.isDiscovering) return true
        nearbyBluetooth.clear()
        return adapter.startDiscovery()
    }
    fun stopBluetoothScan() {
        if (discoveryRegistered) {
            try { context.getSystemService(BluetoothManager::class.java).adapter?.cancelDiscovery() } catch (_: SecurityException) { }
            runCatching { context.unregisterReceiver(discoveryReceiver) }; discoveryRegistered = false
        }
    }
    fun pairBluetooth(address: String): Boolean {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter ?: return false
        try { adapter.cancelDiscovery() } catch (_: SecurityException) { }
        return adapter.getRemoteDevice(address).createBond()
    }
    @android.annotation.SuppressLint("MissingPermission") // Carrier privileges are an alternative to the signature-only MODIFY_PHONE_STATE permission.
    fun mobileData(): Boolean {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        if (telephony.hasCarrierPrivileges() || ContextCompat.checkSelfPermission(context, Manifest.permission.MODIFY_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            telephony.setDataEnabled(!telephony.isDataEnabled); return true
        }
        open(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_DATA_ROAMING_SETTINGS)); return false
    }
    internal fun dialIntent(number: String): Intent = truecallerDialIntent(number)
    fun dial(number: String) { open(dialIntent(number)) }
    fun transport(command: String): Boolean {
        val session = controller ?: return false
        val state = session.playbackState?.state ?: return false
        if (state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_ERROR) return false
        val controls = session.transportControls
        when (command) { "previous" -> controls.skipToPrevious(); "next" -> controls.skipToNext(); else -> if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play() }
        return true
    }
    fun startMusicPlayer() {
        val request = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        val target = context.packageManager.resolveActivity(request, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        if (target != null && target.exported && target.packageName !in setOf("android", "com.android.intentresolver")) {
            open(request.setComponent(ComponentName(target.packageName, target.name)))
            return
        }
        val youtube = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
        open(youtube ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/")))
    }
    fun open(intent: Intent) {
        if (intent.action == Intent.ACTION_DIAL) {
            intent.setPackage("com.truecaller")
            intent.component = null
            if (intent.data == null) intent.data = Uri.parse("tel:")
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            val launch = if (intent.action == Intent.ACTION_DIAL) resolveTruecallerCallingIntent(context.packageManager, intent) else intent
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), appLaunchOptions())
        }
        catch (error: ActivityNotFoundException) {
            if (intent.action != Intent.ACTION_DIAL) throw error
            android.widget.Toast.makeText(context, "Truecaller calling screen unavailable. Enable Truecaller as your phone app.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    private fun appLaunchOptions(): Bundle = android.app.ActivityOptions.makeCustomAnimation(context,
        com.example.launcher.R.anim.app_launch, com.example.launcher.R.anim.home_stationary).toBundle()
}
