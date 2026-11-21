package com.moneysmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneysmart.app.data.local.entity.SplitBillEntity
import com.moneysmart.app.data.local.entity.SplitParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitBillDao {
    @Query("SELECT * FROM split_bills ORDER BY timestamp DESC, id DESC")
    fun observeAllSplitBills(): Flow<List<SplitBillEntity>>

    @Query("SELECT * FROM split_participants ORDER BY id ASC")
    fun observeAllParticipants(): Flow<List<SplitParticipantEntity>>

    @Query("SELECT * FROM split_bills WHERE id = :id LIMIT 1")
    suspend fun getBillById(id: Long): SplitBillEntity?

    @Query("SELECT * FROM split_participants WHERE splitBillId = :billId ORDER BY id ASC")
    suspend fun getParticipantsForBill(billId: Long): List<SplitParticipantEntity>

    @Query("SELECT * FROM split_participants WHERE id = :participantId LIMIT 1")
    suspend fun getParticipantById(participantId: Long): SplitParticipantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: SplitBillEntity): Long

    @Update
    suspend fun updateBill(bill: SplitBillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<SplitParticipantEntity>)

    @Update
    suspend fun updateParticipant(participant: SplitParticipantEntity)

    @Query("DELETE FROM split_participants WHERE splitBillId = :billId")
    suspend fun deleteParticipantsForBill(billId: Long)

    @Query("DELETE FROM split_bills WHERE id = :billId")
    suspend fun deleteBill(billId: Long)
}
