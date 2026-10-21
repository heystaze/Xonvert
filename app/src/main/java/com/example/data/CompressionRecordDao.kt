package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompressionRecordDao {
    @Query("SELECT * FROM compression_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<CompressionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CompressionRecord): Long

    @Query("DELETE FROM compression_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM compression_records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM compression_records")
    suspend fun getCount(): Int
}
