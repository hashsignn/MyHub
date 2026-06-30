package com.contentreg.app.feature4_retention

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * M4.0 — switches the launcher icon/label by enabling exactly one `<activity-alias>` and disabling
 * the others. Lets the user disguise the app (e.g. as a calculator) or set a meaningful icon so
 * they're reluctant to uninstall it. No special permission required.
 *
 * Exactly one alias is always enabled, so there is never zero (icon disappears) or two launcher
 * entries. The change is applied with DONT_KILL_APP, though some launchers still refresh the icon
 * after a short delay.
 */
enum class AppDisguise(val aliasName: String) {
    DEFAULT(".Default"),
    CALCULATOR(".AliasCalculator"),
    NOTES(".AliasNotes"),
}

object IconAliasController {

    fun apply(context: Context, chosen: AppDisguise) {
        val pm = context.packageManager
        val pkg = context.packageName
        for (disguise in AppDisguise.entries) {
            val component = ComponentName(pkg, pkg + disguise.aliasName)
            val state = if (disguise == chosen) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}
