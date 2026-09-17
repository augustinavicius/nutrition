package io.github.augustinavicius.nutrition.core

/**
 * One raw ingredient in a [Recipe], snapshotted the same way a diary entry is: editing or
 * deleting the underlying food later must not silently rewrite a recipe you already cooked.
 */
data class RecipeIngredient(
    val id: Long = 0,
    val foodId: Long?,
    val name: String,
    val brand: String? = null,
    val grams: Double,
    val per100g: Nutrients,
) {
    val nutrients: Nutrients get() = per100g * (grams / 100.0)
}

/**
 * A dish cooked from raw ingredients.
 *
 * Cooking changes what a dish weighs — water boils off, or is soaked up — but not how much
 * energy is in it. So the nutrition of the finished dish is the sum of its raw ingredients
 * spread over whatever it weighs once cooked. Weigh the pan, put that number in
 * [cookedGrams], and any portion can then be logged by weight.
 *
 * Until it is weighed, the raw total stands in, which is correct for anything not cooked
 * at all — a salad, a smoothie, a lunchbox.
 */
data class Recipe(
    val id: Long = 0,
    val name: String,
    val cookedGrams: Double? = null,
    val ingredients: List<RecipeIngredient> = emptyList(),
    /** The derived food this recipe keeps up to date, so it can be searched and logged. */
    val foodId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val rawGrams: Double get() = ingredients.sumOf { it.grams }

    val total: Nutrients get() = ingredients.map { it.nutrients }.sum()

    /** The weight portions are measured against: what it weighed cooked, else the raw total. */
    val yieldGrams: Double get() = cookedGrams?.takeIf { it > 0 } ?: rawGrams

    val per100g: Nutrients?
        get() = yieldGrams.takeIf { it > 0 }?.let { total * (100.0 / it) }

    /**
     * How much the dish lost or gained in the pan, as a fraction of its raw weight.
     * Null when it has not been weighed, or there is nothing to compare against.
     */
    val yieldRatio: Double?
        get() {
            val cooked = cookedGrams?.takeIf { it > 0 } ?: return null
            return rawGrams.takeIf { it > 0 }?.let { cooked / it }
        }

    val canSave: Boolean
        get() = name.isNotBlank() && ingredients.isNotEmpty() && yieldGrams > 0
}
