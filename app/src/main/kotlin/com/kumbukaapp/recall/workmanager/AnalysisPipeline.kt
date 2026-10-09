package com.kumbukaapp.recall.workmanager

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kumbukaapp.recall.ai.AnalysisProviderFactory
import com.kumbukaapp.recall.ai.AnalysisResult
import com.kumbukaapp.recall.ai.TranscriptionProviderFactory
import com.kumbukaapp.recall.ai.TranscriptionResult
import com.kumbukaapp.recall.data.model.CallAnalysis
import com.kumbukaapp.recall.data.model.Transcript
import com.kumbukaapp.recall.data.repository.RecordingRepository
import com.kumbukaapp.recall.service.CallDatabaseFactory
import java.io.File
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

/** Status of the AI analysis pipeline for a call */
enum class AnalysisStatus {
    PENDING,    // Queued, not yet started
    RUNNING,    // In progress
    DONE,       // Completed successfully
    FAILED      // Failed, may be retried
}

/** Data class holding pipeline status and results */
data class PipelineStatus(
    val status: AnalysisStatus = AnalysisStatus.PENDING,
    val transcription: TranscriptionResult? = null,
    val analysis: AnalysisResult? = null,
    val error: String? = null,
    val retryCount: Int = 0
)

/** Constraints for the analysis pipeline */
val analysisConstraints: Constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
    .setRequiresCharging(false)
    .build()

private fun transcriptJson(result: TranscriptionResult): String {
    val arr = JSONArray()
    for (seg in result.segments) {
        arr.put(
            JSONObject()
                .put("startMs", seg.startMs)
                .put("endMs", seg.endMs)
                .put("text", seg.text)
        )
    }
    return arr.toString()
}

/** Transcribe the call's audio and store the transcript. Returns true on success. */
private suspend fun runTranscription(
    context: Context,
    recordingId: Int,
    languageHint: String?
): Boolean {
    val db = CallDatabaseFactory.getInstance(context)
    val repo = RecordingRepository(db)

    val recording = repo.getCallById(recordingId) ?: return false
    val path = recording.recordingPath ?: return false
    val audioFile = File(path)
    if (!audioFile.exists()) return false

    return try {
        val provider = TranscriptionProviderFactory.createProvider()
        val result = provider.transcribe(audioFile, languageHint)
        val text = result.segments.joinToString(" ") { it.text }

        db.transcriptDao().insert(
            Transcript(
                callId = recordingId,
                text = text,
                segments = transcriptJson(result),
                language = result.detectedLanguage,
                status = "DONE",
                provider = "on_device_whisper"
            )
        )
        true
    } catch (e: Exception) {
        db.transcriptDao().insert(
            Transcript(
                callId = recordingId,
                text = "",
                segments = "[]",
                language = languageHint ?: "en",
                status = "FAILED",
                provider = "on_device_whisper"
            )
        )
        false
    }
}

/** Analyze the stored transcript and store the analysis. Returns true on success. */
private suspend fun runAnalysis(context: Context, recordingId: Int): Boolean {
    val db = CallDatabaseFactory.getInstance(context)
    val repo = RecordingRepository(db)

    val recording = repo.getCallById(recordingId) ?: return false
    val transcript = db.transcriptDao().getByCallId(recordingId) ?: return false
    if (transcript.status != "DONE" || transcript.text.isBlank()) return false

    return try {
        val analyzer = AnalysisProviderFactory.createProvider()
        val analysis = analyzer.analyze(transcript.text, Date(recording.startTime))

        db.callAnalysisDao().insert(
            CallAnalysis(
                callId = recordingId,
                summary = analysis.summary,
                promises = JSONArray().also { arr ->
                    analysis.promises.forEach { p ->
                        arr.put(
                            JSONObject()
                                .put("who", p.who)
                                .put("what", p.what)
                                .put("dueText", p.dueText)
                                .put("dueDate", p.dueDate ?: "")
                        )
                    }
                }.toString(),
                dates = JSONArray().also { arr ->
                    analysis.dates.forEach { d ->
                        arr.put(
                            JSONObject()
                                .put("originalText", d.originalText)
                                .put("resolvedDate", d.resolvedDate?.time ?: -1L)
                                .put("relativePhrase", d.relativePhrase ?: "")
                        )
                    }
                }.toString(),
                amounts = JSONArray().also { arr ->
                    analysis.amounts.forEach { a ->
                        arr.put(
                            JSONObject()
                                .put("value", a.value)
                                .put("currency", a.currency)
                                .put("context", a.context ?: "")
                        )
                    }
                }.toString(),
                locations = JSONArray(analysis.locations).toString(),
                people = analysis.peopleOrOrganizations.joinToString(", "),
                status = "DONE",
                analyzedAt = System.currentTimeMillis(),
                provider = "on_device"
            )
        )
        true
    } catch (e: Exception) {
        false
    }
}

/** Worker that transcribes an audio file using the transcription provider */
class TranscriptionWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private val recordingId = inputData.getInt("recordingId", -1)
    private val languageHint = inputData.getString("languageHint")

    override suspend fun doWork(): Result {
        if (recordingId < 0) return Result.failure()
        val ok = runTranscription(applicationContext, recordingId, languageHint)
        return if (ok) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

/** Worker that analyzes an existing transcript */
class AnalysisWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private val recordingId = inputData.getInt("recordingId", -1)

    override suspend fun doWork(): Result {
        if (recordingId < 0) return Result.failure()
        val ok = runAnalysis(applicationContext, recordingId)
        return if (ok) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

/** Worker that runs the full pipeline: transcribe then analyze */
class AnalysisPipelineWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private val recordingId = inputData.getInt("recordingId", -1)
    private val languageHint = inputData.getString("languageHint")
    private val force = inputData.getBoolean("force", false)

    override suspend fun doWork(): Result {
        if (recordingId < 0) return Result.failure()
        val db = CallDatabaseFactory.getInstance(applicationContext)

        // Step 1: Transcribe (skip if a DONE transcript already exists, unless forced)
        val existingTranscript = db.transcriptDao().getByCallId(recordingId)
        if (force || existingTranscript == null || existingTranscript.status != "DONE") {
            val ok = runTranscription(applicationContext, recordingId, languageHint)
            if (!ok && runAttemptCount >= 3) return Result.failure()
        }

        // Step 2: Analyze (skip if a DONE analysis already exists, unless forced)
        if (force || db.callAnalysisDao().getByCallId(recordingId)?.status != "DONE") {
            val ok = runAnalysis(applicationContext, recordingId)
            if (!ok) {
                // No transcript to analyze (e.g., transcription failed) - retry later
                if (runAttemptCount < 3) return Result.retry() else return Result.failure()
            }
        }

        return Result.success()
    }

    companion object {
        fun inputData(recordingId: Int, languageHint: String?, force: Boolean = false) =
            workDataOf(
                "recordingId" to recordingId,
                "languageHint" to languageHint,
                "force" to force
            )
    }
}
