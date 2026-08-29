package io.github.augustinavicius.nutrition.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodTest {

    private val bar = Food(
        name = "Protein bar",
        servingLabel = "1 bar (60 g)",
        servingGrams = 60.0,
        perServing = Nutrients(kcal = 210.0, protein = 20.0, carbs = 21.0, fat = 6.0),
    )

    @Test
    fun `per 100 g is derived from the serving weight`() {
        val per100 = bar.per100g!!
        assertEquals(350.0, per100.kcal, 1e-9)
        assertEquals(20.0 / 0.6, per100.protein, 1e-9)
    }

    @Test
    fun `optional nutrients survive the per 100 g conversion`() {
        val withFibre = bar.copy(perServing = bar.perServing.copy(fiber = 3.0, sugar = null))
        assertEquals(5.0, withFibre.per100g!!.fiber!!, 1e-9)
        assertNull(withFibre.per100g!!.sugar)
    }

    @Test
    fun `foods counted by the piece have no gram view`() {
        assertNull(bar.copy(servingGrams = null).per100g)
    }

    @Test
    fun `a zero serving weight is treated as unknown rather than dividing by zero`() {
        assertNull(bar.copy(servingGrams = 0.0).per100g)
    }

    @Test
    fun `display name includes the brand only when there is one`() {
        assertEquals("Protein bar", bar.displayName)
        assertEquals("Protein bar · Acme", bar.copy(brand = "Acme").displayName)
        assertEquals("Protein bar", bar.copy(brand = "  ").displayName)
    }
}
