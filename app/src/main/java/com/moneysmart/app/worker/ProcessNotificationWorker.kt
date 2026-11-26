package com.moneysmart.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import com.moneysmart.app.MoneySmartApp
import com.moneysmart.app.R
import com.moneysmart.app.data.local.entity.NotificationProcessStatus
import com.moneysmart.app.notification.ProcessOutcome
import com.moneysmart.app.notification.TransactionAlertNotifier
import java.util.concurrent.TimeUnit

class ProcessNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val rawId = inputData.getLong(KEY_RAW_NOTIFICATION_ID, -1L)
        if (rawId <= 0L) return Result.failure()

        val container = MoneySmartApp.container(applicationContext)
        return when (val outcome = container.notificationProcessor.processRawNotification(rawId)) {
            is ProcessOutcome.RetryableFailure -> {
                if (runAttemptCount < 8) {
                    Result.retry()
                } else {
                    container.repository.getRawNotificationById(rawId)?.let { raw ->
                        container.repository.updateRawNotification(
                            raw.copy(
                                status = NotificationProcessStatus.FAILED,
                                errorMessage = outcome.message
                            )
                        )
                    }
                    Result.failure()
                }
            }
            is ProcessOutcome.PermanentFailure -> Result.failure()
            is ProcessOutcome.Recorded,
            is ProcessOutcome.Duplicate,
            is ProcessOutcome.Ignored -> Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        TransactionAlertNotifier.ensureChannelsCreated(applicationContext)
        val notification = NotificationCompat.Builder(
            applicationContext,
            TransactionAlertNotifier.CHANNEL_AI_RECORDED
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Processing payment alert...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        return ForegroundInfo(31_001, notification)
    }

    companion object {
        const val KEY_RAW_NOTIFICATION_ID = "raw_notification_id"

        fun enqueue(context: Context, rawNotificationId: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = Data.Builder()
                .putLong(KEY_RAW_NOTIFICATION_ID, rawNotificationId)
                .build()

            val request = OneTimeWorkRequestBuilder<ProcessNotificationWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "process_notif_$rawNotificationId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
