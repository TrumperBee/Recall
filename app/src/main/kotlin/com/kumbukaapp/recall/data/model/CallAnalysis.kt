package com.kumbukaapp.recall.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_analyses")
data class CallAnalysis(
    @PrimaryKey @ColumnInfo(name = "call_id") val callId: Int = 0,
    @ColumnInfo(name = "summary") val summary: String = "",
    @ColumnInfo(name = "promises") val promises: String = "[]",
    @ColumnInfo(name = "dates") val dates: String = "[]",
    @ColumnInfo(name = "amounts") val amounts: String = "[]",
    @ColumnInfo(name = "locations") val locations: String = "[]",
    @ColumnInfo(name = "people") val people: String = "",
    @ColumnInfo(name = "status") val status: String = "PENDING",
    @ColumnInfo(name = "analyzedAt") val analyzedAt: Long = 0L,
    @ColumnInfo(name = "provider") val provider: String? = null
)
