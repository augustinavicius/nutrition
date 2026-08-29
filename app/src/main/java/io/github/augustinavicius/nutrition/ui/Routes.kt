package io.github.augustinavicius.nutrition.ui

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Type-safe navigation routes. Dates cross screen boundaries as epoch days and meals as enum
 * names, because navigation arguments have to be primitives.
 */
object Routes {

    @Serializable
    data object Diary

    /** The search / add-food list. Carries the meal and day the result should be logged to. */
    @Serializable
    data class Search(
        val meal: String? = null,
        val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    )

    @Serializable
    data object Scan

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

    /** Create a food ([foodId] == 0) or edit an existing one. */
    @Serializable
    data class EditFood(
        val foodId: Long = 0,
        val barcode: String? = null,
        val prefillName: String? = null,
        val prefillBrand: String? = null,
    )
}
