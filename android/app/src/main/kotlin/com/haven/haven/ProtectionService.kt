package com.haven.haven

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/**
 * Persistent foreground service that owns [AppMonitor]. Runs in a separate `:guard`
 * process so swiping Haven from Recents does not stop uninstall protection.
 */
class ProtectionService : Service() {

    private var appMonitor: AppMonitor? = null
    private var gateReceiver: android.content.BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        promoteToForeground()
        gateReceiver = UninstallGateCommands.register(this)
        appMonitor = AppMonitor(applicationContext)
        appMonitor?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP ||
            (!BlockerPrefs.isProtectionEnabled(this) && !BlockerPrefs.shouldGuardAppInfo(this))
        ) {
            UninstallGateCommands.hideLocally(this)
            appMonitor?.stop()
            appMonitor = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        promoteToForeground()
        if (appMonitor == null) {
            appMonitor = AppMonitor(applicationContext)
        }
        appMonitor?.start()

        when (intent?.action) {
            ACTION_SHOW_GATE -> UninstallGateCommands.showLocally(
                this,
                intent.getBooleanExtra(UninstallGateCommands.EXTRA_RESHOW, false),
                intent.getBooleanExtra(UninstallGateCommands.EXTRA_URGENT, false),
            )
            ACTION_HIDE_GATE -> UninstallGateCommands.hideLocally(this)
            else -> {
                if (BlockerPrefs.isRemovalAttemptActive(this) &&
                    !BlockerPrefs.isAppInfoUnlocked(this) &&
                    !BlockerPrefs.isGateUserDismissed(this)
                ) {
                    UninstallGateCommands.showLocally(this, reshow = false, urgent = false)
                }
            }
        }

        if (BlockerPrefs.isProtectionEnabled(this) && !HavenVpnService.running) {
            HavenVpnService.start(this)
        }
        ProtectionController.scheduleRestart(this)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleResurrection()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        gateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        gateReceiver = null
        appMonitor?.stop()
        appMonitor = null
        if (BlockerPrefs.isProtectionEnabled(this) || BlockerPrefs.shouldGuardAppInfo(this)) {
            scheduleResurrection()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleResurrection() {
        val restartIntent = Intent(applicationContext, BootAndRestartReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            applicationContext,
            RESTART_REQUEST,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = SystemClock.elapsedRealtime() + RESURRECT_DELAY_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    at,
                    pending,
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        } catch (_: Exception) {
        }
    }

    private fun promoteToForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.protection_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.protection_channel_description)
                },
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_haven)
            .setContentTitle(getString(R.string.protection_notification_title))
            .setContentText(getString(R.string.protection_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_STOP = "com.haven.haven.protection.STOP"
        const val ACTION_SHOW_GATE = "com.haven.haven.protection.SHOW_GATE"
        const val ACTION_HIDE_GATE = "com.haven.haven.protection.HIDE_GATE"
        private const val CHANNEL = "haven_protection_channel"
        private const val NOTIFICATION_ID = 2006
        private const val RESTART_REQUEST = 3102
        private const val RESURRECT_DELAY_MS = 1_000L

        fun start(context: Context) {
            val intent = Intent(context, ProtectionService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun requestShowGate(context: Context, reshow: Boolean, urgent: Boolean) {
            val intent = Intent(context, ProtectionService::class.java)
                .setAction(ACTION_SHOW_GATE)
                .putExtra(UninstallGateCommands.EXTRA_RESHOW, reshow)
                .putExtra(UninstallGateCommands.EXTRA_URGENT, urgent)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun requestHideGate(context: Context) {
            try {
                context.startService(
                    Intent(context, ProtectionService::class.java).setAction(ACTION_HIDE_GATE),
                )
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, ProtectionService::class.java).setAction(ACTION_STOP),
                )
            } catch (_: Exception) {
            }
        }
    }
}
