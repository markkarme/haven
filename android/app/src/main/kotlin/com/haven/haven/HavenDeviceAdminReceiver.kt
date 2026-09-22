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
}
