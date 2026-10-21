package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "compression_records")
data class CompressionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val fileType: String, // "IMAGE" or "PDF"
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val targetSizeBytes: Long,
    val isTargetMet: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val outputFilePath: String? = null,
    val mimeType: String = ""
) {
    val savedBytes: Long
        get() = (originalSizeBytes - compressedSizeBytes).coerceAtLeast(0L)

    val savingsPercentage: Int
        get() {
            if (originalSizeBytes <= 0L) return 0
            val reduction = ((originalSizeBytes - compressedSizeBytes).toDouble() / originalSizeBytes) * 100.0
            return reduction.toInt().coerceIn(0, 100)
        }
}
