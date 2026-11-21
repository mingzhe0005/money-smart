package com.moneysmart.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["categoryId", "yearMonth"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val categoryId: Long? = null,
    val yearMonth: String,
    val limitAmount: Double,
    val alertThresholdPercent: Int = 80,
    val alertSent80: Boolean = false,
    val alertSent100: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
