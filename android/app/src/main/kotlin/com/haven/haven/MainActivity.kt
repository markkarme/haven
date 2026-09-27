package com.haven.haven

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "com.haven.blocker/native"
    private var pendingVpnResult: MethodChannel.Result? = null

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
                        "getVpnStatus" -> result.success(HavenVpnService.hasPermission(this))
                        "requestVpnPermission" -> requestVpnPermission(result)
                        "getVpnExcludedApps" -> {
                            result.success(BlockerPrefs.getVpnExcludedPackages(this).toList())
                        }
                        "setVpnExcludedApps" -> {
                            val packages =
                                call.argument<List<String>>("packages") ?: emptyList()
                            BlockerPrefs.setVpnExcludedPackages(this, packages)
                            result.success(null)
                        }
                        "removeDeviceAdmin" -> {
                            ProtectionController.removeDeviceAdmin(this)
                            result.success(null)
                        }
                        "requestUsageAccess" -> {
                            ProtectionController.openUsageAccessSettings(this)
                            result.success(null)
                        }
                        "requestOverlay" -> {
                            ProtectionController.openOverlaySettings(this)
                            result.success(null)
                        }
                        "openVpnSettings" -> {
                            ProtectionController.openVpnSettings(this)
                            result.success(null)
                        }
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
                            if (protectionEnabled || uninstall) {
                                ProtectionController.startGuard(this)
                                ProtectionController.scheduleRestart(this)
                            } else {
                                ProtectionController.cancelRestart(this)
                                ProtectionService.stop(this)
                            }
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

    override fun onResume() {
        super.onResume()
        if (BlockerPrefs.isProtectionEnabled(this) && !HavenVpnService.running) {
            HavenVpnService.start(this)
        }
    }

    private fun requestVpnPermission(result: MethodChannel.Result) {
        val consent = VpnService.prepare(this)
        if (consent == null) {
            result.success(true)
            return
        }
        pendingVpnResult?.success(false)
        pendingVpnResult = result
        @Suppress("DEPRECATION")
        startActivityForResult(consent, REQUEST_VPN)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_VPN) return
        val granted = resultCode == Activity.RESULT_OK
        if (granted && BlockerPrefs.isProtectionEnabled(this)) {
            HavenVpnService.start(this)
        }
        pendingVpnResult?.success(granted)
        pendingVpnResult = null
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1002
        private const val REQUEST_VPN = 1003
    }
}
