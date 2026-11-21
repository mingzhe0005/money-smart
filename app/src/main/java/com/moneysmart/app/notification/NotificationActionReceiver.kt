package com.moneysmart.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.moneysmart.app.MoneySmartApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TransactionAlertNotifier.ACTION_UNDO_TRANSACTION) return

        val transactionId = intent.getLongExtra(TransactionAlertNotifier.EXTRA_TRANSACTION_ID, -1L)
        val notifId = intent.getIntExtra(TransactionAlertNotifier.EXTRA_NOTIFICATION_ID, -1)
        if (transactionId <= 0L) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = MoneySmartApp.container(appContext)
                container.repository.softDeleteTransaction(transactionId)
                if (notifId > 0) {
                    NotificationManagerCompat.from(appContext).cancel(notifId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
