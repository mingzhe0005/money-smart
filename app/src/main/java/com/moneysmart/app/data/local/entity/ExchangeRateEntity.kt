package com.moneysmart.app.data.local.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "exchange_rates",
    primaryKeys = ["baseCurrency", "targetCurrency"]
)
data class ExchangeRateEntity(
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: Double,
    val updatedAt: Long = System.currentTimeMillis()
)
