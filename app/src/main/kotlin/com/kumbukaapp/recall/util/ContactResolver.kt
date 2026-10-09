package com.kumbukaapp.recall.util

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.kumbukaapp.recall.util.NumberNormalizer

class ContactResolver(private val context: Context) {
    private val cache = mutableMapOf<String, String>().apply {
        // In a real app, this would be persisted across launches via SharedPreferences
    }

    /** Look up the contact display name for a given phone number.
      *  Normalizes the number first, then queries ContactsContract.
      *  Returns the cached result if available, otherwise queries the contacts database.
      */
    fun resolveContactName(rawNumber: String): String {
        // Check cache first
        val normalized = NumberNormalizer.normalize(rawNumber)
        if (cache.containsKey(normalized)) {
            return cache[normalized]!!
        }

        val name = lookupNameFromContacts(normalized, rawNumber)
        cache[normalized] = name
        return name
    }

    /** Core lookup logic using ContactsContract.PhoneLookup. */
    private fun lookupNameFromContacts(normalized: String, rawNumber: String): String {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalized)
        )

        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME
        )

        var displayName = normalized  // fallback: show the number

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                displayName = cursor.getString(nameIndex)
                cursor.close()
            } else {
                // Also try with raw number normalization variants
                displayName = tryLookupWithRawNumber(rawNumber)
            }
        } catch (e: Exception) {
            // Permission not granted or other error - fall back to number
            displayName = rawNumber
        }

        return displayName
    }

    /** Try looking up using the raw number without normalization. */
    private fun tryLookupWithRawNumber(rawNumber: String): String {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(rawNumber)
        )

        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME
        )

        var displayName = rawNumber

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                displayName = cursor.getString(nameIndex)
                cursor.close()
            }
        } catch (e: Exception) {
            // Ignore
        }

        return displayName
    }

    /** Clear the cache (e.g., when contacts are updated). */
    fun clearCache() {
        cache.clear()
    }

    /** Get all normalized numbers from contacts for pre-population. */
    fun getAllCachedNames(): Map<String, String> {
        val result = mutableMapOf<String, String>()

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = context.contentResolver.query(uri, projection, null, null, null)

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: continue
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: continue

                    if (name.isNotBlank() && number.isNotBlank()) {
                        val normalized = NumberNormalizer.normalize(number)
                        if (normalized.isNotBlank() && !result.containsKey(normalized)) {
                            result[normalized] = name
                        }
                    }
                }
            } finally {
                cursor.close()
            }
        }

        return result
    }
}