package com.haven.haven

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Blocks apps / websites, and covers Haven App Info so uninstall isn't reachable
 * without the unlock password.
 */
class AppBlockAccessibilityService : AccessibilityService() {

    private var lastAppBlockAt = 0L
    private var lastSiteBlockAt = 0L
    private var lastAppInfoGateAt = 0L
    private var lastBlockedHost: String? = null

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        // 0) Block Haven App Info (Settings / App management) — cover with our dialog.
        // No GLOBAL_ACTION_BACK (that caused flicker before).
        if (BlockerPrefs.shouldGuardAppInfo(this) &&
            !BlockerPrefs.isAppInfoUnlocked(this) &&
            !AppInfoGateActivity.isShowing &&
            AppInfoGuard.isSettingsLikePackage(packageName)
        ) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAppInfoGateAt >= 1500) {
                val root = rootInActiveWindow
                try {
                    if (AppInfoGuard.isHavenAppInfoScreen(this, event, root)) {
                        lastAppInfoGateAt = now
                        AppInfoGateActivity.show(this)
                        return
                    }
                } finally {
                    try {
                        root?.recycle()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        if (!BlockerPrefs.isProtectionEnabled(this)) return
        if (packageName == "com.android.systemui") return

        // 1) Blocked apps
        if (BlockerPrefs.isPackageBlocked(this, packageName)) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAppBlockAt < 700) return
            lastAppBlockAt = now
            showBlockOverlay(
                reason = BlockOverlayActivity.REASON_APP,
                detail = packageName,
                browserPackage = null,
            )
            return
        }

        // 2) Blocked websites inside browsers
        if (!BrowserUrlInspector.isBrowserPackage(packageName)) return

        // Strict mode: block only after navigation/commit events (Enter/Go/open),
        // never while typing, suggestions, or raw click rows.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastSiteBlockAt < 500) return

        val root = rootInActiveWindow ?: return
        try {
            val rules = BlockerPrefs.getBlockedDomains(this)
            if (BlockerPrefs.isAdultProtectionEnabled(this)) {
                val visibleKeyword = BrowserUrlInspector.findBlockedAdultKeywordInVisibleContent(root)
                if (visibleKeyword != null) {
                    if (visibleKeyword == lastBlockedHost && now - lastSiteBlockAt < 2500) return
                    lastBlockedHost = visibleKeyword
                    lastSiteBlockAt = now

                    BrowserNavigator.resetToHome(this, packageName)
                    showBlockOverlay(
                        reason = BlockOverlayActivity.REASON_SITE,
                        detail = visibleKeyword,
                        browserPackage = packageName,
                    )
                    return
                }
            }

            val blockedDomainInResults =
                BrowserUrlInspector.findBlockedDomainInVisibleSearchResults(root, rules)
            if (blockedDomainInResults != null) {
                if (blockedDomainInResults == lastBlockedHost && now - lastSiteBlockAt < 2500) return
                lastBlockedHost = blockedDomainInResults
                lastSiteBlockAt = now

                BrowserNavigator.resetToHome(this, packageName)
                showBlockOverlay(
                    reason = BlockOverlayActivity.REASON_SITE,
                    detail = blockedDomainInResults,
                    browserPackage = packageName,
                )
                return
            }

            val blockedHost = BrowserUrlInspector.findBlockedHostAfterEnter(root, rules) ?: return
            if (blockedHost == lastBlockedHost && now - lastSiteBlockAt < 2500) return
            lastBlockedHost = blockedHost
            lastSiteBlockAt = now

            BrowserNavigator.resetToHome(this, packageName)
            showBlockOverlay(
                reason = BlockOverlayActivity.REASON_SITE,
                detail = blockedHost,
                browserPackage = packageName,
            )
        } finally {
            try {
                root.recycle()
            } catch (_: Exception) {
            }
        }
    }

    private fun showBlockOverlay(
        reason: String,
        detail: String,
        browserPackage: String?,
    ) {
        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
            putExtra(BlockOverlayActivity.EXTRA_REASON, reason)
            putExtra(BlockOverlayActivity.EXTRA_DETAIL, detail)
            if (!browserPackage.isNullOrBlank()) {
                putExtra(BlockOverlayActivity.EXTRA_BROWSER_PACKAGE, browserPackage)
            }
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // No-op
    }
}
