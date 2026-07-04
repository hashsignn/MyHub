package com.contentreg.app.feature4_retention.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.contentreg.app.R

/**
 * Task 2 — the Device Admin receiver for **anti-uninstall friction**.
 *
 * While this admin is active, Android refuses to uninstall the app until it is first deactivated —
 * a deliberate speed bump against a moment-of-weakness deletion. It is **friction, not a wall**: on
 * a normal (non-rooted, unmanaged) device the user can always deactivate here, boot into Safe Mode,
 * use `adb`, or factory-reset. See ADR 0002.
 *
 * The receiver holds no policy (no password/lock enforcement); being an active admin is sufficient
 * to gate uninstall. [onDisableRequested] returns a clear warning the system shows before the user
 * turns protection off.
 */
class ContentRegDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)
}
