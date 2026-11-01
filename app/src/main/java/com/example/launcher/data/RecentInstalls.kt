package com.example.launcher.data

const val RECENT_INSTALL_WINDOW = 20 * 60_000L
data class RecentInstallStamp(val component: String, val packageName: String, val installedAt: Long)
fun eligibleRecentInstalls(apps: List<RecentInstallStamp>, now: Long) = apps
    .filter { it.installedAt > 0 && it.installedAt <= now && now - it.installedAt < RECENT_INSTALL_WINDOW }
    .sortedWith(compareByDescending<RecentInstallStamp> { it.installedAt }.thenBy { it.component })
    .distinctBy { it.packageName }
fun recentInstallCandidates(apps: List<InstalledApp>, now: Long): List<InstalledApp> {
    val byComponent = apps.associateBy { it.component.flattenToString() }
    return eligibleRecentInstalls(apps.map { RecentInstallStamp(it.component.flattenToString(), it.component.packageName, it.firstInstalledAt) }, now).mapNotNull { byComponent[it.component] }
}
fun <T> recentInstallWindow(candidates: List<T>, rotation: Int): List<T> {
    if (candidates.size <= 4) return candidates
    val start = ((rotation.toLong() * 4) % candidates.size).toInt()
    return (0..3).map { candidates[(start + it) % candidates.size] }
}
