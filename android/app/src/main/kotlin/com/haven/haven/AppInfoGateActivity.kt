package com.haven.haven

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Password cover for uninstall / Device Admin when overlay permission is missing.
 * Sticky: system Back and leaving the screen must not dismiss it — only Go Back
 * (home) or a correct password closes the gate.
 */
class AppInfoGateActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_FINISH_ONLY, false)) {
            BlockerPrefs.setUninstallGateOpen(this, false)
            finishAndRemoveTask()
            return
        }
        instanceRef = WeakReference(this)
        setContentView(R.layout.activity_app_info_gate)
        cancelLaunchNotification(this)
        launchInFlight = false
        BlockerPrefs.setUninstallGateOpen(this, true)

        val passwordField = findViewById<EditText>(R.id.app_info_password)
        val errorView = findViewById<TextView>(R.id.app_info_error)

        findViewById<Button>(R.id.app_info_go_back).setOnClickListener {
            ProtectionController.retreatFromAppInfoGate(this)
        }

        findViewById<Button>(R.id.app_info_confirm).setOnClickListener {
            val entered = passwordField.text?.toString().orEmpty()
            if (entered != UnlockSecrets.PASSWORD) {
                errorView.visibility = android.view.View.VISIBLE
                errorView.setText(R.string.app_info_wrong_password)
                return@setOnClickListener
            }

            BlockerPrefs.unlockAppInfo(this)
            BlockerPrefs.clearRemovalAttemptIfSet(this)
            Toast.makeText(this, R.string.app_info_unlocked_toast, Toast.LENGTH_LONG).show()
            isShowing = false
            ProtectionController.dismissUninstallGateAfterUnlock(this)
            finishAndRemoveTask()
        }
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
        BlockerPrefs.setUninstallGateOpen(this, true)
        launchInFlight = false
        cancelLaunchNotification(this)
    }

    override fun onDestroy() {
        val stillNeeded = BlockerPrefs.shouldGuardAppInfo(this) &&
            BlockerPrefs.isRemovalAttemptActive(this) &&
            !BlockerPrefs.isAppInfoUnlocked(this) &&
            !BlockerPrefs.isGateUserDismissed(this)
        isShowing = false
        BlockerPrefs.setUninstallGateOpen(this, false)
        launchInFlight = false
        if (instanceRef?.get() === this) instanceRef = null
        super.onDestroy()
        // Killed from Recents / OEM — restore the gate while uninstall is still in progress.
        if (stillNeeded) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!BlockerPrefs.isGateUserDismissed(applicationContext) &&
                    BlockerPrefs.isRemovalAttemptActive(applicationContext) &&
                    !BlockerPrefs.isAppInfoUnlocked(applicationContext)
                ) {
                    ProtectionController.showUninstallGate(
                        applicationContext,
                        reshow = true,
                        urgent = true,
                    )
                }
            }, 350L)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra(EXTRA_FINISH_ONLY, false) == true) {
            isShowing = false
            BlockerPrefs.setUninstallGateOpen(this, false)
            finishAndRemoveTask()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Consume — the gate cannot be dismissed with the system Back key.
    }

    companion object {
        const val EXTRA_FINISH_ONLY = "finish_only"
        @Volatile
        var isShowing: Boolean = false
            private set

        @Volatile
        private var launchInFlight: Boolean = false

        @Volatile
        private var instanceRef: WeakReference<AppInfoGateActivity>? = null

        fun dismissIfShowing() {
            instanceRef?.get()?.run {
                isShowing = false
                launchInFlight = false
                BlockerPrefs.setUninstallGateOpen(this, false)
                finishAndRemoveTask()
            }
        }

        /** Works from `:guard` — the Activity instance lives in the main process. */
        fun requestDismiss(context: Context) {
            dismissIfShowing()
            try {
                context.applicationContext.startActivity(
                    launchIntent(context).putExtra(EXTRA_FINISH_ONLY, true),
                )
            } catch (_: Exception) {
            }
        }

        private const val CHANNEL = "haven_gate"
        private const val NOTIFICATION_ID = 2005

        fun launchIntent(context: Context): Intent =
            Intent(context, AppInfoGateActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
            }

        fun show(context: Context, reshow: Boolean = false, urgent: Boolean = false) {
            if (isShowing) return
            if (launchInFlight && !reshow) return
            val app = context.applicationContext
            if (!BlockerPrefs.tryAcquireUninstallGateShow(app, reshow)) return

            launchInFlight = true
            // Mark open before startActivity so `:guard` does not spam launches.
            BlockerPrefs.setUninstallGateOpen(app, true)
            val intent = launchIntent(app)
            val pending = PendingIntent.getActivity(
                app,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            var launched = false
            try {
                app.startActivity(intent)
                launched = true
            } catch (_: Exception) {
            }

            // Urgent path: also fire full-screen intent so we draw over Package Installer on ColorOS.
            if (!launched || urgent) {
                postFullScreen(app, pending)
            }

            Handler(Looper.getMainLooper()).postDelayed({
                if (!isShowing) launchInFlight = false
            }, if (reshow) 200L else 350L)
        }

        fun cancelLaunchNotification(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        private fun postFullScreen(context: Context, pending: PendingIntent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.vpn_channel_blocked),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_haven)
                .setContentTitle(context.getString(R.string.app_info_gate_title))
                .setContentText(context.getString(R.string.app_info_gate_message))
                .setContentIntent(pending)
                .setFullScreenIntent(pending, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setTimeoutAfter(8_000)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                launchInFlight = false
            }
        }
    }
}
