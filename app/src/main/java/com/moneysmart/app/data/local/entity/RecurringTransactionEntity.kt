package com.moneysmart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class RecurrenceFrequency(val displayName: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

@Serializable
@Entity(tableName = "recurring_transactions")
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val type: TransactionType,
    val amount: Double,
    val currency: String = "USD",
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val frequency: RecurrenceFrequency,
    val intervalCount: Int = 1,
    val nextDueMillis: Long,
    val isActive: Boolean = true,
    val lastExecutedMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
