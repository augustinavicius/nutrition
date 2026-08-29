package io.github.augustinavicius.nutrition.data.off

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Open Food Facts is crowd-sourced, so the same numeric field arrives as a number on one
 * product and as a quoted string ("30", "unknown", "") on the next. Decoding those fields
 * as [JsonPrimitive] accepts both shapes, and [num] turns anything non-numeric into null
 * rather than failing the whole response.
 */
private fun JsonPrimitive?.num(): Double? = this?.content?.trim()?.toDoubleOrNull()

@Serializable
data class OffProductResponse(
    val status: Int = 0,
    @SerialName("status_verbose") val statusVerbose: String? = null,
    val product: OffProduct? = null,
)

@Serializable
data class OffSearchResponse(
    val count: Int = 0,
    val page: Int = 1,
    val products: List<OffProduct> = emptyList(),
)

@Serializable
data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_en") val productNameEn: String? = null,
    @SerialName("generic_name") val genericName: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
    @SerialName("image_front_small_url") val imageFrontSmallUrl: String? = null,
    @SerialName("image_small_url") val imageSmallUrl: String? = null,
    val nutriments: OffNutriments = OffNutriments(),
) {
    val imageUrl: String? get() = imageFrontSmallUrl ?: imageSmallUrl
}

@Serializable
data class OffNutriments(
    @SerialName("energy-kcal_100g") val energyKcalRaw: JsonPrimitive? = null,
    @SerialName("energy-kj_100g") val energyKjRaw: JsonPrimitive? = null,
    @SerialName("energy_100g") val energyRaw: JsonPrimitive? = null,
    @SerialName("proteins_100g") val proteinsRaw: JsonPrimitive? = null,
    @SerialName("carbohydrates_100g") val carbohydratesRaw: JsonPrimitive? = null,
    @SerialName("fat_100g") val fatRaw: JsonPrimitive? = null,
    @SerialName("fiber_100g") val fiberRaw: JsonPrimitive? = null,
    @SerialName("sugars_100g") val sugarsRaw: JsonPrimitive? = null,
    @SerialName("sodium_100g") val sodiumRaw: JsonPrimitive? = null,
    @SerialName("salt_100g") val saltRaw: JsonPrimitive? = null,
) {
    /** Prefers a reported kcal figure, else converts the kJ figure. */
    val kcalPer100g: Double?
        get() = energyKcalRaw.num() ?: (energyKjRaw.num() ?: energyRaw.num())?.let { it / KJ_PER_KCAL }

    val proteinPer100g: Double? get() = proteinsRaw.num()
    val carbsPer100g: Double? get() = carbohydratesRaw.num()
    val fatPer100g: Double? get() = fatRaw.num()
    val fiberPer100g: Double? get() = fiberRaw.num()
    val sugarPer100g: Double? get() = sugarsRaw.num()

    /** OFF reports sodium and salt in **grams**; the app works in milligrams. */
    val sodiumMgPer100g: Double?
        get() = sodiumRaw.num()?.times(1000) ?: saltRaw.num()?.times(1000 / SALT_TO_SODIUM)

    /** True when the product carries enough data to be logged without editing. */
    val hasAnyData: Boolean
        get() = listOf(kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g).any { it != null }
}

private const val KJ_PER_KCAL = 4.184
private const val SALT_TO_SODIUM = 2.5
