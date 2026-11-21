package com.moneysmart.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
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
        @Volatile
        private var INSTANCE: MoneySmartDatabase? = null

        fun getInstance(context: Context): MoneySmartDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoneySmartDatabase::class.java,
                    "money_smart.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
