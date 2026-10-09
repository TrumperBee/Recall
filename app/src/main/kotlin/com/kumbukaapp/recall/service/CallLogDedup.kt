package com.kumbukaapp.recall.service

import com.kumbukaapp.recall.data.model.Recording

/** Pure dedupe rules for call-log sync (unit-testable). */
object CallLogDedup {

    /**
     * Returns only candidates that are not already present in [existing].
     * Two calls are considered the same when the normalized number matches
     * and the start times are within [timeToleranceMs] of each other.
     */
    fun filterNew(
        existing: List<Recording>,
        candidates: List<Recording>,
        timeToleranceMs: Long = 60_000L
    ): List<Recording> {
        return candidates.filter { candidate ->
            existing.none { seen -> isDuplicate(seen, candidate, timeToleranceMs) }
        }
    }

    /** True when the two records describe the same call. */
    fun isDuplicate(
        a: Recording,
        b: Recording,
        timeToleranceMs: Long = 60_000L
    ): Boolean {
        if (a.normalizedNumber != b.normalizedNumber) return false
        return kotlin.math.abs(a.startTime - b.startTime) <= timeToleranceMs
    }

    /** Removes duplicates inside a single batch. */
    fun dedupeBatch(
        candidates: List<Recording>,
        timeToleranceMs: Long = 60_000L
    ): List<Recording> {
        val kept = mutableListOf<Recording>()
        for (candidate in candidates) {
            if (kept.none { isDuplicate(it, candidate, timeToleranceMs) }) {
                kept.add(candidate)
            }
        }
        return kept
    }
}
