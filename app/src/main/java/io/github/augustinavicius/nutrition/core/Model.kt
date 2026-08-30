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

enum class FoodSource { CUSTOM, OPEN_FOOD_FACTS, RECIPE }

/**
 * A food as the app knows it.
 *
 * Everything is measured in grams. Nutrients are stored per 100 g, which is how packaging and
 * food databases state them, and any logged amount is scaled from that.
 */
data class Food(
    val id: Long = 0,
    /** Stable across devices; the row id is not. Assigned on first save. */
    val uid: String = "",
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val per100g: Nutrients,
    val source: FoodSource = FoodSource.CUSTOM,
    val imageUrl: String? = null,
    val favorite: Boolean = false,
    val lastUsedAt: Long? = null,
    val useCount: Int = 0,
) {
    val displayName: String get() = if (brand.isNullOrBlank()) name else "$name · $brand"

    /** Nutrients for [grams] of this food. */
    fun forGrams(grams: Double): Nutrients = per100g * (grams / 100.0)
}

data class DiaryEntry(
    val id: Long = 0,
    val date: LocalDate,
    val meal: MealType,
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val grams: Double,
    val per100g: Nutrients,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val total: Nutrients get() = per100g * (grams / 100.0)

    val amountLabel: String get() = "${Format.amount(grams)} g"
}

/** Daily targets. Macro targets are in grams and may be left unset. */
data class Goals(
    val kcal: Int = 2000,
    val proteinG: Int? = 120,
    val carbsG: Int? = 220,
    val fatG: Int? = 65,
)
