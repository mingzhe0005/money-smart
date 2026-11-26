package com.moneysmart.app.notification

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.moneysmart.app.MoneySmartApp
import com.moneysmart.app.data.local.entity.NotificationProcessStatus
import com.moneysmart.app.data.local.entity.RawNotificationEntity
import com.moneysmart.app.worker.ProcessNotificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaymentNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == applicationContext.packageName) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val standardText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

        val bodyText = listOfNotNull(
            bigText?.takeIf { it.isNotEmpty() } ?: standardText?.takeIf { it.isNotEmpty() },
            subText?.takeIf { it.isNotEmpty() }
        ).joinToString(" · ")

        if (title.isBlank() && bodyText.isBlank()) return

        val postedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        scope.launch {
            val container = MoneySmartApp.container(applicationContext)
            val settings = container.settingsDataStore.getCurrentSettings()
            if (!settings.aiCaptureEnabled) return@launch
            if (pkg !in settings.allowedPackages) return@launch

            val recentIdentical = container.repository.findRecentIdenticalRawNotification(
                packageName = pkg,
                title = title,
                text = bodyText,
                sinceMillis = postedAt - 5_000L
            )
            if (recentIdentical != null) return@launch

            val appName = resolveAppLabel(pkg)
            val rawId = container.repository.insertRawNotification(
                RawNotificationEntity(
                    packageName = pkg,
                    appName = appName,
                    title = title,
                    text = bodyText,
                    postedAt = postedAt,
                    status = NotificationProcessStatus.PENDING
                )
            )
            ProcessNotificationWorker.enqueue(applicationContext, rawId)
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun isNotificationListenerEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val target = ComponentName(context, PaymentNotificationListenerService::class.java)
            return flat.split(":").any { item ->
                val cn = ComponentName.unflattenFromString(item)
                cn == target || item.contains(context.packageName)
            }
        }
    }
}
