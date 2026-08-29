package io.github.augustinavicius.nutrition.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTest {

    private fun ingredient(name: String, grams: Double, kcalPer100g: Double, proteinPer100g: Double = 0.0) =
        RecipeIngredient(
            foodId = null,
            name = name,
            grams = grams,
            per100g = Nutrients(kcalPer100g, proteinPer100g, 0.0, 0.0),
        )

    /** 500 g raw chicken at 120 kcal/100 g plus 200 g raw rice at 358 kcal/100 g. */
    private val chickenAndRice = Recipe(
        name = "Chicken and rice",
        ingredients = listOf(
            ingredient("Chicken breast", 500.0, 120.0, proteinPer100g = 22.5),
            ingredient("Rice, dry", 200.0, 358.0, proteinPer100g = 6.5),
        ),
    )

    @Test
    fun `the dish totals its raw ingredients`() {
        assertEquals(700.0, chickenAndRice.rawGrams, 1e-9)
        assertEquals(600.0 + 716.0, chickenAndRice.total.kcal, 1e-9)
        assertEquals(112.5 + 13.0, chickenAndRice.total.protein, 1e-9)
    }

    @Test
    fun `without a cooked weight portions come off the raw total`() {
        assertEquals(700.0, chickenAndRice.yieldGrams, 1e-9)
        assertEquals(1316.0 / 7.0, chickenAndRice.per100g!!.kcal, 1e-9)
        assertNull(chickenAndRice.yieldRatio)
    }

    @Test
    fun `a dish that loses water is more energy dense per gram`() {
        // Rice soaks up water and chicken loses it; say the pan comes out at 1100 g.
        val cooked = chickenAndRice.copy(cookedGrams = 1100.0)
        assertEquals(1100.0, cooked.yieldGrams, 1e-9)
        assertEquals(1316.0 / 11.0, cooked.per100g!!.kcal, 1e-9)
        // Heavier than raw, so each gram carries less energy than the raw mixture did.
        assertTrue(cooked.per100g!!.kcal < chickenAndRice.per100g!!.kcal)
    }

    @Test
    fun `energy is conserved whatever the dish ends up weighing`() {
        listOf(400.0, 700.0, 1100.0).forEach { cookedGrams ->
            val cooked = chickenAndRice.copy(cookedGrams = cookedGrams)
            val whole = cooked.per100g!! * (cookedGrams / 100.0)
            assertEquals(chickenAndRice.total.kcal, whole.kcal, 1e-9)
            assertEquals(chickenAndRice.total.protein, whole.protein, 1e-9)
        }
    }

    @Test
    fun `a portion of the cooked dish scales from its weight`() {
        val cooked = chickenAndRice.copy(cookedGrams = 1100.0)
        val portion = cooked.per100g!! * (275.0 / 100.0)   // a quarter of the pan
        assertEquals(1316.0 / 4.0, portion.kcal, 1e-6)
    }

    @Test
    fun `the yield ratio reports what happened in the pan`() {
        assertEquals(0.8, chickenAndRice.copy(cookedGrams = 560.0).yieldRatio!!, 1e-9)
        assertEquals(1.5, chickenAndRice.copy(cookedGrams = 1050.0).yieldRatio!!, 1e-9)
    }

    @Test
    fun `a zero cooked weight is treated as not yet weighed`() {
        val zero = chickenAndRice.copy(cookedGrams = 0.0)
        assertEquals(700.0, zero.yieldGrams, 1e-9)
        assertNull(zero.yieldRatio)
    }

    @Test
    fun `a recipe needs a name, an ingredient and a weight before it can be saved`() {
        assertTrue(chickenAndRice.canSave)
        assertFalse(chickenAndRice.copy(name = "  ").canSave)
        assertFalse(chickenAndRice.copy(ingredients = emptyList()).canSave)
        assertFalse(Recipe(name = "Empty", ingredients = emptyList()).canSave)
    }

    @Test
    fun `an empty recipe has no per-100 g figures rather than dividing by zero`() {
        assertNull(Recipe(name = "Nothing").per100g)
    }

    @Test
    fun `unreported nutrients stay unreported across the whole dish`() {
        val withFibre = Recipe(
            name = "Mixed",
            ingredients = listOf(
                ingredient("Known", 100.0, 100.0).let {
                    it.copy(per100g = it.per100g.copy(fiber = 5.0))
                },
                ingredient("Unknown", 100.0, 100.0),
            ),
        )
        // One ingredient reports fibre and the other does not: the known part still counts.
        assertEquals(5.0, withFibre.total.fiber!!, 1e-9)
        assertNull(withFibre.total.sugar)
    }
}
