package com.haven.haven

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "com.haven.blocker/native"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                try {
                    when (call.method) {
                        "getInstalledApps" -> {
                            Thread {
                                try {
                                    val apps =
                                        InstalledAppsHelper.listLaunchableApps(applicationContext)
                                    runOnUiThread { result.success(apps) }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        result.error("apps_error", e.message, null)
                                    }
                                }
                            }.start()
                        }
                        "getAppIcon" -> {
                            val packageName = call.argument<String>("packageName").orEmpty()
                            Thread {
                                try {
                                    val icon = InstalledAppsHelper.iconBase64(
                                        applicationContext,
                                        packageName,
                                    )
                                    runOnUiThread { result.success(icon) }
                                } catch (_: Exception) {
                                    runOnUiThread { result.success(null) }
                                }
                            }.start()
                        }
                        "getAccessibilityStatus" -> {
                            result.success(ProtectionController.isAccessibilityEnabled(this))
                        }
                        "requestAccessibility" -> {
                            ProtectionController.openAccessibilitySettings(this)
                            result.success(null)
                        }
                        "getVpnStatus" -> result.success(false)
                        "requestVpnPermission" -> result.success(false)
                        "getDeviceAdminStatus" -> {
                            result.success(ProtectionController.isDeviceAdminActive(this))
                        }
                        "requestDeviceAdmin" -> {
                            ProtectionController.requestDeviceAdmin(this)
                            result.success(null)
                        }
                        "getNotificationStatus" -> {
                            result.success(ProtectionController.areNotificationsEnabled(this))
                        }
                        "requestNotificationPermission" -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        this,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    ActivityCompat.requestPermissions(
                                        this,
                                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                        REQUEST_NOTIFICATIONS,
                                    )
                                }
                            } else {
                                ProtectionController.openNotificationSettings(this)
                            }
                            result.success(null)
                        }
                        "getProtectionStatus" -> {
                            result.success(ProtectionController.protectionStatusMap(this))
                        }
                        "syncRules" -> {
                            val domains = call.argument<List<String>>("domains") ?: emptyList()
                            val packages = call.argument<List<String>>("packages") ?: emptyList()
                            val protectionEnabled =
                                call.argument<Boolean>("protectionEnabled") ?: false
                            val adult =
                                call.argument<Boolean>("adultProtectionEnabled") ?: true
                            val uninstall =
                                call.argument<Boolean>("uninstallProtectionEnabled") ?: false
                            BlockerPrefs.setBlockedDomains(this, domains)
                            BlockerPrefs.setBlockedPackages(this, packages)
                            BlockerPrefs.setProtectionEnabled(this, protectionEnabled)
                            BlockerPrefs.setAdultProtectionEnabled(this, adult)
                            BlockerPrefs.setUninstallProtectionEnabled(this, uninstall)
                            result.success(null)
                        }
                        "startProtection" -> {
                            ProtectionController.startProtection(this)
                            result.success(null)
                        }
                        "stopProtection" -> {
                            ProtectionController.stopProtection(this)
                            result.success(null)
                        }
                        else -> result.notImplemented()
                    }
                } catch (e: Exception) {
                    result.error("haven_error", e.message, null)
                }
            }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1002
    }
}
