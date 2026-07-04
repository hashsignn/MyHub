package com.contentreg.app.feature4_retention.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.contentreg.app.R

/**
 * Task 2 — thin wrapper over [DevicePolicyManager] for the optional uninstall-protection admin.
 *
 * These calls touch the framework and are verified on-device; the pure activate/deactivate/level
 * decisions live in [UninstallProtection] and are unit-tested there.
 */
object AdminController {

    private fun component(context: Context): ComponentName =
        ComponentName(context, ContentRegDeviceAdminReceiver::class.java)

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    /** True when our device-admin is currently active (so uninstall is blocked). */
    fun isActive(context: Context): Boolean = dpm(context).isAdminActive(component(context))

    /**
     * The system intent that asks the user to activate our admin. The caller starts it (result is
     * ignored — state is re-read via [isActive] on resume). Carries a plain-language explanation.
     */
    fun buildActivationIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.admin_add_explanation))
        }

    /**
     * Turns protection off from within the app (does not trigger [ContentRegDeviceAdminReceiver
     * .onDisableRequested], which the system shows only for its own deactivation UI). Safe to call
     * when inactive. Re-activation later works normally via [buildActivationIntent].
     */
    fun deactivate(context: Context) {
        val component = component(context)
        val manager = dpm(context)
        if (manager.isAdminActive(component)) manager.removeActiveAdmin(component)
    }
}
