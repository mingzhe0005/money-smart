package com.moneysmart.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneysmart.app.MoneySmartApp
import java.util.concurrent.TimeUnit

class RecurringTransactionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = MoneySmartApp.container(applicationContext)
        container.repository.processDueRecurringTransactions()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "recurring_transactions_check"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(6, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
