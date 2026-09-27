package com.haven.haven

import java.util.Locale

/** Detects App info screens via Usage Access (package + activity class). */
object AppInfoGuard {

    fun isSettingsLikePackage(packageName: String): Boolean {
        val p = packageName.lowercase(Locale.US)
        return p.contains("settings") ||
            p.contains("hihonor") ||
            p.contains("honor") ||
            p.contains("huawe") ||
            p.contains("systemmanager") ||
            p.contains("securitycenter") ||
            p.contains("securitypermission") ||
            p.contains("phonemanager") ||
            p.contains("safecenter") ||
            p.contains("coloros") ||
            p.contains("oplus") ||
            p.contains("realme") ||
            p.contains("heytap") ||
            p.contains("permissioncontroller") ||
            p.contains("packageinstaller") ||
            p.contains("appdetail") ||
            p.contains("appmanager")
    }

    fun isAppInfoActivityClass(className: String): Boolean {
        val cls = className.lowercase(Locale.US)
        if (cls.isEmpty()) return false
        // Explicit App info / application details screens (AOSP + OEM).
        if (cls.contains("installedappdetails") ||
            cls.contains("appinfodetails") ||
            cls.contains("applicationdetails") ||
            cls.contains("applicationsummary") ||
            cls.contains("appdetailsactivity") ||
            cls.contains("appdetail") ||
            cls.contains("modularityappdetails") ||
            cls.contains("spa.app.appinfo") ||
            cls.contains("oplus.settings.feature.application") ||
            cls.contains("applicationsettings") ||
            cls.contains("installedapp") ||
            cls.contains("manageapplications") ||
            cls.contains("applicationinfo") ||
            cls.contains("appinfodetail") ||
            cls.contains("subsettings")
        ) {
            return true
        }
        // ColorOS / Oppo often use generic Settings hosts with an "app" path.
        if (cls.contains("appinfo") &&
            !cls.contains("accessibility") &&
            !cls.contains("deviceadmin")
        ) {
            return true
        }
        if (cls.contains(".applications.") &&
            (cls.contains("detail") || cls.contains("info") || cls.contains("app"))
        ) {
            return true
        }
        return false
    }

    fun isAppInfoScreenByUsage(packageName: String, className: String): Boolean {
        if (!isSettingsLikePackage(packageName)) return false
        return isAppInfoActivityClass(className)
    }
}
