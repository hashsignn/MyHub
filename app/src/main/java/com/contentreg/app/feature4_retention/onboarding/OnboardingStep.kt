package com.contentreg.app.feature4_retention.onboarding

import android.content.Context
import com.contentreg.app.R
import com.contentreg.app.core.permissions.PermissionRouter
import com.contentreg.app.feature4_retention.admin.AdminController

/**
 * M4.1 — the permissions/steps the app offers, in the order we present them. The app is useless
 * until Accessibility + Overlay are granted; the VPN (URL filter) and the Device-Admin uninstall
 * protection (Task 2) are both optional.
 */
enum class OnboardingStep(
    val titleRes: Int,
    val descRes: Int,
    val required: Boolean,
) {
    ACCESSIBILITY(R.string.onb_a11y_title, R.string.onb_a11y_desc, required = true),
    OVERLAY(R.string.onb_overlay_title, R.string.onb_overlay_desc, required = true),
    VPN(R.string.onb_vpn_title, R.string.onb_vpn_desc, required = false),
    ADMIN(R.string.onb_admin_title, R.string.onb_admin_desc, required = false);

    fun isGranted(context: Context): Boolean = when (this) {
        ACCESSIBILITY -> PermissionRouter.isAccessibilityServiceEnabled(context)
        OVERLAY -> PermissionRouter.canDrawOverlays(context)
        VPN -> PermissionRouter.prepareVpn(context) == null
        ADMIN -> AdminController.isActive(context)
    }
}
