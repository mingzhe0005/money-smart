package com.moneysmart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class AccountType(val displayName: String) {
    CASH("Cash"),
    BANK("Bank Account"),
    CREDIT_CARD("Credit Card"),
    E_WALLET("E-Wallet"),
    INVESTMENT("Investment"),
    OTHER("Other")
}

@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: AccountType,
    val currency: String = "USD",
    val initialBalance: Double = 0.0,
    val iconName: String = "AccountBalanceWallet",
    val colorHex: Long = 0xFF0D6E52L,
    val includeInTotal: Boolean = true,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val autoRechargeSourceAccountId: Long? = null,
    val autoRechargeMinBalance: Double = 0.0,
    val marketSymbol: String? = null,
    val holdingUnits: Double? = null,
    val lastSyncedMarketValue: Double? = null,
    val lastMarketSyncAt: Long = 0L,
    val creditLimit: Double? = null,
    val cardLast4: String? = null,
    val defaultPaymentAccountId: Long? = null
)
