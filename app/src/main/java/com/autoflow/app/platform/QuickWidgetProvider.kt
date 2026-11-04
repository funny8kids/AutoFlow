package com.autoflow.app.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.autoflow.app.AutoFlowApp
import com.autoflow.app.R

/**
 * 桌面小组件：最多展示 6 个启用中的宏，点一下直接运行。
 */
class QuickWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildViews(context))
        }
    }

    companion object {
        const val ACTION_RUN = "com.autoflow.app.WIDGET_RUN"
        const val EXTRA_MACRO_ID = "macro_id"

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quick)
            views.removeAllViews(R.id.widget_buttons)

            val app = runCatching { AutoFlowApp.app() }.getOrNull()
            val macros = app?.store?.macros?.value.orEmpty().filter { it.enabled && !it.isBlock }.take(6)

            if (macros.isEmpty()) {
                val empty = RemoteViews(context.packageName, R.layout.widget_button)
                empty.setTextViewText(R.id.widget_button_root, "还没有启用中的宏")
                views.addView(R.id.widget_buttons, empty)
                return views
            }

            macros.forEach { macro ->
                val item = RemoteViews(context.packageName, R.layout.widget_button)
                item.setTextViewText(R.id.widget_button_root, macro.name)
                val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                    action = ACTION_RUN
                    putExtra(EXTRA_MACRO_ID, macro.id)
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    macro.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                item.setOnClickPendingIntent(R.id.widget_button_root, pending)
                views.addView(R.id.widget_buttons, item)
            }

            views.setTextViewText(R.id.widget_title, "AutoFlow · 快捷运行")
            return views
        }

        /** 宏列表变化后调用，刷新所有小组件。 */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, QuickWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
