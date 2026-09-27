package com.haven.haven

import android.annotation.SuppressLint
import android.content.Context
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
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * Uninstall password gate as a system overlay — not an Activity, so it cannot be
 * swiped away from Recent Apps. Falls back to [AppInfoGateActivity] when overlay
 * permission is missing.
 *
 * Must be shown from the `:guard` process ([ProtectionService]) so the main Haven
 * task can be cleared without dismissing this UI.
 */
object UninstallGateOverlay {

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null

    @Volatile
    var isShowing: Boolean = false
        private set

    fun show(context: Context, reshow: Boolean = false) {
        val app = context.applicationContext
        main.post { showOnMain(app, reshow) }
    }

    fun hide() {
        main.post { hideOnMain() }
    }

    @SuppressLint("InflateParams")
    private fun showOnMain(context: Context, reshow: Boolean) {
        if (isShowing) return
        if (!Settings.canDrawOverlays(context)) {
            AppInfoGateActivity.show(context, reshow)
            return
        }
        if (!BlockerPrefs.tryAcquireUninstallGateShow(context, reshow)) return

        // Mark open before addView so other processes debounce correctly.
        BlockerPrefs.setUninstallGateOpen(context, true)

        val themed = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
        val root = LayoutInflater.from(themed).inflate(R.layout.activity_app_info_gate, null)
        val passwordField = root.findViewById<EditText>(R.id.app_info_password)
        val errorView = root.findViewById<TextView>(R.id.app_info_error)

        root.findViewById<Button>(R.id.app_info_go_back)?.setOnClickListener {
            ProtectionController.retreatFromAppInfoGate(context)
        }

        root.findViewById<Button>(R.id.app_info_confirm)?.setOnClickListener {
            val entered = passwordField.text?.toString().orEmpty()
            if (entered != UnlockSecrets.PASSWORD) {
                errorView.visibility = View.VISIBLE
                errorView.setText(R.string.app_info_wrong_password)
                return@setOnClickListener
            }
            BlockerPrefs.unlockAppInfo(context)
            BlockerPrefs.clearRemovalAttemptIfSet(context)
            Toast.makeText(context, R.string.app_info_unlocked_toast, Toast.LENGTH_LONG).show()
            ProtectionController.dismissUninstallGateAfterUnlock(context)
        }

        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.setOnKeyListener { _, keyCode, event ->
            // System Back must not dismiss the gate — only Go Back or the password works.
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
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
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE,
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        try {
            windowManager(context).addView(root, params)
            view = root
            isShowing = true
            passwordField.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(passwordField, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {
            view = null
            isShowing = false
            BlockerPrefs.setUninstallGateOpen(context, false)
            AppInfoGateActivity.show(context, reshow)
        }
    }

    private fun hideOnMain() {
        val current = view ?: run {
            isShowing = false
            return
        }
        try {
            val imm = current.context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
            imm.hideSoftInputFromWindow(current.windowToken, 0)
            windowManager(current.context).removeView(current)
        } catch (_: Exception) {
        }
        view = null
        isShowing = false
        BlockerPrefs.setUninstallGateOpen(current.context, false)
    }

    private fun windowManager(context: Context): WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
}
