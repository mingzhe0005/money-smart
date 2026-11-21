package com.moneysmart.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneysmart.app.MainActivity
import com.moneysmart.app.R
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.repository.BudgetAlertEvent
import com.moneysmart.app.util.FormatUtils

object TransactionAlertNotifier {

    const val CHANNEL_AI_RECORDED = "ai_recorded_channel"
    const val CHANNEL_BUDGET_ALERTS = "budget_alerts_channel"

    const val EXTRA_EDIT_TRANSACTION_ID = "extra_edit_transaction_id"
    const val ACTION_UNDO_TRANSACTION = "com.moneysmart.app.ACTION_UNDO_TRANSACTION"
    const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    fun ensureChannelsCreated(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val aiChannel = NotificationChannel(
            CHANNEL_AI_RECORDED,
            context.getString(R.string.channel_ai_recorded_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_ai_recorded_desc)
        }

        val budgetChannel = NotificationChannel(
            CHANNEL_BUDGET_ALERTS,
            context.getString(R.string.channel_budget_alerts_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_budget_alerts_desc)
        }

        manager.createNotificationChannel(aiChannel)
        manager.createNotificationChannel(budgetChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showRecordedTransactionAlert(
        context: Context,
        transaction: TransactionEntity,
        accountName: String,
        categoryName: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannelsCreated(context)

        val notifId = (20_000 + (transaction.id % 10_000)).toInt()
        val formattedAmount = FormatUtils.formatMoney(transaction.amount, transaction.currency)
        val label = transaction.merchant.ifBlank { categoryName.ifBlank { transaction.type.displayName } }

        val title = if (transaction.needsReview) {
            "Review AI ${transaction.type.displayName}: $formattedAmount"
        } else {
            "Recorded ${transaction.type.displayName}: $formattedAmount"
        }
        val body = buildString {
            append(label)
            if (accountName.isNotBlank()) append(" · ").append(accountName)
            if (categoryName.isNotBlank()) append(" (").append(categoryName).append(")")
            if (transaction.needsReview) append(" — Tap to review")
        }

        val editIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_EDIT_TRANSACTION_ID, transaction.id)
        }
        val editPendingIntent = PendingIntent.getActivity(
            context,
            transaction.id.toInt(),
            editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val undoIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_UNDO_TRANSACTION
            putExtra(EXTRA_TRANSACTION_ID, transaction.id)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val undoPendingIntent = PendingIntent.getBroadcast(
            context,
            (100_000 + transaction.id).toInt(),
            undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_AI_RECORDED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(editPendingIntent)
            .addAction(0, "Edit", editPendingIntent)
            .addAction(0, "Undo", undoPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    fun showBudgetAlert(
        context: Context,
        event: BudgetAlertEvent,
        baseCurrency: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannelsCreated(context)

        val notifId = (40_000 + (event.budgetId % 10_000)).toInt()
        val spentFormatted = FormatUtils.formatMoney(event.spentAmount, baseCurrency)
        val limitFormatted = FormatUtils.formatMoney(event.limitAmount, baseCurrency)
        val title = if (event.isExceeded) {
            "Budget Exceeded: ${event.categoryName}"
        } else {
            "Budget Alert (${event.percentUsed}%): ${event.categoryName}"
        }
        val body = "Spent $spentFormatted of $limitFormatted monthly limit."

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }
}
