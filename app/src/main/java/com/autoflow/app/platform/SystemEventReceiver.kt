package com.autoflow.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autoflow.app.AutoFlowApp

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? AutoFlowApp ?: return
        app.triggers.onSystemEvent(intent)
    }
}
