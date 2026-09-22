package com.haven.haven

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Detects Haven "App info" screens so they can be covered by [AppInfoGateActivity].
 * Allows Device Admin activation and Accessibility settings (enable service) screens.
 */
object AppInfoGuard {

    fun isSettingsLikePackage(packageName: String): Boolean {
        val p = packageName.lowercase(Locale.US)
        return p.contains("settings") ||
            p.contains("permissioncontroller") ||
            p.contains("packageinstaller")
    }

    fun isHavenAppInfoScreen(
        context: Context,
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        val packageName = event.packageName?.toString() ?: return false
        if (!isSettingsLikePackage(packageName)) return false

        val className = event.className?.toString()?.lowercase(Locale.US).orEmpty()

        val texts = LinkedHashSet<String>()
        if (root != null) {
            collectTexts(root, texts, 0)
        }
        event.text?.forEach { item ->
            item?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
        }
        val blob = texts.joinToString(" ").lowercase(Locale.US)

        // Permission / setup screens that must stay usable.
        if (isDeviceAdminActivationScreen(className, blob)) return false
        if (isAccessibilitySettingsScreen(className, blob)) return false

        val classLooksLikeAppInfo =
            className.contains("installedappdetails") ||
                className.contains("appinfodetails") ||
                className.contains("applicationdetails") ||
                className.contains("applicationsummary") ||
                className.contains("installedapp") ||
                className.contains("spa.app.appinfo") ||
                // Avoid bare "appinfo" — it can match unrelated Settings classes.
                (className.contains("appinfo") &&
                    !className.contains("accessibility") &&
                    !className.contains("deviceadmin"))

        if (blob.isEmpty() && !classLooksLikeAppInfo) return false

        val mentionsHaven =
            blob.contains("haven") ||
                blob.contains(context.packageName.lowercase(Locale.US))

        val hasAppInfoActions =
            blob.contains("force stop") ||
                blob.contains("uninstall") ||
                blob.contains("clear storage") ||
                blob.contains("storage & cache") ||
                blob.contains("storage and cache") ||
                blob.contains("mobile data usage") ||
                blob.contains("app info") ||
                blob.contains("application info")

        // Real App Info always has typical actions; don't gate on name alone.
        return mentionsHaven && hasAppInfoActions &&
            (classLooksLikeAppInfo || hasAppInfoActions)
    }

    private fun isDeviceAdminActivationScreen(className: String, blob: String): Boolean {
        if (className.contains("deviceadminadd")) return true
        if (className.contains("device_admin_add")) return true

        return blob.contains("activate this device admin") ||
            blob.contains("activate device admin app") ||
            (blob.contains("device admin") &&
                blob.contains("activate") &&
                !blob.contains("deactivate") &&
                !blob.contains("uninstall") &&
                !blob.contains("force stop"))
    }

    private fun isAccessibilitySettingsScreen(className: String, blob: String): Boolean {
        if (className.contains("accessibility")) return true
        if (blob.contains("accessibility")) return true
        if (blob.contains("full control of your device")) return true
        if (blob.contains("restricted setting")) return true
        if (blob.contains("downloaded apps") && blob.contains("services")) return true
        if (blob.contains("installed apps") && blob.contains("services")) return true
        return false
    }

    private fun collectTexts(
        node: AccessibilityNodeInfo,
        out: MutableSet<String>,
        depth: Int,
    ) {
        if (depth > 22) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                collectTexts(child, out, depth + 1)
            } finally {
                try {
                    child.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }
}
