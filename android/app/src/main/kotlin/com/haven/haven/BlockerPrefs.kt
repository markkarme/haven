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
    const val KEY_VPN_EXCLUDED = "vpn_excluded_packages"
    private const val KEY_DEACTIVATION_ATTEMPT_AT = "deactivation_attempt_at"
    private const val KEY_LAST_GATE_SHOW_AT = "last_gate_show_at"
    private const val KEY_GATE_USER_DISMISSED_UNTIL = "gate_user_dismissed_until"
    private const val KEY_UNINSTALL_GATE_OPEN = "uninstall_gate_open"
    private const val KEY_GATE_OPEN_AT = "uninstall_gate_open_at"
    private const val REMOVAL_FILE = "removal_attempt"
    private const val GATE_SHOW_COOLDOWN_MS = 150L
    private const val GATE_RESHOW_COOLDOWN_MS = 100L
    private const val GATE_USER_DISMISS_MS = 8_000L
    private const val GATE_OPEN_STALE_MS = 45_000L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Caller must keep a strong reference to [listener]. */
    fun registerListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

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

    /** Password gate for uninstall — only when uninstall protection is enabled. */
    fun shouldGuardAppInfo(context: Context): Boolean =
        isUninstallProtectionEnabled(context)

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

    /** User tapped Go Back / system back on the gate — don't instantly re-show on App info. */
    fun markGateUserDismissed(context: Context, durationMs: Long = GATE_USER_DISMISS_MS) {
        val until = System.currentTimeMillis() + durationMs
        prefs(context).edit().putLong(KEY_GATE_USER_DISMISSED_UNTIL, until).commit()
    }

    fun isGateUserDismissed(context: Context): Boolean {
        val until = prefs(context).getLong(KEY_GATE_USER_DISMISSED_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    fun clearGateUserDismissed(context: Context) {
        prefs(context).edit().remove(KEY_GATE_USER_DISMISSED_UNTIL).apply()
    }

    /** Shared across `:guard` and main — in-memory [isShowing] flags are process-local. */
    fun setUninstallGateOpen(context: Context, open: Boolean) {
        val editor = prefs(context).edit()
        if (open) {
            editor.putLong(KEY_GATE_OPEN_AT, System.currentTimeMillis())
        } else {
            editor.remove(KEY_GATE_OPEN_AT)
        }
        editor.putBoolean(KEY_UNINSTALL_GATE_OPEN, open).commit()
    }

    fun isUninstallGateOpen(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_UNINSTALL_GATE_OPEN, false)) return false
        val openedAt = p.getLong(KEY_GATE_OPEN_AT, 0L)
        if (openedAt > 0L && System.currentTimeMillis() - openedAt > GATE_OPEN_STALE_MS) {
            setUninstallGateOpen(context, false)
            return false
        }
        return true
    }

    /** Cross-process debounce. Uses a shorter cooldown while an uninstall attempt is active. */
    fun tryAcquireUninstallGateShow(context: Context, reshow: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val last = p.getLong(KEY_LAST_GATE_SHOW_AT, 0L)
        val cooldown = if (reshow) GATE_RESHOW_COOLDOWN_MS else GATE_SHOW_COOLDOWN_MS
        if (now - last < cooldown) return false
        return p.edit().putLong(KEY_LAST_GATE_SHOW_AT, now).commit()
    }

    fun clearAppInfoUnlock(context: Context) {
        prefs(context).edit().remove(KEY_APP_INFO_UNLOCK_UNTIL).apply()
    }

    /**
     * Uninstall / Device Admin deactivation is in progress. Written to a file so the
     * `:guard` process sees it even after the main process is swiped away.
     */
    fun markRemovalAttempt(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(KEY_DEACTIVATION_ATTEMPT_AT, now).commit()
        try {
            context.applicationContext.getFileStreamPath(REMOVAL_FILE)
                .writeText(now.toString())
        } catch (_: Exception) {
        }
    }

    fun clearRemovalAttemptIfSet(context: Context) {
        val p = prefs(context)
        if (p.contains(KEY_DEACTIVATION_ATTEMPT_AT)) {
            p.edit().remove(KEY_DEACTIVATION_ATTEMPT_AT).commit()
        }
        try {
            context.applicationContext.getFileStreamPath(REMOVAL_FILE).delete()
        } catch (_: Exception) {
        }
    }

    fun isRemovalAttemptActive(context: Context): Boolean {
        val fromPrefs = prefs(context).getLong(KEY_DEACTIVATION_ATTEMPT_AT, 0L)
        val fromFile = try {
            context.applicationContext.getFileStreamPath(REMOVAL_FILE)
                .takeIf { it.exists() }
                ?.readText()
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
        val at = maxOf(fromPrefs, fromFile)
        return at != 0L && System.currentTimeMillis() - at < 5 * 60 * 1000L
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

    fun setVpnExcludedPackages(context: Context, packages: Collection<String>) {
        val json = JSONArray(packages.toSortedSet().toList()).toString()
        prefs(context).edit().putString(KEY_VPN_EXCLUDED, json).apply()
    }

    /** Apps that bypass the VPN entirely (for apps that refuse to work behind any VPN). */
    fun getVpnExcludedPackages(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_VPN_EXCLUDED, "[]") ?: "[]"
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
