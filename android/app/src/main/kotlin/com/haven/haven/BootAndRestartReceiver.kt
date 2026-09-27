package com.haven.haven

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts [ProtectionService] after boot, package replace, or an alarm fired when the
 * service was swiped away from Recents or killed by the OS.
 */
class BootAndRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        handleRestart(context.applicationContext)
    }

    companion object {
        fun handleRestart(app: Context) {
            if (BlockerPrefs.isRemovalAttemptActive(app) &&
                BlockerPrefs.shouldGuardAppInfo(app) &&
                !BlockerPrefs.isAppInfoUnlocked(app)
            ) {
                ProtectionController.showUninstallGate(app)
            }

            ProtectionController.restoreAfterBoot(app)

            if (BlockerPrefs.isProtectionEnabled(app) || BlockerPrefs.shouldGuardAppInfo(app)) {
                ProtectionService.start(app)
                ProtectionController.scheduleRestart(app)
            }
        }
    }
}
