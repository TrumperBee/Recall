package com.kumbukaapp.recall.util

object NumberNormalizer {
    /** Normalize a phone number to E.164-like format for database storage and lookup.
      * Keeps leading + if present, strips all other non-digits, handles '0' trunk prefix.
      * Example: "0712 345 678" → "+254712345678" (Kenya format assumption)
      * Example: "+254712345678" → "+254712345678"
      * Example: "712345678" → "+254712345678"
      */
    fun normalize(number: String?): String {
        if (number == null || number.isBlank()) return ""

        val cleaned = number.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")

        // If already has + prefix, just strip non-digits after it
        if (cleaned.startsWith("+")) {
            val digits = cleaned.replace("+", "").replace(" ", "")
            return if (digits.isNotEmpty()) "+$digits" else ""
        }

        // Strip all non-digits
        val digits = cleaned.filter { it.isDigit() }

        if (digits.isEmpty()) return ""

        // Handle Kenyan/trunk prefix: 07... → +2547...
        // Also handle other common Africa/Middle East prefixes
        return when {
            digits.startsWith("0") && digits.length >= 9 -> {
                // Assume regional format like 0712... → +254712...
                val regionCode = digits.substring(1, 3)  // e.g., "71"
                val rest = digits.substring(2)            // e.g., "2345678"
                "+254$regionCode$rest"
            }
            digits.startsWith("254") -> "+$digits"
            digits.startsWith("0") -> {
                // Generic: strip leading 0, add +
                "+${digits.substring(1)}"
            }
            else -> {
                // Unknown format, just prepend +
                "+$digits"
            }
        }
    }

    /** Parse a normalized number to extract country code and subscriber number. */
    data class ParsedNumber(
        val countryCode: String,
        val subscriberNumber: String
    )

    fun parseNormalized(normalized: String): ParsedNumber {
        val trimmed = normalized.trimStart('+')
        val separatorIndex = trimmed.indexOf('.')  // some formats use . as separator
        val code = if (separatorIndex > 0) trimmed.substring(0, separatorIndex) else trimmed.take(3)
        val subscriber = if (separatorIndex > 0) trimmed.substring(separatorIndex + 1) else trimmed.drop(3)
        return ParsedNumber(countryCode = code, subscriberNumber = subscriber)
    }
}