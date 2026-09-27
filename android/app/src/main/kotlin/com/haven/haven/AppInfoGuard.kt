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
            p.contains("appdetail")
    }

    fun isAppInfoActivityClass(className: String): Boolean {
        val cls = className.lowercase(Locale.US)
        return cls.contains("installedappdetails") ||
            cls.contains("appinfodetails") ||
            cls.contains("applicationdetails") ||
            cls.contains("applicationsummary") ||
            cls.contains("appdetailsactivity") ||
            cls.contains("modularityappdetails") ||
            cls.contains("spa.app.appinfo") ||
            cls.contains("oplus.settings.feature.application") ||
            cls.contains("subsettings") ||
            (cls.contains("appinfo") &&
                !cls.contains("accessibility") &&
                !cls.contains("deviceadmin"))
    }

    fun isAppInfoScreenByUsage(packageName: String, className: String): Boolean {
        if (!isSettingsLikePackage(packageName)) return false
        return isAppInfoActivityClass(className)
    }
}
