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
import com.moneysmart.app.data.local.dao.TransactionDao
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.BudgetEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.ExchangeRateEntity
import com.moneysmart.app.data.local.entity.RawNotificationEntity
import com.moneysmart.app.data.local.entity.RecurringTransactionEntity
import com.moneysmart.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class,
        RawNotificationEntity::class,
        ExchangeRateEntity::class
    ],
    version = 2,
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

        @Volatile
        private var INSTANCE: MoneySmartDatabase? = null

        fun getInstance(context: Context): MoneySmartDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoneySmartDatabase::class.java,
                    "money_smart.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
