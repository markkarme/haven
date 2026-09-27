package com.haven.haven

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Cross-process show/hide for the uninstall password gate.
 *
 * The visible gate is [AppInfoGateActivity] (draws over Settings / installer on ColorOS).
 * [UninstallGateOverlay] is an unkillable backup owned by `:guard`.
 */
object UninstallGateCommands {
    const val ACTION_SHOW = "com.haven.haven.action.SHOW_UNINSTALL_GATE"
    const val ACTION_HIDE = "com.haven.haven.action.HIDE_UNINSTALL_GATE"
    const val EXTRA_RESHOW = "reshow"
    const val EXTRA_URGENT = "urgent"

    fun requestShow(context: Context, reshow: Boolean = false, urgent: Boolean = false) {
        val app = context.applicationContext
        ProtectionService.start(app)
        ProtectionService.requestShowGate(app, reshow, urgent)
        app.sendBroadcast(
            Intent(ACTION_SHOW).setPackage(app.packageName)
                .putExtra(EXTRA_RESHOW, reshow)
                .putExtra(EXTRA_URGENT, urgent),
        )
    }

    fun requestHide(context: Context) {
        val app = context.applicationContext
        hideLocally(app)
        app.sendBroadcast(Intent(ACTION_HIDE).setPackage(app.packageName))
        ProtectionService.requestHideGate(app)
    }

    fun showLocally(context: Context, reshow: Boolean, urgent: Boolean) {
        if (BlockerPrefs.isGateUserDismissed(context)) return
        // Activity first: TYPE_APPLICATION_OVERLAY is often hidden under ColorOS Settings
        // until the user leaves — that looked like "shows only when I go back".
        AppInfoGateActivity.show(context, reshow, urgent = true)
        // Overlay backup in `:guard`: cannot be swiped away from Recents.
        if (ProtectionController.canDrawOverlays(context)) {
            UninstallGateOverlay.show(context, reshow = true, force = true)
        }
    }

    fun hideLocally(context: Context) {
        BlockerPrefs.setUninstallGateOpen(context, false)
        UninstallGateOverlay.hide()
        AppInfoGateActivity.requestDismiss(context)
        AppInfoGateActivity.cancelLaunchNotification(context)
    }

    fun register(context: Context): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                when (intent?.action) {
                    ACTION_SHOW -> showLocally(
                        ctx.applicationContext,
                        intent.getBooleanExtra(EXTRA_RESHOW, false),
                        intent.getBooleanExtra(EXTRA_URGENT, false),
                    )
                    ACTION_HIDE -> hideLocally(ctx.applicationContext)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_SHOW)
            addAction(ACTION_HIDE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        return receiver
    }
}
