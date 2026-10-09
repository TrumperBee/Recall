package com.kumbukaapp.recall.util

import org.junit.Assert.*
import org.junit.Test

class NumberNormalizerTest {

    @Test
    fun `normalize already in E164 format preserves the number`() {
        val result = NumberNormalizer.normalize("+254712345678")
        assertEquals("+254712345678", result)
    }

    @Test
    fun `normalize Kenyan trunk prefix 07xx converts to plus 2547`() {
        val result = NumberNormalizer.normalize("0712345678")
        assertTrue(result.startsWith("+2547"))
    }

    @Test
    fun `normalize number with spaces strips whitespace`() {
        val result = NumberNormalizer.normalize("0712 345 678")
        assertTrue(result.startsWith("+2547"))
        assertFalse(result.contains(" "))
    }

    @Test
    fun `normalize empty string returns empty`() {
        val result = NumberNormalizer.normalize("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `normalize null returns empty`() {
        val result = NumberNormalizer.normalize(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseNormalized extracts country code and subscriber`() {
        val parsed = NumberNormalizer.parseNormalized("+254712345678")
        assertEquals("254", parsed.countryCode)
        assertEquals("712345678", parsed.subscriberNumber)
    }

    @Test
    fun `parseNormalized without plus sign`() {
        val parsed = NumberNormalizer.parseNormalized("254712345678")
        assertEquals("254", parsed.countryCode)
        assertEquals("712345678", parsed.subscriberNumber)
    }
}