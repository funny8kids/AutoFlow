package com.autoflow.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autoflow.app.AutoFlowApp

/** 小组件点击入口：直接运行对应宏。 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(QuickWidgetProvider.EXTRA_MACRO_ID) ?: return
        val app = context.applicationContext as? AutoFlowApp ?: return
        val macro = app.store.macros.value.firstOrNull { it.id == id } ?: return
        app.engine.run(macro, "桌面小组件")
    }
}
