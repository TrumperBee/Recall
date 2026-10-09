package com.kumbukaapp.recall.service

import com.kumbukaapp.recall.data.model.Recording
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallLogDedupTest {

    private fun call(id: Int, number: String, duration: Int = 1): Recording = Recording(
        id = id,
        normalizedNumber = number,
        rawNumber = number,
        contactName = null,
        direction = "incoming",
        startTime = 1700000000100L,
        durationSeconds = duration,
        recordingPath = null,
        recordingSource = "NONE",
        subscriptionId = null,
        createdAt = 1700000000000L
    )

    private fun atTime(base: Recording, time: Long) = base.copy(startTime = time)

    @Test
    fun `same number and near-identical start time is a duplicate`() {
        val a = call(1, "+254712345678")
        val b = call(2, "+254712345678", duration = 2)
        assertTrue(CallLogDedup.isDuplicate(atTime(a, 1000L), atTime(b, 1001L)))
    }

    @Test
    fun `different numbers are not duplicates`() {
        val a = call(1, "+254712345678")
        val b = call(2, "+254799999999")
        assertFalse(CallLogDedup.isDuplicate(a, b))
    }

    @Test
    fun `same number far apart in time is not a duplicate`() {
        val a = call(1, "+254712345678")
        val b = call(2, "+254712345678")
        assertFalse(CallLogDedup.isDuplicate(atTime(a, 0L), atTime(b, 1000L * 60 * 60 * 24)))
    }

    @Test
    fun `filterNew drops candidates already present`() {
        val existing = listOf(call(1, "+254712345678"))
        val candidate = atTime(call(2, "+254712345678"), existing[0].startTime + 1000L)
        val result = CallLogDedup.filterNew(existing, listOf(candidate))
        assertEquals(0, result.size)
    }

    @Test
    fun `dedupeBatch removes duplicates inside a single batch`() {
        val a = atTime(call(1, "+254712345678"), 0L)
        val b = atTime(call(2, "+254712345678"), 500L)
        val c = atTime(call(3, "+254799999999"), 0L)
        val result = CallLogDedup.dedupeBatch(listOf(a, b, c))
        assertEquals(2, result.size)
    }
}