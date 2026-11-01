package com.example.launcher.data

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object HomeSetup {
    fun isDefaultHome(context: Context): Boolean {
        val roleHeld = Build.VERSION.SDK_INT >= 29 && runCatching {
            context.getSystemService(RoleManager::class.java).let { it.isRoleAvailable(RoleManager.ROLE_HOME) && it.isRoleHeld(RoleManager.ROLE_HOME) }
        }.getOrDefault(false)
        return roleHeld || runCatching {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName == context.packageName
        }.getOrDefault(false)
    }

    /** Hide only the bootstrap alias. The HOME activity always remains enabled. */
    fun syncSetupEntry(context: Context) {
        val alias = ComponentName(context.packageName, "${context.packageName}.Setup")
        val enabled = !isDefaultHome(context)
        val desired = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val current = context.packageManager.getComponentEnabledSetting(alias)
        if (current != desired && !(enabled && current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)) {
            context.packageManager.setComponentEnabledSetting(alias, desired, PackageManager.DONT_KILL_APP)
        }
    }
}
