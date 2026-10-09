package com.kumbukaapp.recall.service

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.kumbukaapp.recall.data.model.Recording
import com.kumbukaapp.recall.data.repository.RecordingRepository
import java.io.File
import kotlin.math.abs

/**
 * Watches a user-selected folder for call recording audio files and matches them to call records.
 * Uses the Storage Access Framework to get persistent URI permission.
 * Matches using: (a) filename number/contact, (b) file modified time vs call time, (c) duration similarity.
 */
class RecorderFolderWatcher(
    private val context: android.content.Context,
    private val repository: RecordingRepository
) {

    // Audio file extensions we care about
    private val supportedExtensions = setOf("m4a", "mp3", "amr", "aac", "wav", "3gp")

    /** File timestamp tolerance for matching (in milliseconds) */
    private val timeToleranceMs = 5 * 60 * 1000 // 5 minutes

    /** Duration tolerance for matching (in seconds) */
    private val durationToleranceSec = 10

    /** The currently selected folder URI, if any (persisted across launches) */
    private var _selectedFolderUri: Uri? = null
    var selectedFolderUri: Uri?
        get() {
            if (_selectedFolderUri == null) {
                _selectedFolderUri = prefs().getString(KEY_FOLDER_URI, null)?.let(Uri::parse)
            }
            return _selectedFolderUri
        }
        set(value) {
            _selectedFolderUri = value
        }

    private fun prefs() =
        context.getSharedPreferences("recall_prefs", android.content.Context.MODE_PRIVATE)

    /** Persistent permission grant for the folder */
    val folderPermissionGranted: Boolean
        get() {
            val uri = selectedFolderUri ?: return false
            return isUriPermissionGranted(uri)
        }

    /** Request code for Storage Access Framework */
    private val REQUEST_OPEN_FOLDER = 1001

    /** Request code for file picker */
    private val REQUEST_PICK_FILE = 1002

    /** Folder help text for onboarding/settings */
    val folderHelpText = "Open your phone's Phone app > Settings > Call recording to turn it on and find the folder."

    /** Request the user to select their call recording folder via Storage Access Framework */
    fun requestSelectFolder() {
        // Launched from an Activity via ACTION_OPEN_DOCUMENT_TREE; no storage permission needed.
    }

    /** Handle the result from the folder selection intent */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Uri? {
        return when (requestCode) {
            REQUEST_OPEN_FOLDER -> {
                if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                    val uri = data.data
                    uri?.let { saveFolderUri(it) }
                    uri
                } else {
                    null
                }
            }
            REQUEST_PICK_FILE -> {
                if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                    data.data
                } else {
                    null
                }
            }
            else -> null
        }
    }

    companion object {
        private const val KEY_FOLDER_URI = "folder_uri"
    }

    /** Save the selected folder URI for future use */
    fun saveFolderUri(uri: Uri) {
        selectedFolderUri = uri
        prefs().edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
        persistFolderPermission(uri)
    }

    /** Check if the folder URI permission is still valid */
    private fun isUriPermissionGranted(uri: Uri): Boolean {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Persist the folder URI permission */
    private fun persistFolderPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Permission persist failed
        }
    }

    /** Scan the selected folder for audio files and attempt to match them to calls */
    suspend fun scanAndMatchCalls(): ScanResults {
        val uri = selectedFolderUri ?: return ScanResults(emptyList(), emptyList())
        if (!folderPermissionGranted) return ScanResults(emptyList(), emptyList())
        return scanFolderForFiles(uri)
    }

    /** Scan a folder tree URI for audio files and return match results */
    private suspend fun scanFolderForFiles(folderUri: Uri): ScanResults {
        val results = mutableListOf<MatchResult>()
        val unmatched = mutableListOf<UnmatchedFile>()
        val resolver = context.contentResolver

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )

        val cursor = resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        ) ?: return ScanResults(results, unmatched)

        if (!cursor.moveToFirst()) {
            cursor.close()
            return ScanResults(results, unmatched)
        }

        try {
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modifiedIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            do {
                val displayName = cursor.getString(nameIdx) ?: continue
                val dateModified = cursor.getLong(modifiedIdx)

                if (displayName.isBlank()) continue

                val extension = displayName.substringAfterLast(".", "").lowercase()
                if (!supportedExtensions.contains(extension)) continue

                val file = File(folderUri.path + "/" + displayName)
                val duration = 0L // duration is not exposed by DocumentsContract

                // Try to match this file to a call
                val matchResult = attemptMatchFile(displayName, dateModified, duration, file)
                if (matchResult != null) {
                    results.add(matchResult)
                } else {
                    unmatched.add(
                        UnmatchedFile(
                            file = file,
                            filename = displayName,
                            suggestedCalls = determineSuggestedCalls(displayName)
                        )
                    )
                }
            } while (cursor.moveToNext())
        } finally {
            cursor.close()
        }

        return ScanResults(results, unmatched)
    }

    /** Determine suggested calls for an unmatched file based on filename patterns */
    private suspend fun determineSuggestedCalls(filename: String): List<Recording> {
        val calls = repository.getAllCalls()
        val suggested = mutableSetOf<Recording>()

        val normalized = filename
            .lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace(".", "")

        for (call in calls) {
            // Check normalized number
            val numNorm = call.normalizedNumber
                .lowercase()
                .replace(" ", "")
                .replace("-", "")
            if (numNorm.isNotBlank() && normalized.contains(numNorm)) {
                suggested.add(call)
            }

            // Check contact name
            val contactName = call.contactName
            if (contactName != null && normalized.contains(contactName.lowercase())) {
                suggested.add(call)
            }
        }

        return suggested.toList()
    }

    /** Attempt to match a file to a call record */
    private suspend fun attemptMatchFile(
        filename: String,
        dateModified: Long,
        duration: Long,
        file: File
    ): MatchResult? {
        // Strategy (a): Match by phone number or contact name in filename
        val nameMatch = tryNameMatch(filename)
        if (nameMatch != null) {
            return MatchResult(
                recording = nameMatch.recording,
                audioFile = file,
                confidence = MatchConfidence.HIGH,
                strategy = "filename_number_match"
            )
        }

        // Strategy (b): Match by file modified time vs call start/end time
        val timeMatch = tryTimeMatch(dateModified, duration)
        if (timeMatch != null) {
            return MatchResult(
                recording = timeMatch.recording,
                audioFile = file,
                confidence = MatchConfidence.MEDIUM,
                strategy = "time_match"
            )
        }

        // Strategy (c): Duration similarity (if we have duration from both file and call)
        if (duration > 0L) {
            val durationMatch = tryDurationMatch(duration)
            if (durationMatch != null) {
                return MatchResult(
                    recording = durationMatch.recording,
                    audioFile = file,
                    confidence = MatchConfidence.MEDIUM,
                    strategy = "duration_match"
                )
            }
        }

        return null
    }

    /** Strategy (a): Try to match by phone number or contact name in filename */
    private suspend fun tryNameMatch(filename: String): NameMatch? {
        val calls = repository.getAllCalls()
        val lowerFilename = filename.lowercase()

        for (call in calls) {
            // Check normalized number in various formats
            val numLower = call.normalizedNumber.lowercase()
            if (numLower.isNotBlank()) {
                val patterns = listOf(
                    numLower,
                    numLower.replace("+", ""),
                    // Without country code: just the subscriber part
                    if (numLower.length > 3) numLower.substring(3) else ""
                )

                for (pattern in patterns) {
                    if (pattern.isNotBlank() && lowerFilename.contains(pattern)) {
                        return NameMatch(recording = call)
                    }
                }
            }

            // Check contact name
            val contactName = call.contactName
            if (contactName != null && contactName.isNotBlank()) {
                if (lowerFilename.contains(contactName.lowercase())) {
                    return NameMatch(recording = call)
                }
            }
        }

        return null
    }

    /** Strategy (b): Try to match by file modified time vs call start/end time */
    private suspend fun tryTimeMatch(dateModified: Long, duration: Long): TimeMatch? {
        val calls = repository.getAllCalls()

        for (call in calls) {
            val callStart = call.startTime
            val callDuration = call.durationSeconds
            val callEnd = callStart + (callDuration * 1000L)

            // Check if the file was modified within the call time window
            val windowStart = callStart - timeToleranceMs
            val windowEnd = callEnd + timeToleranceMs

            if (dateModified in windowStart..windowEnd) {
                return TimeMatch(
                    recording = call,
                    timeDiffMs = dateModified - callStart
                )
            }
        }

        return null
    }

    /** Strategy (c): Try to match by duration similarity */
    private suspend fun tryDurationMatch(fileDuration: Long): DurationMatch? {
        val calls = repository.getAllCalls()

        for (call in calls) {
            val callDurationMs = call.durationSeconds * 1000L
            val diff = abs(fileDuration - callDurationMs)
            if (diff <= (durationToleranceSec * 1000L)) {
                return DurationMatch(
                    recording = call,
                    fileDuration = fileDuration,
                    callDurationMs = callDurationMs
                )
            }
        }

        return null
    }

    /** Data class for name match results */
    data class NameMatch(
        val recording: Recording
    )

    /** Data class for time match results */
    data class TimeMatch(
        val recording: Recording,
        val timeDiffMs: Long = 0
    )

    /** Data class for duration match results */
    data class DurationMatch(
        val recording: Recording,
        val fileDuration: Long,
        val callDurationMs: Long
    )

    /** Data class for match results */
    data class MatchResult(
        val recording: Recording?,     // null if no match
        val audioFile: File?,          // the audio file if matched
        val confidence: MatchConfidence,
        val strategy: String
    )

    /** Confidence levels for matches */
    enum class MatchConfidence {
        HIGH,    // Filename number/contact matched exactly
        MEDIUM,  // File time or duration matched
        LOW      // No good match - needs review
    }

    /** Data class for unmatched files that need manual review */
    data class UnmatchedFile(
        val file: File,
        val filename: String,
        val suggestedCalls: List<Recording> = emptyList()
    )

    /** Results from scanning a folder */
    data class ScanResults(
        val matches: List<MatchResult>,
        val unmatched: List<UnmatchedFile>
    )
}
