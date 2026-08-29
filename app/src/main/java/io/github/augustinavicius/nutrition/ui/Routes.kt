package io.github.augustinavicius.nutrition.ui

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Type-safe navigation routes. Dates cross screen boundaries as epoch days and meals as enum
 * names, because navigation arguments have to be primitives.
 */
object Routes {

    /** Key the scanner writes a captured barcode into, on the caller's back-stack entry. */
    const val SCANNED_BARCODE = "scanned_barcode"

    /** Key the search screen writes a chosen food's id into, when picking an ingredient. */
    const val PICKED_FOOD_ID = "picked_food_id"

    @Serializable
    data object Diary

    /**
     * The search / add-food list. Carries the meal and day the result should be logged to,
     * or, in [pickIngredient] mode, hands the chosen food back to a recipe being edited.
     */
    @Serializable
    data class Search(
        val meal: String? = null,
        val dateEpochDay: Long = LocalDate.now().toEpochDay(),
        val pickIngredient: Boolean = false,
    )

    /**
      * The scanner. In [captureOnly] mode it hands the raw digits back to whoever opened it
      * — the food editor filling in a barcode field — instead of resolving the product and
      * navigating on.
      */
    @Serializable
    data class Scan(val captureOnly: Boolean = false)

    @Serializable
    data object Settings

    /** Log a food, or edit an already-logged entry when [entryId] is non-zero. */
    @Serializable
    data class LogEntry(
        val foodId: Long = 0,
        val entryId: Long = 0,
        val dateEpochDay: Long = LocalDate.now().toEpochDay(),
        val meal: String? = null,
    )

    /** Create a recipe ([recipeId] == 0) or edit an existing one. */
    @Serializable
    data class EditRecipe(val recipeId: Long = 0)

    /** Create a food ([foodId] == 0) or edit an existing one. */
    @Serializable
    data class EditFood(
        val foodId: Long = 0,
        val barcode: String? = null,
        val prefillName: String? = null,
        val prefillBrand: String? = null,
    )
}
