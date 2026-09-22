package com.haven.haven

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class BlockOverlayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_overlay)

        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val browserPackage = intent.getStringExtra(EXTRA_BROWSER_PACKAGE).orEmpty()

        val title = findViewById<TextView>(R.id.block_title)
        val subtitle = findViewById<TextView>(R.id.block_subtitle)
        val button = findViewById<Button>(R.id.block_go_back)

        when (reason) {
            REASON_SITE -> {
                title.text = getString(R.string.site_blocked_title)
                subtitle.text = getString(R.string.site_blocked_message)
                button.text = getString(R.string.go_to_home)
                button.setOnClickListener {
                    leaveSiteBlock(browserPackage)
                }
            }
            else -> {
                title.text = getString(R.string.app_blocked_title)
                subtitle.text = getString(R.string.app_blocked_message)
                button.text = getString(R.string.go_back)
                button.setOnClickListener {
                    goLauncherHome()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val browserPackage = intent.getStringExtra(EXTRA_BROWSER_PACKAGE).orEmpty()
        if (reason == REASON_SITE) {
            leaveSiteBlock(browserPackage)
        } else {
            goLauncherHome()
        }
    }

    private fun leaveSiteBlock(browserPackage: String) {
        if (browserPackage.isNotBlank()) {
            BrowserNavigator.resetToHome(this, browserPackage)
        } else {
            goLauncherHome()
            return
        }
        finishAndRemoveTask()
    }

    private fun goLauncherHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_DETAIL = "extra_detail"
        const val EXTRA_BROWSER_PACKAGE = "extra_browser_package"
        const val EXTRA_PACKAGE = "extra_package"
        const val REASON_APP = "app"
        const val REASON_SITE = "site"
    }
}
