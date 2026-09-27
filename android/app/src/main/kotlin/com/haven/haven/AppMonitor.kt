package com.haven.haven

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import java.util.Locale

/**
 * Usage Access foreground watcher. Blocks listed apps and shows the password
 * dialog as soon as App info (or an uninstall intent) appears — before the
 * uninstall confirmation wizard is usable.
 */
class AppMonitor(private val context: Context) {

    private val usageStats =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguard =
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private var foregroundPackage: String? = null
    private var foregroundClass: String? = null
    private var lastEventTime = 0L
    private var removalGuardStarted = false
    private var wasOnProtectedScreen = false
    private var lastBringToFrontAt = 0L
    private val tick = object : Runnable {
        override fun run() {
            val delay = try {
                poll()
            } catch (_: Exception) {
                IDLE_MS
            }
            handler?.postDelayed(this, delay)
        }
    }

    fun start() {
        if (thread != null) return
        val t = HandlerThread("haven-app-monitor").also { it.start() }
        thread = t
        handler = Handler(t.looper).also { it.post(tick) }
    }

    fun stop() {
        BlockOverlayWindow.hide()
        handler?.removeCallbacksAndMessages(null)
        handler = null
        thread?.quitSafely()
        thread = null
        removalGuardStarted = false
        wasOnProtectedScreen = false
    }

    private fun poll(): Long {
        val attempt = BlockerPrefs.isRemovalAttemptActive(context)
        if (!power.isInteractive || keyguard.isKeyguardLocked) {
            return IDLE_MS
        }
        if (!ProtectionController.hasUsageAccess(context)) return IDLE_MS

        val now = System.currentTimeMillis()
        ingestUsageEvents(now)

        val activePkg = foregroundPackage ?: return ACTIVE_MS
        val activeCls = foregroundClass.orEmpty()
        val pkgLower = activePkg.lowercase(Locale.US)
        val clsLower = activeCls.lowercase(Locale.US)

        evaluate(activePkg, activeCls)

        val onProtected = shouldGateScreen(pkgLower, clsLower)
        return if (onProtected || (attempt && isRemovalFlowScreen(pkgLower, clsLower))) {
            ACTIVE_MS
        } else {
            IDLE_MS
        }
    }

    private fun ingestUsageEvents(now: Long) {
        val events = usageStats.queryEvents(now - LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            val pkg = event.packageName ?: continue
            val pkgLower = pkg.lowercase(Locale.US)
            val cls = event.className.orEmpty()
            val type = event.eventType

            @Suppress("DEPRECATION")
            val resumed = type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                type == UsageEvents.Event.ACTIVITY_RESUMED

            // Any event from the installer / uninstall UI — catch before ACTIVITY_RESUMED.
            // Do NOT keep sticky "hottest installer" after Cancel; that caused the gate
            // to appear when leaving the wizard instead of before entering it.
            if (isInstallerPackage(pkgLower) || isSystemUninstallUi(pkgLower, cls)) {
                if (event.timeStamp >= lastEventTime) {
                    lastEventTime = event.timeStamp
                    foregroundPackage = pkg
                    foregroundClass = cls.ifEmpty { "UninstallerActivity" }
                }
                continue
            }

            // App info: treat any event (not only RESUMED) so ColorOS reports sooner.
            if (AppInfoGuard.isAppInfoScreenByUsage(pkgLower, cls) ||
                (isAppManagerPackage(pkgLower) && AppInfoGuard.isAppInfoActivityClass(cls))
            ) {
                if (event.timeStamp >= lastEventTime) {
                    lastEventTime = event.timeStamp
                    foregroundPackage = pkg
                    foregroundClass = cls
                }
                continue
            }

            if (resumed && event.timeStamp >= lastEventTime) {
                lastEventTime = event.timeStamp
                foregroundPackage = pkg
                foregroundClass = cls
            }
        }
    }

