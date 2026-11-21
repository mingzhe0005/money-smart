# Money Smart

**Money Smart** is an Android personal finance & expense manager built with **Kotlin**, **Jetpack Compose (Material 3)**, **Room**, and **WorkManager**. It reads payment notifications from your chosen banking/e-wallet/SMS apps, uses **Google Gemini** (free tier via your own API key) to analyze and classify each alert, and automatically records transactions to your ledger.

All features are **100% free of charge** with zero backend server costs or paid APIs.

---

## Key Features (100% Free Architecture)

- **AI Payment Notification Reader (`NotificationListenerService` + `WorkManager`)**
  - Monitors **only** the apps you explicitly allow in **More → Monitored Payment Apps**.
  - Queues notifications locally and sends them to Gemini Flash (`gemini-3.8-flash` by default, with live free-tier probing and automatic model fallback) using your free **Google AI Studio API key (BYOK)**.
  - **On-Device Habit Learning**: summarizes your historical merchant/note patterns locally and teaches Gemini your personal Category & Account preferences with zero extra API calls.
  - Automatically classifies **Expense**, **Income**, **Transfer**, or **Ignore** (OTPs, promos, failed payments), infers the matching **Account** and **Category/Subcategory**, and records the transaction.
  - **Smart Deduplication**: merges duplicate alerts (e.g., bank app + SMS for the same purchase) matching the same amount and currency within ±2 minutes.
  - **Auto-Record + Quick Edit/Undo**: immediately records transactions, sends a notification with **Edit** and **1-Tap Undo** actions, and flags low-confidence or ambiguous parses for review.
  - **Test AI Parser Simulator**: test any sample bank/e-wallet notification text live inside **AI Notification Capture**.
- **Full Money Manager Suite & Importer**
  - **Money Manager Importer**: import your existing Money Manager `.xls` / `.xlsx` / `.csv` export from Google Drive or local storage in **More → Backup & Restore** (preserving accounts, categories, transfers, and foreign currencies).
  - **Accounts & Wallets** (Cash, Bank, Credit Card, E-Wallet, Investment) with live balances, Net Worth, Assets, Liabilities, and Transfers.
  - **Custom Categories & Subcategories** with icon & color pickers.
  - **Daily, Monthly, and Calendar Views** plus **Search** and **Needs Review** filtering.
  - **Spending Analytics** with Donut chart, Category breakdown bars, and Weekly/Monthly trend charts.
  - **Monthly Budgets** (overall or per category) with 80% / 100% progress alerts.
  - **Multi-Currency Support** using free no-key daily exchange rates (`open.er-api.com` + `frankfurter.app` fallback), cached locally offline with manual rate override per transaction.
  - **Recurring Transactions & Subscriptions** with automatic scheduling and manual "Record Now".
- **Free Google Drive / Local Backup & Security**
  - **Storage Access Framework (SAF)** backup/restore: pick your **Google Drive** folder or any local folder with zero cloud billing or OAuth setup, export full JSON snapshots, export CSV, or enable daily auto-backup.
  - **Optional PIN & Biometric App Lock** (salted SHA-256 PIN + Android `BiometricPrompt`), plus Light/Dark/System themes.

---

## Setup Guide

### 1. Get a Free Gemini API Key
1. Open [Google AI Studio](https://aistudio.google.com/) and sign in with your Google account.
2. Click **Get API key → Create API key** (the free tier requires no credit card and is more than sufficient for daily payment notifications).
3. In **Money Smart**, go to **More → Settings & Security**, paste your API key, and tap **Save Key → Test Connection**.

### 2. Enable AI Notification Capture
1. Go to **More → AI Activity & Test Simulator**.
2. Tap **Enable Access** to grant Android **Notification Access** to **Money Smart Payment Reader**.
3. Tap **Select Apps** (or **More → Monitored Payment Apps**) and check the banking, card, e-wallet, or SMS apps you want Money Smart to read.

### 3. Free Google Drive / Local Backup
1. Go to **More → Backup & Restore**.
2. Tap **Choose Google Drive / Local Folder** and pick a folder inside **Google Drive** (or local storage) in Android's system file picker.
3. Optionally enable **Daily Auto-Backup**.

---

## Building from Source

Open the project folder in **Android Studio**, or build from PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is output to:
`app/build/outputs/apk/debug/app-debug.apk`
