package com.haven.haven

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button

object BlockOverlayWindow {

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun show(context: Context) {
        val app = context.applicationContext
        main.post { showOnMain(app) }
    }

    fun hide() {
        main.post { hideOnMain() }
    }

    @SuppressLint("InflateParams")
    private fun showOnMain(context: Context) {
        if (view != null) return
        if (!Settings.canDrawOverlays(context)) return

        val themed = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
        val root = LayoutInflater.from(themed).inflate(R.layout.activity_block_overlay, null)

        root.findViewById<Button>(R.id.block_go_back)?.setOnClickListener { goHome(context) }

        root.isFocusableInTouchMode = true
        root.requestFocus()
        root.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                goHome(context)
                true
            } else {
                false
            }
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        )

        try {
            windowManager(context).addView(root, params)
            view = root
        } catch (_: Exception) {
            view = null
        }
    }

    private fun hideOnMain() {
        val current = view ?: return
        try {
            windowManager(current.context).removeView(current)
        } catch (_: Exception) {
        }
        view = null
    }

    private fun goHome(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }

    private fun windowManager(context: Context): WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
}