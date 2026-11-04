package com.autoflow.app.platform

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.autoflow.app.AutoFlowApp

/**
 * 通知监听服务：需要用户在系统设置里授予「通知使用权」。
 */
class AutoNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        val app = runCatching { AutoFlowApp.app() }.getOrNull() ?: return
        runCatching { app.triggers.onNotificationPosted(pkg) }

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val big = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val merged = "$title $text $big".trim()
        runCatching { app.triggers.onNotificationText(pkg, merged) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        val app = runCatching { AutoFlowApp.app() }.getOrNull() ?: return
        runCatching { app.triggers.onNotificationRemoved(pkg) }
    }
}
