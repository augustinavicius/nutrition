package io.github.augustinavicius.nutrition.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutrientsTest {

    private val base = Nutrients(kcal = 100.0, protein = 10.0, carbs = 20.0, fat = 5.0, fiber = 2.0)

    @Test
    fun `scaling multiplies every reported nutrient`() {
        val doubled = base * 2.0
        assertEquals(200.0, doubled.kcal, EPSILON)
        assertEquals(20.0, doubled.protein, EPSILON)
        assertEquals(4.0, doubled.fiber!!, EPSILON)
    }

    @Test
    fun `scaling leaves unreported nutrients unreported`() {
        assertNull((base * 3.0).sugar)
    }

    @Test
    fun `adding two unknowns stays unknown`() {
        val sum = base + base
        assertNull(sum.sugar)
        assertEquals(4.0, sum.fiber!!, EPSILON)
    }

    @Test
    fun `adding a known to an unknown treats the unknown as zero`() {
        val withSugar = base.copy(sugar = 7.0)
        val sum = base + withSugar
        assertEquals(7.0, sum.sugar!!, EPSILON)
    }

    @Test
    fun `summing an empty list yields zero rather than nulls`() {
        val total = emptyList<Nutrients>().sum()
        assertEquals(0.0, total.kcal, EPSILON)
        assertNull(total.fiber)
    }

    @Test
    fun `energy from macros uses Atwater factors`() {
        assertEquals(10 * 4 + 20 * 4 + 5 * 9.0, base.kcalFromMacros, EPSILON)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
