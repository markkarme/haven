package com.haven.haven

import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.Application
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object ProtectionController {
    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, HavenDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(component)
    }

    fun requestDeviceAdmin(context: Context) {
        val component = ComponentName(context, HavenDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.device_admin_explanation),
            )
        }
        startUiIntent(context, intent)
    }

    /** Password screen for uninstall / App info / Device Admin. Does not lock the phone. */
    fun showUninstallGate(
        context: Context,
        reshow: Boolean = false,
        urgent: Boolean = false,
    ) {
        val app = context.applicationContext
        if (BlockerPrefs.isGateUserDismissed(app)) return
        if (isUninstallGateShowing(app)) return
        startGuard(app)
        try {
            // Overlay is process-local — `:guard` always uses the Activity in the main process.
            if (!isGuardProcess(app) && canDrawOverlays(app)) {
                UninstallGateOverlay.show(app, reshow)
            } else {
                AppInfoGateActivity.show(app, reshow, urgent = urgent)
            }
        } catch (_: Exception) {
        }
    }

    fun hideUninstallGate(context: Context) {
        val app = context.applicationContext
        BlockerPrefs.setUninstallGateOpen(app, false)
        UninstallGateOverlay.hide()
        AppInfoGateActivity.requestDismiss(app)
        AppInfoGateActivity.cancelLaunchNotification(app)
    }

    fun isUninstallGateShowing(context: Context): Boolean {
        val app = context.applicationContext
        return UninstallGateOverlay.isShowing ||
            AppInfoGateActivity.isShowing ||
            BlockerPrefs.isUninstallGateOpen(app)
    }

    fun goHome(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: Exception) {
        }
    }

    /** Go Back: close the gate, suppress re-show, and send the user home. */
    fun retreatFromAppInfoGate(context: Context) {
        val app = context.applicationContext
        BlockerPrefs.markGateUserDismissed(app)
        BlockerPrefs.clearRemovalAttemptIfSet(app)
        BlockerPrefs.setUninstallGateOpen(app, false)
        hideUninstallGate(app)
        goHome(app)
    }

    private fun isGuardProcess(context: Context): Boolean =
        processName(context)?.endsWith(":guard") == true

    private fun processName(context: Context): String? {
        if (Build.VERSION.SDK_INT >= 28) {
            return Application.getProcessName()
        }
        val pid = Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
    }

    /** Only a correct password (or turning off protection) may remove the gate. */
    fun dismissUninstallGateAfterUnlock(context: Context) {
        val app = context.applicationContext
        BlockerPrefs.clearRemovalAttemptIfSet(app)
        hideUninstallGate(app)
    }

    /** Password-protected exit path: Haven removes its own admin so uninstall works normally. */
    fun removeDeviceAdmin(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, HavenDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(component)) dpm.removeActiveAdmin(component)
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        }
        startUiIntent(context, intent)
    }

    private fun startUiIntent(context: Context, intent: Intent) {
        if (context is Activity) {
            context.startActivity(intent)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun startProtection(context: Context) {
        BlockerPrefs.setProtectionEnabled(context, true)
        HavenVpnService.start(context)
        startGuard(context)
        scheduleRestart(context)
    }

    fun stopProtection(context: Context) {
        BlockerPrefs.setProtectionEnabled(context, false)
        if (!BlockerPrefs.shouldGuardAppInfo(context)) {
            cancelRestart(context)
            ProtectionService.stop(context)
        }
        HavenVpnService.stop(context)
    }

    fun restoreAfterBoot(context: Context) {
        if (BlockerPrefs.isProtectionEnabled(context)) {
            HavenVpnService.start(context)
        }
        if (BlockerPrefs.isProtectionEnabled(context) || BlockerPrefs.shouldGuardAppInfo(context)) {
            startGuard(context)
            scheduleRestart(context)
        }
    }

    fun startGuard(context: Context) {
        if (!BlockerPrefs.isProtectionEnabled(context) && !BlockerPrefs.shouldGuardAppInfo(context)) {
            return
        }
        ProtectionService.start(context.applicationContext)
    }

    fun scheduleRestart(context: Context, delayMs: Long = RESTART_INTERVAL_MS) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = restartPending(app)
        val at = SystemClock.elapsedRealtime() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        } catch (_: Exception) {
        }
    }

    fun cancelRestart(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(restartPending(context.applicationContext))
    }

    private fun restartPending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            RESTART_REQUEST,
            Intent(context, BootAndRestartReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            startUiIntent(context, intent)
        } catch (_: Exception) {
            // Some OEMs reject the package-scoped variant.
            startUiIntent(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    /** Needed to show block screens from the background on Android 10+. */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openOverlaySettings(context: Context) {
        startUiIntent(
            context,
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    fun openVpnSettings(context: Context) {
        startUiIntent(context, Intent(Settings.ACTION_VPN_SETTINGS))
    }

    /** Strict Private DNS (a hostname) sends lookups past the local VPN filter. */
    fun isPrivateDnsBypassing(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return try {
            @Suppress("DEPRECATION")
            cm.allNetworks.any { network ->
                !cm.getLinkProperties(network)?.privateDnsServerName.isNullOrEmpty()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun protectionStatusMap(context: Context): Map<String, Any> {
        val vpnRunning = HavenVpnService.running
        return mapOf(
            "isActive" to (BlockerPrefs.isProtectionEnabled(context) && vpnRunning),
            "vpnEnabled" to HavenVpnService.hasPermission(context),
            "vpnRunning" to vpnRunning,
            "privateDnsBypass" to isPrivateDnsBypassing(context),
            "usageAccessEnabled" to hasUsageAccess(context),
            "overlayEnabled" to canDrawOverlays(context),
            "deviceAdminEnabled" to isDeviceAdminActive(context),
            "notificationEnabled" to areNotificationsEnabled(context),
        )
    }

    private const val RESTART_INTERVAL_MS = 8_000L
    private const val RESTART_REQUEST = 3101
}
