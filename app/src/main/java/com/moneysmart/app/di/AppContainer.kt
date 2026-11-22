package com.moneysmart.app.di

import android.content.Context
import com.moneysmart.app.data.local.MoneySmartDatabase
import com.moneysmart.app.data.local.SettingsDataStore
import com.moneysmart.app.data.remote.AppUpdateChecker
import com.moneysmart.app.data.remote.ExchangeRateApiClient
import com.moneysmart.app.data.remote.GeminiApiClient
import com.moneysmart.app.data.remote.MarketPriceApiClient
import com.moneysmart.app.data.repository.BackupRepository
import com.moneysmart.app.data.repository.MoneyManagerImporter
import com.moneysmart.app.data.repository.MoneyRepository
import com.moneysmart.app.notification.NotificationProcessor

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: MoneySmartDatabase = MoneySmartDatabase.getInstance(appContext)
    val settingsDataStore: SettingsDataStore = SettingsDataStore(appContext)
    val geminiApiClient: GeminiApiClient = GeminiApiClient()
    val exchangeRateApiClient: ExchangeRateApiClient = ExchangeRateApiClient()
    val marketPriceApiClient: MarketPriceApiClient = MarketPriceApiClient()
    val appUpdateChecker: AppUpdateChecker = AppUpdateChecker()

    val repository: MoneyRepository = MoneyRepository(
        db = database,
        settingsDataStore = settingsDataStore,
        exchangeRateApiClient = exchangeRateApiClient,
        marketPriceApiClient = marketPriceApiClient
    )

    val backupRepository: BackupRepository = BackupRepository(
        context = appContext,
        db = database,
        settingsDataStore = settingsDataStore
    )

    val moneyManagerImporter: MoneyManagerImporter = MoneyManagerImporter(
        context = appContext,
        db = database,
        settingsDataStore = settingsDataStore
    )

    val notificationProcessor: NotificationProcessor = NotificationProcessor(
        context = appContext,
        repository = repository,
        settingsDataStore = settingsDataStore,
        geminiApiClient = geminiApiClient
    )
}
