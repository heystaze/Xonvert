package com.example.data

import kotlinx.coroutines.flow.Flow

class CompressionRepository(private val dao: CompressionRecordDao) {
    val allRecords: Flow<List<CompressionRecord>> = dao.getAllRecords()

    suspend fun recordCompression(record: CompressionRecord): Long {
        return dao.insertRecord(record)
    }

    suspend fun deleteRecord(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearHistory() {
        dao.deleteAll()
    }
}
