package com.kumbukaapp.recall.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "calls")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "normalized_number") val normalizedNumber: String,
    @ColumnInfo(name = "raw_number") val rawNumber: String,
    @ColumnInfo(name = "contact_name") val contactName: String?,
    val direction: String,    // INCOMING, OUTGOING, MISSED, REJECTED
    val startTime: Long,      // epoch ms
    val durationSeconds: Int,
    val recordingPath: String?,    // relative path in private app storage
    val recordingSource: String,   // NONE, DEVICE_RECORDER, IMPORTED, MIC
    @ColumnInfo(name = "subscription_id") val subscriptionId: String?,   // SIM slot if available
    val createdAt: Long,      // when this record was created in the app
    var isPlayed: Boolean = false,
    var isStarred: Boolean = false,
    var isDeleted: Boolean = false,
    @ColumnInfo(name = "notes") val notes: String = ""   // Personal notes from user
) {
    val displayName: String
        get() = contactName ?: normalizedNumber
}