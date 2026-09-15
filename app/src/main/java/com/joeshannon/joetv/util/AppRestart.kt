package com.joeshannon.joetv.util

import android.content.Context
import android.content.Intent

object AppRestart {
    fun softRestart(context: Context) {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(context.packageName) ?: return
        val restartIntent = Intent.makeRestartActivityTask(launchIntent.component)
        context.startActivity(restartIntent)
        Runtime.getRuntime().exit(0)
    }
}
