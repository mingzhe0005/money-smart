package com.moneysmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moneysmart.app.data.local.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates WHERE UPPER(baseCurrency) = UPPER(:baseCurrency) ORDER BY targetCurrency ASC")
    fun observeRatesForBase(baseCurrency: String): Flow<List<ExchangeRateEntity>>

    @Query(
        "SELECT * FROM exchange_rates " +
            "WHERE UPPER(baseCurrency) = UPPER(:baseCurrency) " +
            "AND UPPER(targetCurrency) = UPPER(:targetCurrency) LIMIT 1"
    )
    suspend fun getRate(baseCurrency: String, targetCurrency: String): ExchangeRateEntity?

    @Query("SELECT * FROM exchange_rates ORDER BY baseCurrency ASC, targetCurrency ASC")
    suspend fun getAllRates(): List<ExchangeRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rates: List<ExchangeRateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: ExchangeRateEntity)

    @Query("DELETE FROM exchange_rates")
    suspend fun deleteAll()
}
