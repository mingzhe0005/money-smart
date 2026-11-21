package com.moneysmart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class CategoryType(val displayName: String) {
    EXPENSE("Expense"),
    INCOME("Income")
}

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: CategoryType,
    val parentId: Long? = null,
    val iconName: String = "Category",
    val colorHex: Long = 0xFF2563EBL,
    val sortOrder: Int = 0
)
