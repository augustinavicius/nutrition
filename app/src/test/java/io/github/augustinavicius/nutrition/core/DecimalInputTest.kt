package io.github.augustinavicius.nutrition.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecimalInputTest {

    @Test
    fun `both separators state the same number`() {
        assertEquals(DecimalInput.parse("2.6"), DecimalInput.parse("2,6"))
        assertEquals(2.6, DecimalInput.parse("2,6")!!, EPSILON)
    }

    @Test
    fun `the separator the user typed is the one they keep`() {
        assertEquals("2,6", DecimalInput.sanitize("2,6"))
        assertEquals("2.6", DecimalInput.sanitize("2.6"))
    }

    @Test
    fun `a second separator is ignored rather than breaking the value`() {
        assertEquals("2.6", DecimalInput.sanitize("2.6."))
        assertEquals("2,65", DecimalInput.sanitize("2,6.5"))
    }

    @Test
    fun `anything that is not a figure is dropped as it arrives`() {
        assertEquals("12", DecimalInput.sanitize("-1 2g"))
        assertEquals("", DecimalInput.sanitize("abc"))
    }

    @Test
    fun `a bare separator is not yet a number`() {
        assertNull(DecimalInput.parse(","))
        assertNull(DecimalInput.parse(""))
    }

    @Test
    fun `blank is empty rather than invalid for optional fields`() {
        assertNull(DecimalInput.parseOptional("   "))
        assertEquals(0.4, DecimalInput.parseOptional("0,4")!!, EPSILON)
    }

    @Test
    fun `what the formatter writes is what the parser reads back`() {
        val value = 12.35
        assertEquals(value, DecimalInput.parse(Format.amount(value))!!, EPSILON)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