    private fun evaluate(packageName: String, className: String) {
        val pkg = packageName.lowercase(Locale.US)
        val cls = className.lowercase(Locale.US)
        if (BlockerPrefs.isAppInfoUnlocked(context) &&
            ProtectionController.isUninstallGateShowing(context)
        ) {
            ProtectionController.dismissUninstallGateAfterUnlock(context)
        }

        val wantsGate = shouldGateScreen(pkg, cls)
        val userDismissed = BlockerPrefs.isGateUserDismissed(context)

        // Fresh entry into a removal screen — allow the gate again.
        if (wantsGate && !wasOnProtectedScreen) {
            BlockerPrefs.clearGateUserDismissed(context)
        }

        val onProtected = wantsGate && !BlockerPrefs.isGateUserDismissed(context)

        if (onProtected) {
            BlockerPrefs.markRemovalAttempt(context)
            BlockOverlayWindow.hide()
            if (!removalGuardStarted) {
                removalGuardStarted = true
                ProtectionController.startGuard(context)
                ProtectionController.scheduleRestart(context, 800)
            }
            val entering = !wasOnProtectedScreen
            wasOnProtectedScreen = true

            // Always urgent: Activity must appear over Settings / installer (overlay alone
            // is often hidden under ColorOS system apps until the user leaves).
            ProtectionController.showUninstallGate(
                context,
                reshow = !entering,
                urgent = true,
            )

            // Keep the gate in front while App info / installer is still the target.
            val now = System.currentTimeMillis()
            if (!entering && now - lastBringToFrontAt >= BRING_TO_FRONT_MS) {
                lastBringToFrontAt = now
                AppInfoGateActivity.bringToFront(context)
                // Refresh open marker so a long-lived gate is not treated as abandoned.
                BlockerPrefs.setUninstallGateOpen(context, true)
            }
            return
        }

        if (wantsGate && userDismissed) {
            // Go Back while App info / installer is still in usage stats — keep gate hidden.
            wasOnProtectedScreen = true
            if (!ProtectionController.isUninstallGateShowing(context)) {
                BlockerPrefs.clearRemovalAttemptIfSet(context)
            }
            return
        }

        wasOnProtectedScreen = false
        removalGuardStarted = false
        // Gate stays until password unlock or Go Back — do not auto-hide when leaving App info.
        if (!ProtectionController.isUninstallGateShowing(context)) {
            BlockerPrefs.clearRemovalAttemptIfSet(context)
        }
        if (!isDangerPackage(pkg)) {
            BlockerPrefs.clearAppInfoUnlock(context)
        }

        when {
            packageName == context.packageName -> BlockOverlayWindow.hide()
            BlockerPrefs.isPackageBlocked(context, packageName) ->
                BlockOverlayWindow.show(context)
            else -> BlockOverlayWindow.hide()
        }
    }

    private fun isInstallerPackage(pkg: String): Boolean =
        pkg == "com.android.packageinstaller" ||
            pkg == "com.google.android.packageinstaller" ||
            pkg.contains("packageinstaller") ||
            pkg.contains("backupconfirm") ||
            pkg.contains("coloros.packageinstaller") ||
            pkg.contains("oppo.packageinstaller") ||
            pkg.contains("oplus.packageinstaller") ||
            pkg.contains("realme.packageinstaller") ||
            pkg.contains("miui.packageinstaller") ||
            pkg.contains("vendor.packageinstaller")

    /** Uninstall UI hosted in System UI or the launcher (home-screen uninstall). */
    private fun isSystemUninstallUi(pkg: String, cls: String): Boolean {
        if (!isUninstallClass(cls)) return false
        return pkg.contains("systemui") ||
            isLauncherPackage(pkg) ||
            pkg.contains("packageinstaller")
    }

    private fun isLauncherPackage(pkg: String): Boolean =
        pkg.contains("launcher") ||
            pkg.contains("quickstep") ||
            pkg.contains("coloros.launcher") ||
            pkg.contains("oplus.launcher") ||
            pkg.contains("realme.launcher") ||
            pkg.contains("breeno") ||
            pkg.contains("miui.home")

    private fun isRecentsPackage(pkg: String): Boolean =
        pkg.contains("systemui") ||
            pkg.contains("recenttask")

