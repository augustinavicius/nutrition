package io.github.augustinavicius.nutrition.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodTest {

    private val bar = Food(
        name = "Protein bar",
        per100g = Nutrients(kcal = 350.0, protein = 33.3, carbs = 35.0, fat = 10.0),
    )

    @Test
    fun `an arbitrary gram amount scales linearly`() {
        assertEquals(210.0, bar.forGrams(60.0).kcal, 1e-9)
        assertEquals(3.5, bar.forGrams(1.0).kcal, 1e-9)
        assertEquals(350.0, bar.forGrams(100.0).kcal, 1e-9)
    }

    @Test
    fun `zero grams contributes nothing`() {
        assertEquals(0.0, bar.forGrams(0.0).kcal, 1e-9)
    }

    @Test
    fun `optional nutrients survive scaling, and unknown ones stay unknown`() {
        val withFibre = bar.copy(per100g = bar.per100g.copy(fiber = 6.0, sugar = null))
        assertEquals(3.0, withFibre.forGrams(50.0).fiber!!, 1e-9)
        assertNull(withFibre.forGrams(50.0).sugar)
    }

    @Test
    fun `display name includes the brand only when there is one`() {
        assertEquals("Protein bar", bar.displayName)
        assertEquals("Protein bar · Acme", bar.copy(brand = "Acme").displayName)
        assertEquals("Protein bar", bar.copy(brand = "  ").displayName)
    }
}
