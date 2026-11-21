package com.moneysmart.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "split_bills")
data class SplitBillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val totalAmount: Double,
    val myShareAmount: Double,
    val currency: String = "SGD",
    val paidAccountId: Long,
    val categoryId: Long? = null,
    val expenseTransactionId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "split_participants",
    indices = [Index(value = ["splitBillId"])]
)
data class SplitParticipantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val splitBillId: Long,
    val friendName: String,
    val owedAmount: Double,
    val paidAmount: Double = 0.0,
    val isSettled: Boolean = false,
    val repaidAccountId: Long? = null,
    val repaymentTransactionId: Long? = null,
    val settledAt: Long? = null
)
