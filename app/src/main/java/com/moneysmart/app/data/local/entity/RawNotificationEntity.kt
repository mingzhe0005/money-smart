package com.moneysmart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class NotificationProcessStatus(val displayName: String) {
    PENDING("Pending"),
    PROCESSING("Processing"),
    RECORDED("Recorded"),
    IGNORED("Ignored"),
    DUPLICATE("Duplicate"),
    FAILED("Failed")
}

@Serializable
@Entity(tableName = "raw_notifications")
data class RawNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val status: NotificationProcessStatus = NotificationProcessStatus.PENDING,
    val aiResponseJson: String? = null,
    val errorMessage: String? = null,
    val linkedTransactionId: Long? = null,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
