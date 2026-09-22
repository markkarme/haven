package com.haven.haven

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Sends a browser back to a safe start page so a blocked URL is not restored.
 */
object BrowserNavigator {
    private const val SAFE_HOME = "https://www.google.com"

    fun resetToHome(context: Context, browserPackage: String) {
        if (browserPackage.isBlank()) return

        // Preferred: open a safe page and clear that browser's task (drops the blocked tab).
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(SAFE_HOME)).apply {
            setPackage(browserPackage)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
        }
        try {
            context.startActivity(viewIntent)
            return
        } catch (_: Exception) {
            // Fall through.
        }

        // Fallback: relaunch the browser's main activity with a clean task.
        val launch = context.packageManager.getLaunchIntentForPackage(browserPackage)
        if (launch != null) {
            launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
            try {
                context.startActivity(launch)
            } catch (_: Exception) {
            }
        }
    }
}
