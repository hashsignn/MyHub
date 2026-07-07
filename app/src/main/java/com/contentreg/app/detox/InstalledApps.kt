package com.contentreg.app.detox

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** A launchable app: its package plus a human label. */
data class AppInfo(val packageName: String, val label: String)

/**
 * Lists the user-launchable apps on the device (those with a MAIN/LAUNCHER entry). The manifest's
 * `<queries>` block grants the package visibility this needs on Android 11+.
 */
object InstalledApps {

    /** All launchable apps except [excludePackage] (usually our own), sorted by label. */
    fun launchable(context: Context, excludePackage: String? = null): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        return resolved
            .asSequence()
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .filter { it != excludePackage }
            .map { pkg -> AppInfo(pkg, labelOf(pm, pkg)) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Resolves labels for a specific set of packages (e.g. the detox allow-list), sorted by label. */
    fun infoFor(context: Context, packages: Collection<String>): List<AppInfo> {
        val pm = context.packageManager
        return packages
            .map { pkg -> AppInfo(pkg, labelOf(pm, pkg)) }
            .sortedBy { it.label.lowercase() }
    }

    private fun labelOf(pm: PackageManager, pkg: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)
}
