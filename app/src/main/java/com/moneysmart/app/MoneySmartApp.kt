package com.moneysmart.app

import android.app.Application
import android.content.Context
import com.moneysmart.app.data.local.DefaultSeedData
import com.moneysmart.app.di.AppContainer
import com.moneysmart.app.notification.TransactionAlertNotifier
import com.moneysmart.app.worker.AutoBackupWorker
import com.moneysmart.app.worker.ExchangeRateSyncWorker
import com.moneysmart.app.worker.InvestmentSyncWorker
import com.moneysmart.app.worker.RecurringTransactionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MoneySmartApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        TransactionAlertNotifier.ensureChannelsCreated(this)

        appScope.launch {
            val settings = container.settingsDataStore.getCurrentSettings()
            DefaultSeedData.seedDefaultsIfEmpty(container.database, settings.baseCurrency)
            DefaultSeedData.ensurePayLahAutoRechargeLinked(container.database)
            container.repository.processDueRecurringTransactions()
            container.repository.syncExchangeRates(force = false)
            container.repository.syncLiveInvestmentValues()
            if (settings.autoBackupEnabled && settings.backupFolderUri.isNotBlank()) {
                AutoBackupWorker.schedulePeriodic(this@MoneySmartApp)
            }
        }

        ExchangeRateSyncWorker.schedulePeriodic(this)
        RecurringTransactionWorker.schedulePeriodic(this)
        InvestmentSyncWorker.schedulePeriodic(this)
    }

    companion object {
        fun container(context: Context): AppContainer {
            return (context.applicationContext as MoneySmartApp).container
        }
    }
}
