package com.kumbukaapp.recall.ai

import com.kumbukaapp.recall.data.model.Recording
import java.io.File

/** Result of transcribing an audio file */
data class TranscriptionResult(
    val segments: List<TranscriptionSegment>,
    val detectedLanguage: String,
    val durationMs: Long
)

/** A single transcription segment with timestamps */
data class TranscriptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/** Interface for transcription providers */
interface TranscriptionProvider {
    /** Transcribe an audio file.
      *  Returns segments with start/end ms + text, plus the detected language.
      *  @param audioFile the audio file to transcribe
      *  @param languageHint optional language hint (e.g., "en", "sw", "sheng")
      *  @throws TranscriptionException if transcription fails
      */
    suspend fun transcribe(audioFile: File, languageHint: String? = null): TranscriptionResult

    /** Check if a language is supported */
    fun isLanguageSupported(languageCode: String): Boolean
}

/** Exception thrown during transcription */
class TranscriptionException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

/** On-device Whisper implementation (stub - connected to native library later) */
object OnDeviceWhisper : TranscriptionProvider {

    override suspend fun transcribe(audioFile: File, languageHint: String?): TranscriptionResult {
        // TODO: Connect to whisper.cpp native library via JNI
        // For now, return stub data
        return TranscriptionResult(
            segments = listOf(
                TranscriptionSegment(startMs = 0, endMs = 5000, text = "...transcribing...")
            ),
            detectedLanguage = languageHint ?: "en",
            durationMs = 30000
        )
    }

    override fun isLanguageSupported(languageCode: String): Boolean {
        return listOf("en", "sw", "sheng").contains(languageCode)
    }
}

/** Factory for creating transcription providers */
object TranscriptionProviderFactory {

    /** Create the configured provider based on settings */
    fun createProvider(): TranscriptionProvider {
        // Default to on-device Whisper (privacy-first)
        return OnDeviceWhisper
    }
}