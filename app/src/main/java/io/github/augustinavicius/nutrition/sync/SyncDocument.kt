package io.github.augustinavicius.nutrition.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The whole library as it travels between devices: one JSON document holding every food and
 * recipe, plus tombstones for the ones that were deleted.
 *
 * The diary deliberately stays on the device that recorded it. What you ate on a given day is
 * append-heavy, rarely edited, and far more awkward to merge than a library of definitions.
 */
/**
 * Settings for the library document specifically.
 *
 * Defaults are written out rather than omitted, so the file always carries its own schema
 * version and an empty library still says so. It is pretty-printed because it lives on the
 * user's own server, where they may well open or diff it.
 */
val SyncJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
    prettyPrint = true
}

@Serializable
data class SyncDocument(
    val schema: Int = SCHEMA_VERSION,
    val updatedAt: Long = 0,
    val foods: List<SyncFood> = emptyList(),
    val recipes: List<SyncRecipe> = emptyList(),
    val deletions: List<SyncDeletion> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class SyncNutrients(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val sodiumMg: Double? = null,
)

@Serializable
data class SyncFood(
    val uid: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val source: String,
    val imageUrl: String? = null,
    val per100g: SyncNutrients,
    val updatedAt: Long,
)

@Serializable
data class SyncIngredient(
    val name: String,
    val brand: String? = null,
    val grams: Double,
    val per100g: SyncNutrients,
    /** Points at a food by uid, since row ids mean nothing on another device. */
    val foodUid: String? = null,
)

@Serializable
data class SyncRecipe(
    val uid: String,
    val name: String,
    val cookedGrams: Double? = null,
    val ingredients: List<SyncIngredient> = emptyList(),
    /** The food this recipe maintains, by uid, so the link survives the trip. */
    val foodUid: String? = null,
    val updatedAt: Long,
)

@Serializable
data class SyncDeletion(
    val kind: String,
    val uid: String,
    val deletedAt: Long,
)
