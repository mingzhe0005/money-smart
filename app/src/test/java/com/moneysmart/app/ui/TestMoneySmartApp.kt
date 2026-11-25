package com.moneysmart.app.ui

import com.moneysmart.app.MoneySmartApp
import com.moneysmart.app.data.local.MoneySmartDatabase

class TestMoneySmartApp : MoneySmartApp() {
    override fun onCreate() {
        MoneySmartDatabase.enableInMemoryTestMode()
        super.onCreate()
    }

    override fun runStartupBackgroundWork() {
        // No-op in JVM screenshot/unit tests to avoid network calls and WorkManager side effects.
    }
}
