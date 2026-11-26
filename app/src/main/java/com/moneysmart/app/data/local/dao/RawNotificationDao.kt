package com.moneysmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneysmart.app.data.local.entity.RawNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawNotificationDao {
    @Query("SELECT * FROM raw_notifications ORDER BY postedAt DESC, id DESC LIMIT 200")
    fun observeRecentNotifications(): Flow<List<RawNotificationEntity>>

    @Query("SELECT COUNT(*) FROM raw_notifications WHERE status IN ('PENDING', 'PROCESSING')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM raw_notifications WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RawNotificationEntity?

    @Query("SELECT * FROM raw_notifications WHERE status = 'PENDING' ORDER BY postedAt ASC, id ASC LIMIT :limit")
    suspend fun getPendingNotifications(limit: Int = 50): List<RawNotificationEntity>

    @Query("SELECT * FROM raw_notifications WHERE status IN ('PENDING', 'FAILED') ORDER BY postedAt ASC, id ASC LIMIT :limit")
    suspend fun getRetryableNotifications(limit: Int = 50): List<RawNotificationEntity>

    @Query("UPDATE raw_notifications SET status = 'PENDING' WHERE status = 'PROCESSING' AND createdAt < :olderThanMillis")
    suspend fun resetStaleProcessing(olderThanMillis: Long)

    @Query("SELECT * FROM raw_notifications ORDER BY id ASC")
    suspend fun getAllNotifications(): List<RawNotificationEntity>

    @Query(
        "SELECT * FROM raw_notifications " +
            "WHERE packageName = :packageName AND title = :title AND text = :text " +
            "AND postedAt >= :sinceMillis LIMIT 1"
    )
    suspend fun findRecentIdentical(
        packageName: String,
        title: String,
        text: String,
        sinceMillis: Long
    ): RawNotificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: RawNotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<RawNotificationEntity>)

    @Update
    suspend fun update(notification: RawNotificationEntity)

    @Query("DELETE FROM raw_notifications")
    suspend fun deleteAll()
}
