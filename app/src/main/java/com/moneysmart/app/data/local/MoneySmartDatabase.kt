package com.moneysmart.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneysmart.app.data.local.dao.AccountDao
import com.moneysmart.app.data.local.dao.BudgetDao
import com.moneysmart.app.data.local.dao.CategoryDao
import com.moneysmart.app.data.local.dao.ExchangeRateDao
import com.moneysmart.app.data.local.dao.RawNotificationDao
import com.moneysmart.app.data.local.dao.RecurringDao
import com.moneysmart.app.data.local.dao.SplitBillDao
import com.moneysmart.app.data.local.dao.TransactionDao
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.BudgetEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.ExchangeRateEntity
import com.moneysmart.app.data.local.entity.RawNotificationEntity
import com.moneysmart.app.data.local.entity.RecurringTransactionEntity
import com.moneysmart.app.data.local.entity.SplitBillEntity
import com.moneysmart.app.data.local.entity.SplitParticipantEntity
import com.moneysmart.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class,
        RawNotificationEntity::class,
        ExchangeRateEntity::class,
        SplitBillEntity::class,
        SplitParticipantEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MoneySmartDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun rawNotificationDao(): RawNotificationDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun splitBillDao(): SplitBillDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN autoRechargeSourceAccountId INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN autoRechargeMinBalance REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE accounts ADD COLUMN marketSymbol TEXT")
                db.execSQL("ALTER TABLE accounts ADD COLUMN holdingUnits REAL")
                db.execSQL("ALTER TABLE accounts ADD COLUMN lastSyncedMarketValue REAL")
                db.execSQL("ALTER TABLE accounts ADD COLUMN lastMarketSyncAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN reimbursedAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN reimbursedBaseAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isReimbursement INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN splitBillId INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS split_bills (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        totalAmount REAL NOT NULL,
                        myShareAmount REAL NOT NULL,
                        currency TEXT NOT NULL,
                        paidAccountId INTEGER NOT NULL,
                        categoryId INTEGER,
                        expenseTransactionId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS split_participants (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        splitBillId INTEGER NOT NULL,
                        friendName TEXT NOT NULL,
                        owedAmount REAL NOT NULL,
                        paidAmount REAL NOT NULL,
                        isSettled INTEGER NOT NULL,
                        repaidAccountId INTEGER,
                        repaymentTransactionId INTEGER,
                        settledAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_split_participants_splitBillId ON split_participants(splitBillId)"
                )
            }
        }

        @Volatile
        private var INSTANCE: MoneySmartDatabase? = null

        @Volatile
        private var inMemoryTestMode: Boolean = false

        internal fun enableInMemoryTestMode() {
            inMemoryTestMode = true
        }

        fun getInstance(context: Context): MoneySmartDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    if (inMemoryTestMode) {
                        Room.inMemoryDatabaseBuilder(
                            context.applicationContext,
                            MoneySmartDatabase::class.java
                        )
                            .allowMainThreadQueries()
                            .build()
                    } else {
                        Room.databaseBuilder(
                            context.applicationContext,
                            MoneySmartDatabase::class.java,
                            "money_smart.db"
                        )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build()
                    }
                }.also { INSTANCE = it }
            }
        }

        internal fun resetForTests() {
            val dbToClose = synchronized(this) {
                INSTANCE.also { INSTANCE = null }
            }
            runCatching { dbToClose?.close() }
        }
    }
}
