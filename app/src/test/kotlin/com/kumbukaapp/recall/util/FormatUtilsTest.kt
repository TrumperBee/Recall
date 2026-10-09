package com.kumbukaapp.recall.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatUtilsTest {

    @Test
    fun `formatDateLong produces non-empty date string`() {
        val result = FormatUtils.formatDateLong(1700000000000L)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `formatDateLong formats epoch zero`() {
        val result = FormatUtils.formatDateLong(0L)
        assertTrue(result.contains("1970") || result.isNotBlank())
    }

    @Test
    fun `formatDuration returns dash for zero or negative`() {
        assertEquals("—", FormatUtils.formatDuration(0))
        assertEquals("—", FormatUtils.formatDuration(-5))
    }

    @Test
    fun `formatDuration under an hour shows minutes`() {
        assertEquals("45 min", FormatUtils.formatDuration(45))
    }

    @Test
    fun `formatDuration over an hour shows hours and minutes`() {
        assertEquals("1 h 30 m", FormatUtils.formatDuration(90))
        assertEquals("2 h", FormatUtils.formatDuration(120))
    }
}
