package io.github.augustinavicius.nutrition.core

import java.time.LocalDate

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snacks"),
    ;

    companion object {
        /** Best guess for the meal a user is logging right now, used to preselect a chip. */
        fun forHour(hour: Int): MealType = when (hour) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 16..21 -> DINNER
            else -> SNACK
        }
    }
}

enum class FoodSource { CUSTOM, OPEN_FOOD_FACTS }

/**
 * A food as the app knows it.
 *
 * Nutrients are stored **per serving** rather than per 100 g, because that is the only
 * basis that is always well defined: foods bought by the piece (an egg, a protein bar)
 * may have no meaningful gram weight, while every food has a serving. When [servingGrams]
 * is known the per-100 g view is derived from it.
 */
data class Food(
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val servingLabel: String,
    val servingGrams: Double? = null,
    val perServing: Nutrients,
    val source: FoodSource = FoodSource.CUSTOM,
    val imageUrl: String? = null,
    val favorite: Boolean = false,
    val lastUsedAt: Long? = null,
    val useCount: Int = 0,
) {
    val displayName: String get() = if (brand.isNullOrBlank()) name else "$name · $brand"

    val per100g: Nutrients?
        get() = servingGrams?.takeIf { it > 0 }?.let { perServing * (100.0 / it) }
}

data class DiaryEntry(
    val id: Long = 0,
    val date: LocalDate,
    val meal: MealType,
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val servings: Double,
    val servingLabel: String,
    val servingGrams: Double?,
    val perServing: Nutrients,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val total: Nutrients get() = perServing * servings

    val grams: Double? get() = servingGrams?.let { it * servings }

    val amountLabel: String
        get() = grams?.let { "${Format.amount(it)} g" } ?: "${Format.amount(servings)} × $servingLabel"
}

/** Daily targets. Macro targets are in grams and may be left unset. */
data class Goals(
    val kcal: Int = 2000,
    val proteinG: Int? = 120,
    val carbsG: Int? = 220,
    val fatG: Int? = 65,
)
