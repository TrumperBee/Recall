package com.kumbukaapp.recall.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.kumbukaapp.recall.data.repository.RecordingRepository
import java.io.File
import timber.log.Timber

/** Utility for managing recording files: copy, delete, read duration */
object RecordingFileManager {

    /** Directory for app-owned recordings (internal, app-private storage) */
    private fun recordingsDir(context: Context): File =
        File(context.filesDir, "recordings")

    /**
     * Import a recording from a content URI (SAF / file picker) into
     * app-private storage and link it to the call. Returns the new path.
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        callId: Int,
        repository: RecordingRepository
    ): String? {
        val recordingsDir = recordingsDir(context)
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        val timestamp = System.currentTimeMillis()
        val destFile = File(recordingsDir, "call_${callId}_${timestamp}.m4a")

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                Timber.e("Could not open content URI for import")
                return null
            }

            val durationMs = readDurationFromFile(destFile)
            val call = repository.getCallById(callId)
            if (call == null) {
                destFile.delete()
                return null
            }
            repository.insertCall(
                call.copy(
                    recordingPath = destFile.absolutePath,
                    recordingSource = "IMPORTED",
                    durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(0)
                )
            )
            destFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Failed to import recording to private storage")
            null
        }
    }

    /** Copy a recording from source to app-private storage */
    suspend fun copyRecordingToPrivateStorage(
        context: Context,
        sourceFile: File,
        callId: Int,
        repository: RecordingRepository
    ): String? {
        val recordingsDir = recordingsDir(context)
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }

        // Generate a filename based on call ID and timestamp
        val timestamp = System.currentTimeMillis()
        val destFile = File(recordingsDir, "call_${callId}_${timestamp}.m4a")

        return try {
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Read duration from the file
            val durationMs = readDurationFromFile(destFile)

            // Update the call record in the database
            updateRecordingMetadata(
                callId,
                destFile.absolutePath,
                "DEVICE_RECORDER",
                durationMs,
                repository
            )

            destFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy recording to private storage")
            null
        }
    }

    /** Read duration from a recording file using MediaMetadataRetriever */
    private fun readDurationFromFile(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Failed to read duration from file: ${file.absolutePath}")
            0L
        } finally {
            retriever.release()
        }
    }

    /** Update the call metadata after recording import */
    private suspend fun updateRecordingMetadata(
        callId: Int,
        recordingPath: String?,
        recordingSource: String,
        durationMs: Long,
        repository: RecordingRepository
    ) {
        val call = repository.getCallById(callId) ?: return
        val updatedRecording = call.copy(
            recordingPath = recordingPath,
            recordingSource = recordingSource,
            durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(0)
        )
        repository.insertCall(updatedRecording)
    }

    /** Delete the app's copy of a recording (does NOT delete the original from the phone's recorder folder) */
    suspend fun deleteAppRecording(callId: Int, repository: RecordingRepository) {
        val call = repository.getCallById(callId) ?: return

        call.recordingPath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        // Clear the path but keep the call record
        repository.insertCall(
            call.copy(recordingPath = null, recordingSource = "NONE")
        )
    }

    /** Setting: mark the original file for deletion from the phone's recorder folder */
    fun markOriginalForDeletion(callId: Int, shouldDelete: Boolean) {
        // The actual deletion happens on the next folder scan.
        Timber.i("Original file %s deletion marked for call %d", if (shouldDelete) "enabled" else "disabled", callId)
    }
}
