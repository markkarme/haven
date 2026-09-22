package com.haven.haven

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat

object ProtectionController {
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AppBlockAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next())
            if (component != null && component == expected) return true
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        startUiIntent(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

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
    }

    fun stopProtection(context: Context) {
        BlockerPrefs.setProtectionEnabled(context, false)
    }

    fun restoreAfterBoot(context: Context) {
        // Prefs + AccessibilityService are enough; no VPN to restart.
    }

    fun protectionStatusMap(context: Context): Map<String, Any> {
        val accessibility = isAccessibilityEnabled(context)
        return mapOf(
            "isActive" to (BlockerPrefs.isProtectionEnabled(context) && accessibility),
            "accessibilityEnabled" to accessibility,
            "vpnEnabled" to false,
            "deviceAdminEnabled" to isDeviceAdminActive(context),
            "notificationEnabled" to areNotificationsEnabled(context),
            "vpnRunning" to false,
        )
    }
}
