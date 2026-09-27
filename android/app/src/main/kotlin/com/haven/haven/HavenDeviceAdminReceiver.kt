package com.haven.haven

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class HavenDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        BlockerPrefs.setUninstallProtectionEnabled(context, true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        BlockerPrefs.setUninstallProtectionEnabled(context, false)
    }

    /** Deactivation is the step before uninstall. Show the password screen — do not lock. */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        if (BlockerPrefs.shouldGuardAppInfo(context) && !BlockerPrefs.isAppInfoUnlocked(context)) {
            BlockerPrefs.markRemovalAttempt(context)
            ProtectionController.showUninstallGate(context)
            ProtectionController.startGuard(context)
            ProtectionController.scheduleRestart(context, 800)
        }
        return context.getString(R.string.device_admin_disable_warning)
    }
}
