package com.moneysmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneysmart.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY timestamp DESC, id DESC")
    fun observeAllActiveTransactions(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE isDeleted = 0 AND timestamp >= :startMillis AND timestamp < :endMillis " +
            "ORDER BY timestamp DESC, id DESC"
    )
    fun observeTransactionsInRange(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE isDeleted = 0 AND needsReview = 1")
    fun observeNeedsReviewCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY timestamp DESC, id DESC")
    suspend fun getAllActiveTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY id ASC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE isDeleted = 0 " +
            "AND ABS(amount - :amount) < 0.005 " +
            "AND UPPER(currency) = UPPER(:currency) " +
            "AND timestamp BETWEEN :startMillis AND :endMillis " +
            "ORDER BY timestamp DESC"
    )
    suspend fun findPotentialDuplicates(
        amount: Double,
        currency: String,
        startMillis: Long,
        endMillis: Long
    ): List<TransactionEntity>

    @Query(
        "SELECT COALESCE(SUM(baseAmount), 0.0) FROM transactions " +
            "WHERE isDeleted = 0 AND type = 'EXPENSE' " +
            "AND timestamp >= :startMillis AND timestamp < :endMillis"
    )
    suspend fun getExpensesSumInRange(startMillis: Long, endMillis: Long): Double

    @Query(
        "SELECT COALESCE(SUM(baseAmount), 0.0) FROM transactions " +
            "WHERE isDeleted = 0 AND type = 'EXPENSE' " +
            "AND categoryId IN (:categoryIds) " +
            "AND timestamp >= :startMillis AND timestamp < :endMillis"
    )
    suspend fun getExpensesSumForCategoriesInRange(
        categoryIds: List<Long>,
        startMillis: Long,
        endMillis: Long
    ): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isDeleted = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET needsReview = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markReviewed(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
