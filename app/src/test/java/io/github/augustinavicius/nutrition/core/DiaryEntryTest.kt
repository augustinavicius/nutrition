package io.github.augustinavicius.nutrition.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DiaryEntryTest {

    private fun entry(grams: Double) = DiaryEntry(
        date = LocalDate.of(2026, 8, 29),
        meal = MealType.LUNCH,
        foodId = 1,
        name = "Chicken breast",
        brand = null,
        grams = grams,
        per100g = Nutrients(kcal = 120.0, protein = 22.5, carbs = 0.0, fat = 2.6),
    )

    @Test
    fun `the total is the per-100 g figures scaled to the logged weight`() {
        assertEquals(180.0, entry(150.0).total.kcal, 1e-9)
        assertEquals(33.75, entry(150.0).total.protein, 1e-9)
    }

    @Test
    fun `a single gram is a hundredth of the per-100 g figures`() {
        assertEquals(1.2, entry(1.0).total.kcal, 1e-9)
    }

    @Test
    fun `the amount always reads in grams`() {
        assertEquals("150 g", entry(150.0).amountLabel)
        assertEquals("2.5 g", entry(2.5).amountLabel)
    }
}
