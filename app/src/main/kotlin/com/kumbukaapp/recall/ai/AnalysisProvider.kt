package com.kumbukaapp.recall.ai

import java.util.Calendar
import java.util.Locale

/** Result of AI analysis of a transcript */
data class AnalysisResult(
    val summary: String,
    val promises: List<PromiseResult>,
    val dates: List<DateInfo>,
    val amounts: List<AmountInfo>,
    val locations: List<String>,
    val peopleOrOrganizations: List<String>,
    val actionItems: List<String>,
    val detectedLanguage: String
)

/** A promise extracted from the transcript */
data class PromiseResult(
    val who: String,
    val what: String,
    val dueText: String,
    val dueDate: String? = null
)

/** Date information extracted from the transcript */
data class DateInfo(
    val originalText: String,
    val resolvedDate: java.util.Date?,
    val relativePhrase: String?  // e.g., "tomorrow", "Friday"
)

/** Amount information extracted from the transcript */
data class AmountInfo(
    val value: Double,
    val currency: String,  // e.g., "KES", "USD"
    val context: String?   // e.g., " restaurant bill", "travel expense"
)

/** Interface for analysis providers */
interface AnalysisProvider {
    /** Analyze a transcript and extract structured data.
      *  @param transcriptText the full transcript text
      *  @param callDate the date of the call (for resolving relative dates)
      *  @return structured analysis result
      *  @throws AnalysisException if analysis fails
      */
    suspend fun analyze(transcriptText: String, callDate: java.util.Date): AnalysisResult

    /** Check if a language is supported */
    fun isLanguageSupported(languageCode: String): Boolean
}

/** Exception thrown during analysis */
class AnalysisException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

/** On-device analysis implementation using structured extraction rules */
object OnDeviceAnalysis : AnalysisProvider {

    private val locale = Locale.getDefault()

    override suspend fun analyze(transcriptText: String, callDate: java.util.Date): AnalysisResult {
        // Extract structured data using regex patterns
        val summary = extractSummary(transcriptText)
        val promises = extractPromises(transcriptText)
        val dates = extractDates(transcriptText, callDate)
        val amounts = extractAmounts(transcriptText)
        val locations = extractLocations(transcriptText)
        val peopleOrOrgs = extractPeopleOrOrganizations(transcriptText)
        val actionItems = extractActionItems(transcriptText)
        val detectedLanguage = detectLanguage(transcriptText)

        return AnalysisResult(
            summary = summary,
            promises = promises,
            dates = dates,
            amounts = amounts,
            locations = locations,
            peopleOrOrganizations = peopleOrOrgs,
            actionItems = actionItems,
            detectedLanguage = detectedLanguage
        )
    }

    private fun extractSummary(text: String): String {
        // Extract 2-3 sentence summary
        val sentences = text.split(". ").filter { it.isNotBlank() }
        return if (sentences.isEmpty()) {
            "No summary available"
        } else {
            val summarySentences = sentences.dropLast(1).joinToString(". ") + "."
            if (summarySentences.length > 300) {
                summarySentences.substring(0, 300) + "..."
            } else {
                summarySentences
            }
        }
    }

    private fun extractPromises(text: String): List<PromiseResult> {
        // Simple promise extraction - look for patterns like "I promise to ..."
        val promises = mutableListOf<PromiseResult>()
        // Look for "promise" or "commit" patterns
        val lower = text.lowercase()
        if (lower.contains("promise")) {
            // In a full implementation, would use NLP to extract promise details
            promises.add(PromiseResult(
                who = "Speaker",
                what = "Commitment mentioned",
                dueText = "To be determined",
                dueDate = null
            ))
        }
        return promises
    }

