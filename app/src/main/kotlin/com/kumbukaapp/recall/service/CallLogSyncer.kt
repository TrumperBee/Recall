package com.kumbukaapp.recall.service

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import com.kumbukaapp.recall.data.model.Recording
import com.kumbukaapp.recall.data.repository.RecordingRepository
import com.kumbukaapp.recall.util.NumberNormalizer
import com.kumbukaapp.recall.util.Persistence
import timber.log.Timber

class CallLogSyncer(
    private val context: Context,
    private val repository: RecordingRepository
) {

    private val NUMBER = CallLog.Calls.NUMBER
    private val TYPE = CallLog.Calls.TYPE
    private val DATE = CallLog.Calls.DATE
    private val DURATION = CallLog.Calls.DURATION

    /** Reference to the folder watcher - set via setter for loose coupling */
    var folderWatcher: RecorderFolderWatcher? = null

    suspend fun syncAll() {
        syncSince(0L)
    }

    suspend fun syncSince(lastSyncTimestamp: Long) {
        val newCalls = mutableListOf<Recording>()
        val resolver = context.contentResolver

        // Build selection: only calls newer than last sync
        val selection = if (lastSyncTimestamp > 0L) {
            "$DATE > $lastSyncTimestamp"
        } else {
            "1"  // first sync - import everything
        }

        val projection = arrayOf(NUMBER, TYPE, DATE, DURATION)
        val sort = "$DATE DESC"

        val cursor: Cursor? = resolver.query(CallLog.Calls.CONTENT_URI, projection, selection, null, sort)

        if (cursor == null || cursor.count == 0) {
            // No new calls, but update the last sync timestamp
            Persistence.saveLastSync(context, System.currentTimeMillis())
            // Also scan for recordings after sync
            triggerRecordingScan()
            return
        }

        try {
            while (cursor.moveToNext()) {
                val rawNumber = cursor.getString(cursor.getColumnIndexOrThrow(NUMBER))
                val callType = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE))
                val callDate = cursor.getLong(cursor.getColumnIndexOrThrow(DATE))
                val duration = cursor.getInt(cursor.getColumnIndexOrThrow(DURATION))

                val normalized = NumberNormalizer.normalize(rawNumber)

                val recording = Recording(
                    normalizedNumber = normalized,
                    rawNumber = rawNumber ?: "",
                    contactName = null,
                    direction = when (callType) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        else -> "unknown"
                    },
                    startTime = callDate,
                    durationSeconds = duration,
                    recordingPath = null,
                    recordingSource = "NONE",
                    subscriptionId = null,
                    createdAt = System.currentTimeMillis()
                )

                newCalls.add(recording)
            }
        } finally {
            cursor.close()
        }

        // Insert only calls we don't already have (dedupe against DB + within batch)
        val existing = repository.getAllCalls()
        val batch = CallLogDedup.dedupeBatch(newCalls)
        val toInsert = CallLogDedup.filterNew(existing, batch)
        for (call in toInsert) {
            repository.insertCall(call)
        }
        if (toInsert.isNotEmpty()) {
            Timber.d("Inserted %d new calls (filtered %d duplicates)", toInsert.size, newCalls.size - toInsert.size)
        }

        // Update last sync timestamp
        Persistence.saveLastSync(context, System.currentTimeMillis())

        // Trigger recording folder scan after sync
        triggerRecordingScan()
    }

    /** Trigger the recorder folder scan after a call sync */
    private fun triggerRecordingScan() {
        val watcher = folderWatcher
        if (watcher != null && watcher.folderPermissionGranted && watcher.selectedFolderUri != null) {
            Timber.d("Recorder folder available: ${watcher.selectedFolderUri}")
        }
    }
}
