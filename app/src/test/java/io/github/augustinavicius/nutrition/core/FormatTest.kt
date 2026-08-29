package io.github.augustinavicius.nutrition.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormatTest {

    @Test
    fun `amounts drop meaningless trailing zeros`() {
        assertEquals("1", Format.amount(1.0))
        assertEquals("1.5", Format.amount(1.50))
        assertEquals("0.13", Format.amount(0.125))
        assertEquals("0", Format.amount(0.0))
    }

    @Test
    fun `amounts survive a round trip back through parsing`() {
        listOf(0.5, 12.25, 100.0, 3.3).forEach { value ->
            assertEquals(value, Format.amount(value).toDouble(), 0.01)
        }
    }

    @Test
    fun `non-finite input does not produce garbage`() {
        assertEquals("0", Format.amount(Double.NaN))
        assertEquals("0", Format.amount(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `nearby days get names instead of dates`() {
        val today = LocalDate.of(2026, 3, 14)
        assertEquals("Today", Format.day(today, today))
        assertEquals("Yesterday", Format.day(today.minusDays(1), today))
        assertEquals("Tomorrow", Format.day(today.plusDays(1), today))
    }

    @Test
    fun `byte counts pick a sensible unit`() {
        assertEquals("512 B", Format.bytes(512))
        assertEquals("2 KB", Format.bytes(2048))
        assertEquals("1.5 MB", Format.bytes(1_572_864))
    }

    @Test
    fun `missing values render as a dash rather than zero`() {
        assertEquals("–", Format.grams(null))
        assertEquals("–", Format.milligrams(null))
    }
}
