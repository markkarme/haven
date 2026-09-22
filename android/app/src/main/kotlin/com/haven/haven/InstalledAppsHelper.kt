package com.haven.haven

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream

object InstalledAppsHelper {
    fun listLaunchableApps(context: Context): List<Map<String, Any>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL
        } else {
            0
        }

        @Suppress("DEPRECATION")
        val resolveInfos = try {
            pm.queryIntentActivities(intent, flags)
        } catch (_: Exception) {
            emptyList()
        }

        val blocked = BlockerPrefs.getBlockedPackages(context)
        val self = context.packageName
        // Keep the best human-readable label per package.
        val bestByPackage = LinkedHashMap<String, String>()

        for (info in resolveInfos) {
            val packageName = info.activityInfo?.packageName ?: continue
            if (packageName == self) continue

            val label = resolveAppLabel(pm, info, packageName)
            val existing = bestByPackage[packageName]
            if (existing == null ||
                (looksLikePackageName(existing) && !looksLikePackageName(label))
            ) {
                bestByPackage[packageName] = label
            }
        }

        return bestByPackage.entries
            .map { (packageName, appName) ->
                mapOf(
                    "packageName" to packageName,
                    "appName" to appName,
                    "isBlocked" to blocked.contains(packageName),
                )
            }
            .sortedBy { (it["appName"] as String).lowercase() }
    }

    private fun resolveAppLabel(
        pm: PackageManager,
        resolveInfo: ResolveInfo,
        packageName: String,
    ): String {
        val candidates = ArrayList<String>(4)

        fun add(value: CharSequence?) {
            val text = value?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) candidates.add(text)
        }

        try {
            add(resolveInfo.loadLabel(pm))
        } catch (_: Exception) {
        }
        try {
            add(resolveInfo.activityInfo?.loadLabel(pm))
        } catch (_: Exception) {
        }
        try {
            val appInfo = getApplicationInfo(pm, packageName)
            add(appInfo.loadLabel(pm))
            add(pm.getApplicationLabel(appInfo))
        } catch (_: Exception) {
        }

        return candidates.firstOrNull { !looksLikePackageName(it) }
            ?: candidates.firstOrNull()
            ?: packageName
    }

    private fun getApplicationInfo(pm: PackageManager, packageName: String): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
    }

    private fun looksLikePackageName(value: String): Boolean {
        return value.matches(
            Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"),
        )
    }

    fun iconBase64(context: Context, packageName: String): String? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBase64(drawable)
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBase64(drawable: Drawable): String? {
        val size = 48
        val bitmap = when (drawable) {
            is BitmapDrawable -> {
                val src = drawable.bitmap ?: return null
                Bitmap.createScaledBitmap(src, size, size, true)
            }
            else -> {
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bmp
            }
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 55, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