    private fun isAppManagerPackage(pkg: String): Boolean =
        pkg.contains("phonemanager") ||
            pkg.contains("appmanager") ||
            pkg.contains("applicationmanager") ||
            pkg.contains("securitypermission") ||
            pkg.contains("safecenter") ||
            pkg.contains("appdetail")

    private fun isSettingsLikePackage(pkg: String): Boolean =
        pkg.contains("settings") ||
            pkg.contains("securitycenter") ||
            pkg.contains("permissioncontroller") ||
            pkg.contains("coloros") ||
            pkg.contains("oplus") ||
            pkg.contains("realme") ||
            pkg.contains("heytap")

    private fun isDangerPackage(pkg: String): Boolean =
        isInstallerPackage(pkg) ||
            isAppManagerPackage(pkg) ||
            isSettingsLikePackage(pkg) ||
            isLauncherPackage(pkg) ||
            isRecentsPackage(pkg) ||
            pkg == context.packageName.lowercase(Locale.US)

    private fun isUninstallClass(cls: String): Boolean {
        val c = cls.lowercase(Locale.US)
        return c.contains("uninstall") ||
            c.contains("uninstaller") ||
            c.contains("deleteapp") ||
            c.contains("removeapp") ||
            c.contains("appdelete") ||
            c.contains("packagedelete") ||
            (c.contains("dialog") && (
                c.contains("delete") || c.contains("remove") || c.contains("uninstall")
                ))
    }

    /** Installer, App info activity, uninstall dialogs — not whole Settings / launcher home. */
    private fun isRemovalFlowScreen(pkg: String, cls: String): Boolean {
        if (isInstallerPackage(pkg)) return true
        if (AppInfoGuard.isAppInfoScreenByUsage(pkg, cls)) return true
        if (isAppManagerPackage(pkg) && AppInfoGuard.isAppInfoActivityClass(cls)) return true
        if (isLauncherPackage(pkg) && isUninstallClass(cls)) return true
        if (isRecentsPackage(pkg) && isUninstallClass(cls)) return true
        return false
    }

    /** Gate uninstall paths unless the user entered the password (5-minute unlock window). */
    private fun shouldGateScreen(pkg: String, cls: String): Boolean {
        if (!BlockerPrefs.shouldGuardAppInfo(context)) return false
        if (BlockerPrefs.isAppInfoUnlocked(context)) {
            return isDeviceAdminGateScreen(pkg, cls)
        }
        // Package Installer = uninstall wizard. Gate it so the password appears instead
        // of (and before interacting with) the confirmation UI.
        if (isInstallerPackage(pkg)) return true
        return isProtectedSystemScreen(pkg, cls)
    }

    private fun isDeviceAdminGateScreen(pkg: String, cls: String): Boolean {
        if (!ProtectionController.isDeviceAdminActive(context)) return false
        if (BlockerPrefs.isDeviceAdminFlowAllowed(context)) return false
        if (isAppManagerPackage(pkg) || pkg.contains("securitypermission")) return true
        if (isSettingsLikePackage(pkg) && (
                cls.contains("deviceadmin") ||
                    cls.contains("device_admin") ||
                    cls.contains("devicepolicy") ||
                    cls.contains("administrator")
                )
        ) {
            return true
        }
        return cls.contains("deviceadminadd")
    }

    /** App info (Settings / Phone Manager) and explicit uninstall UIs — not launcher menus. */
    private fun isProtectedSystemScreen(pkg: String, cls: String): Boolean {
        if (isRemovalFlowScreen(pkg, cls)) return true

        if (pkg.contains("systemui") && isUninstallClass(cls)) return true
        if (isRecentsPackage(pkg) && isUninstallClass(cls)) return true

        return isDeviceAdminGateScreen(pkg, cls)
    }

    companion object {
        private const val LOOKBACK_MS = 8_000L
        private const val ACTIVE_MS = 25L
        private const val IDLE_MS = 400L
        private const val BRING_TO_FRONT_MS = 700L
    }
}
