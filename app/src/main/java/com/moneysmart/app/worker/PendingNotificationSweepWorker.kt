package com.moneysmart.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneysmart.app.MoneySmartApp
import java.util.concurrent.TimeUnit

class PendingNotificationSweepWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            MoneySmartApp.container(applicationContext)
                .notificationProcessor
                .processAllPendingNotifications(includeFailed = false)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "money_smart_pending_notif_sweep"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<PendingNotificationSweepWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
