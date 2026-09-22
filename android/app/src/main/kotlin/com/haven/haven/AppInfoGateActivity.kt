package com.haven.haven

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * Covers the system App Info screen for Haven.
 * Options: Go Back, or enter password to view App Info for a few minutes.
 * Does not use GLOBAL_ACTION_BACK (avoids flicker loops).
 */
class AppInfoGateActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true
        setContentView(R.layout.activity_app_info_gate)

        val passwordField = findViewById<EditText>(R.id.app_info_password)
        val errorView = findViewById<TextView>(R.id.app_info_error)

        findViewById<Button>(R.id.app_info_go_back).setOnClickListener {
            goHome()
        }

        findViewById<Button>(R.id.app_info_confirm).setOnClickListener {
            val entered = passwordField.text?.toString().orEmpty()
            if (entered != UnlockSecrets.PASSWORD) {
                errorView.visibility = android.view.View.VISIBLE
                errorView.setText(R.string.app_info_wrong_password)
                return@setOnClickListener
            }

            BlockerPrefs.unlockAppInfo(this)
            Toast.makeText(this, R.string.app_info_unlocked_toast, Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        isShowing = false
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
        finishAndRemoveTask()
    }

    companion object {
        @Volatile
        var isShowing: Boolean = false
            private set

        fun show(context: Context) {
            if (isShowing) return
            isShowing = true
            context.startActivity(
                Intent(context, AppInfoGateActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                    )
                },
            )
        }
    }
}
