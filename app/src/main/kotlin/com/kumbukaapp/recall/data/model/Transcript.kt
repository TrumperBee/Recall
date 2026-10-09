package com.kumbukaapp.recall.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcripts")
data class Transcript(
    @PrimaryKey @ColumnInfo(name = "call_id") val callId: Int = 0,
    @ColumnInfo(name = "text") val text: String = "",
    @ColumnInfo(name = "segments") val segments: String = "[]",
    @ColumnInfo(name = "language") val language: String = "en",
    @ColumnInfo(name = "status") val status: String = "PENDING",
    @ColumnInfo(name = "provider") val provider: String? = null
)
