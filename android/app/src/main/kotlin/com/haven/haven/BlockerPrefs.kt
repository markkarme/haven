package com.haven.haven

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** Local prefs read by Accessibility / VPN without the Flutter engine. */
object BlockerPrefs {
    private const val NAME = "haven_blocker_prefs"
    private const val KEY_DOMAINS = "blocked_domains"
    private const val KEY_PACKAGES = "blocked_packages"
    private const val KEY_PROTECTION = "protection_enabled"
    private const val KEY_ADULT = "adult_protection_enabled"
    private const val KEY_UNINSTALL = "uninstall_protection_enabled"
    private const val KEY_APP_INFO_UNLOCK_UNTIL = "app_info_unlock_until"
    private const val KEY_DEVICE_ADMIN_FLOW_UNTIL = "device_admin_flow_until"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun setProtectionEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROTECTION, enabled).apply()
    }

    fun isProtectionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROTECTION, false)

    fun setAdultProtectionEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADULT, enabled).apply()
    }

    fun isAdultProtectionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ADULT, true)

    fun setUninstallProtectionEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_UNINSTALL, enabled).apply()
    }

    fun isUninstallProtectionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_UNINSTALL, false)

    /** Guard App Info / uninstall entry points whenever protection is on. */
    fun shouldGuardAppInfo(context: Context): Boolean =
        isProtectionEnabled(context) || isUninstallProtectionEnabled(context)

    fun unlockAppInfo(context: Context, durationMs: Long = UnlockSecrets.APP_INFO_UNLOCK_MS) {
        val until = System.currentTimeMillis() + durationMs
        prefs(context).edit().putLong(KEY_APP_INFO_UNLOCK_UNTIL, until).apply()
    }

    fun isAppInfoUnlocked(context: Context): Boolean {
        val until = prefs(context).getLong(KEY_APP_INFO_UNLOCK_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    /** Temporarily allow Settings flows (Device Admin activation) without App Info gate. */
    fun allowDeviceAdminFlow(context: Context, durationMs: Long = 3 * 60 * 1000L) {
        val until = System.currentTimeMillis() + durationMs
        prefs(context).edit().putLong(KEY_DEVICE_ADMIN_FLOW_UNTIL, until).apply()
    }

    fun isDeviceAdminFlowAllowed(context: Context): Boolean {
        val until = prefs(context).getLong(KEY_DEVICE_ADMIN_FLOW_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    fun setBlockedDomains(context: Context, domains: Collection<String>) {
        val json = JSONArray(domains.map { it.lowercase() }).toString()
        prefs(context).edit().putString(KEY_DOMAINS, json).apply()
    }

    fun getBlockedDomains(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_DOMAINS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i).lowercase())
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setBlockedPackages(context: Context, packages: Collection<String>) {
        val json = JSONArray(packages.toList()).toString()
        prefs(context).edit().putString(KEY_PACKAGES, json).apply()
    }

    fun getBlockedPackages(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_PACKAGES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i))
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun isPackageBlocked(context: Context, packageName: String): Boolean {
        if (!isProtectionEnabled(context)) return false
        return getBlockedPackages(context).contains(packageName)
    }
}
