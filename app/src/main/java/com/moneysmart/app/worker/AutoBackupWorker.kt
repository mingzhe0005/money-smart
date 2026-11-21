package com.moneysmart.app.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneysmart.app.MoneySmartApp
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = MoneySmartApp.container(applicationContext)
        val settings = container.settingsDataStore.getCurrentSettings()
        if (!settings.autoBackupEnabled || settings.backupFolderUri.isBlank()) {
            return Result.success()
        }
        val uri = runCatching { Uri.parse(settings.backupFolderUri) }.getOrNull()
            ?: return Result.failure()
        val result = container.backupRepository.exportJsonToTree(uri)
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "money_smart_auto_backup"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
