package com.example.launcher

import android.app.role.RoleManager
import android.content.*
import android.content.pm.LauncherApps
import android.os.*
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.launcher.ui.theme.LauncherTheme
import com.example.launcher.data.HomeSetup

class MainActivity : ComponentActivity() {
    private val model: com.example.launcher.fluid.FluidModel by viewModels()
    private var requestHomeOnResume = false
    private var homeRolePending = false
    private val homeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        homeRolePending = false
        syncSetupEntry()
        model.refreshDevice()
        if (!HomeSetup.isDefaultHome(this)) model.message("You can also choose this launcher in Home app settings")
    }
    private val updates = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { model.tick() } }
    private val appsCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = model.refreshApps()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = model.refreshApps()
        override fun onPackageChanged(packageName: String, user: UserHandle) = model.refreshApps()
        override fun onPackagesAvailable(packages: Array<out String>, user: UserHandle, replacing: Boolean) = model.refreshApps()
        override fun onPackagesUnavailable(packages: Array<out String>, user: UserHandle, replacing: Boolean) = model.refreshApps()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeRolePending = savedInstanceState?.getBoolean("homeRolePending") ?: false
        requestHomeOnResume = savedInstanceState == null
        if (savedInstanceState == null && isHomeOrSetupIntent(intent)) {
            model.returnHome()
        }
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { val state by model.state.collectAsStateWithLifecycle(); com.example.launcher.fluid.FluidTheme(state.config) { com.example.launcher.fluid.FluidLauncher(model, ::chooseHome) } }
    }
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, updates, IntentFilter().apply {
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED); addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction("android.net.wifi.SCAN_RESULTS"); addAction("android.intent.action.ANY_DATA_STATE"); addAction("android.net.wifi.STATE_CHANGE"); addAction("android.bluetooth.device.action.BOND_STATE_CHANGED"); addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED"); addAction("android.net.wifi.WIFI_STATE_CHANGED"); addAction("android.media.RINGER_MODE_CHANGED"); addAction("android.bluetooth.adapter.action.STATE_CHANGED")
        }, ContextCompat.RECEIVER_EXPORTED)
        getSystemService(LauncherApps::class.java).registerCallback(appsCallback, Handler(Looper.getMainLooper()))
    }
    override fun onResume() { super.onResume(); syncSetupEntry(); model.tick(); model.refreshApps() }
    override fun onPostResume() {
        super.onPostResume()
        if (requestHomeOnResume) {
            requestHomeOnResume = false
            requestDefaultHomeIfNeeded()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("homeRolePending", homeRolePending)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() { unregisterReceiver(updates); getSystemService(LauncherApps::class.java).unregisterCallback(appsCallback); super.onStop() }
    override fun onPause() { model.pauseSensors(); super.onPause() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (isHomeOrSetupIntent(intent)) {
            model.returnHome()
            requestHomeOnResume = true
        }
    }
    private fun isHomeOrSetupIntent(intent: Intent) = intent.action == Intent.ACTION_MAIN &&
        (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER) || intent.component?.className == "$packageName.Setup")
    private fun syncSetupEntry() { runCatching { HomeSetup.syncSetupEntry(this) } }
    private fun requestDefaultHomeIfNeeded() {
        if (!HomeSetup.isDefaultHome(this)) chooseHome()
    }
    private fun chooseHome() = model.action {
        if (Build.VERSION.SDK_INT >= 29 && !HomeSetup.isDefaultHome(this)) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                if (!homeRolePending) {
                    homeRolePending = true
                    try { homeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                    catch (_: Exception) { homeRolePending = false; openHomeSettings() }
                }
            }
            else openHomeSettings()
        } else openHomeSettings()
    }
    private fun openHomeSettings() {
        for (action in listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)) {
            try { startActivity(Intent(action)); return }
            catch (_: ActivityNotFoundException) { }
            catch (_: SecurityException) { }
        }
        model.message("Open Settings → Apps → Default apps → Home app")
    }
}

