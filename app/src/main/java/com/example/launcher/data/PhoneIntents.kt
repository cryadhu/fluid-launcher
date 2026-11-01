package com.example.launcher.data

import android.content.Intent
import android.net.Uri
import android.content.ComponentName
import android.content.pm.PackageManager

internal fun callingActivityScore(name: String): Int {
    val value = name.lowercase()
    if (listOf("messag", "sms", "mms", "conversation", "compose", "creategroup", "shortcut").any { it in value }) return -1
    return when { "dialer" in value || "dialpad" in value -> 100; "truecallerinit" in value -> 80; "phone" in value || "calling" in value -> 60; else -> 10 }
}

/** Bind the telephone handler explicitly so a messaging activity cannot win resolution. */
@Suppress("DEPRECATION")
internal fun resolveTruecallerCallingIntent(manager: PackageManager, request: Intent): Intent {
    val intent = Intent(request).setPackage("com.truecaller").setComponent(null)
    val matches = manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .map { it.activityInfo }.filter { it.exported && it.enabled && it.packageName == "com.truecaller" }
    val target = matches.filter { callingActivityScore(it.name) >= 0 && (it.targetActivity == null || callingActivityScore(it.targetActivity) >= 0) }.maxByOrNull { callingActivityScore(it.name) }
    if (target != null) intent.component = ComponentName(target.packageName, target.name)
    else if (matches.isNotEmpty()) throw android.content.ActivityNotFoundException("Truecaller has no calling entry point")
    if (intent.data?.schemeSpecificPart.isNullOrBlank()) {
        // Open a fresh calling UI rather than resurfacing the existing SMS task.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    return intent
}

/** Request the calling UI explicitly, rather than restoring Truecaller's last launcher tab. */
internal fun truecallerDialIntent(number: String = ""): Intent = Intent(Intent.ACTION_DIAL).apply {
    setPackage("com.truecaller")
    addCategory(Intent.CATEGORY_DEFAULT)
    // Keep the telephone scheme even for an empty dialpad. A scheme-less DIAL
    // can match Truecaller's messaging entry point on some versions.
    data = if (number.isBlank()) Uri.parse("tel:") else Uri.fromParts("tel", number, null)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
