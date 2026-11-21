package com.moneysmart.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType(val displayName: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer")
}

@Serializable
enum class TransactionSource(val displayName: String) {
    MANUAL("Manual"),
    AI_NOTIFICATION("AI Notification"),
    RECURRING("Recurring")
}

@Serializable
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["amount", "currency", "timestamp"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: TransactionType,
    val amount: Double,
    val currency: String = "USD",
    val exchangeRateToBase: Double = 1.0,
    val baseAmount: Double = amount,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val source: TransactionSource = TransactionSource.MANUAL,
    val aiConfidence: Float? = null,
    val needsReview: Boolean = false,
    val rawNotificationId: Long? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val reimbursedAmount: Double = 0.0,
    val reimbursedBaseAmount: Double = 0.0,
    val isReimbursement: Boolean = false,
    val splitBillId: Long? = null
)