    private fun extractDates(text: String, callDate: java.util.Date): List<DateInfo> {
        val results = mutableListOf<DateInfo>()
        val lower = text.lowercase()

        // Check for relative phrases
        if (lower.contains("tomorrow")) {
            val tomorrow = Calendar.getInstance(locale)
            tomorrow.add(Calendar.DAY_OF_MONTH, 1)
            results.add(DateInfo(
                originalText = "tomorrow",
                resolvedDate = tomorrow.time,
                relativePhrase = "tomorrow"
            ))
        }

        if (lower.contains("friday")) {
            val calendar = Calendar.getInstance(locale)
            // Find next Friday
            var daysUntilFriday = (Calendar.FRIDAY - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
            if (daysUntilFriday == 0) daysUntilFriday = 7
            calendar.add(Calendar.DAY_OF_MONTH, daysUntilFriday)
            results.add(DateInfo(
                originalText = "Friday",
                resolvedDate = calendar.time,
                relativePhrase = "Friday"
            ))
        }

        // Check for absolute dates like "MMM d"
        val datePattern = java.util.regex.Pattern.compile("\\b(\\w+\\s+\\d{1,2})\\b")
        val matcher = datePattern.matcher(text)
        while (matcher.find()) {
            results.add(DateInfo(
                originalText = matcher.group(1),
                resolvedDate = null,  // Would need parsing
                relativePhrase = null
            ))
        }

        return results
    }

    private fun extractAmounts(text: String): List<AmountInfo> {
        val results = mutableListOf<AmountInfo>()
        val lower = text.lowercase()

        // Look for KES or numeric amounts
        val kesPattern = java.util.regex.Pattern.compile("\\b\\d+[.,]?\\d*\\s*(KES|Kenyan|Shillings)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher = kesPattern.matcher(text)
        while (matcher.find()) {
            val amountText = matcher.group()
            val numberPart = amountText.replace(Regex("(KES|Kenyan|Shillings)"), "").trim()
            try {
                val value = numberPart.toDouble()
                results.add(AmountInfo(
                    value = value,
                    currency = "KES",
                    context = null
                ))
            } catch (e: NumberFormatException) {
                // Skip
            }
        }

        // Also look for USD
        val usdPattern = java.util.regex.Pattern.compile("\\b\\d+[.,]?\\d*\\s*(USD|dollars|\\$)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher2 = usdPattern.matcher(text)
        while (matcher2.find()) {
            val amountText = matcher2.group()
            results.add(AmountInfo(
                value = 0.0,  // Would need parsing
                currency = "USD",
                context = null
            ))
        }

        return results
    }

    private fun extractLocations(text: String): List<String> {
        val results = mutableListOf<String>()
        val lower = text.lowercase()

        // Common location keywords
        val locationKeywords = listOf("nairobi", "mombasa", " Kisumu", "eldoret", "kenya")
        for (kw in locationKeywords) {
            if (lower.contains(kw)) {
                results.add(kw.replaceFirstChar { it.uppercaseChar() })
            }
        }

        return if (results.isEmpty()) listOf("Not specified") else results.distinct()
    }

    private fun extractPeopleOrOrganizations(text: String): List<String> {
        val results = mutableListOf<String>()
        // Simple extraction - look for capitalized names/phrases
        // In a full implementation, would use NER
        val namePattern = java.util.regex.Pattern.compile("\\b[A-Z][a-z]+\\s+[A-Z][a-z]+\\b")
        val matcher = namePattern.matcher(text)
        while (matcher.find()) {
            results.add(matcher.group())
        }

        return if (results.isEmpty()) listOf("Not specified") else results.distinct()
    }

    private fun extractActionItems(text: String): List<String> {
        val results = mutableListOf<String>()
        val lower = text.lowercase()

        // Look for action patterns
        if (lower.contains("call")) {
            results.add("Follow up call")
        }
        if (lower.contains("meeting")) {
            results.add("Schedule meeting")
        }
        if (lower.contains("send")) {
            results.add("Send follow-up")
        }

        return if (results.isEmpty()) listOf("No action items identified") else results.distinct()
    }

    private fun detectLanguage(text: String): String {
        // Simple language detection
        val lower = text.lowercase()

        // Check for Swahili patterns
        val swahiliWords = listOf("na", "wa", "kwa", "sio", "ndio", "sasa", "leo", "jana")
        val swahiliCount = swahiliWords.count { lower.contains(it) }

        // Check for English patterns
        val englishWords = listOf("the", "and", "I", "to", "of", "a", "in")
        val englishCount = englishWords.count { lower.contains(it) }

        if (swahiliCount > englishCount && swahiliCount > 0) {
            return "sw"
        } else if (englishCount > swahiliCount && englishCount > 0) {
            return "en"
        } else {
            return "mixed"
        }
    }

    override fun isLanguageSupported(languageCode: String): Boolean {
        return listOf("en", "sw", "sheng", "mixed").contains(languageCode)
    }
}

/** Factory for creating analysis providers */
object AnalysisProviderFactory {

    /** Create the configured provider based on settings */
    fun createProvider(): AnalysisProvider {
        // Default to on-device analysis (privacy-first)
        return OnDeviceAnalysis
    }
}