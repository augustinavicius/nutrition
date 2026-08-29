package io.github.augustinavicius.nutrition.data.off

import io.github.augustinavicius.nutrition.core.Food
import io.github.augustinavicius.nutrition.core.FoodSource
import io.github.augustinavicius.nutrition.core.Nutrients

/** Pulls a gram/millilitre weight out of free-text serving sizes like "30 g" or "1 cup (240 ml)". */
private val SERVING_GRAMS = Regex("""(\d+(?:[.,]\d+)?)\s*(g|ml)\b""", RegexOption.IGNORE_CASE)

internal fun parseServingGrams(servingSize: String?): Double? {
    val text = servingSize?.takeIf { it.isNotBlank() } ?: return null
    // Prefer the last match: "1 cup (240 ml)" carries the useful number in the parenthetical.
    val match = SERVING_GRAMS.findAll(text).lastOrNull() ?: return null
    return match.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
}

/**
 * Maps an Open Food Facts product onto the app's per-serving model.
 *
 * Returns null only when the product is unusable (no barcode or no name). Products with
 * missing nutrition still map, with zeros, so the scanner can hand them to the editor
 * pre-filled instead of pretending the barcode is unknown.
 */
fun OffProduct.toFood(): Food? {
    val barcode = code?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val name = listOfNotNull(productName, productNameEn, genericName)
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return null

    val servingGrams = servingQuantity?.takeIf { it > 0 }
        ?: parseServingGrams(servingSize)
        ?: DEFAULT_SERVING_GRAMS

    val per100g = Nutrients(
        kcal = nutriments.kcalPer100g ?: 0.0,
        protein = nutriments.proteinPer100g ?: 0.0,
        carbs = nutriments.carbsPer100g ?: 0.0,
        fat = nutriments.fatPer100g ?: 0.0,
        fiber = nutriments.fiberPer100g,
        sugar = nutriments.sugarPer100g,
        sodiumMg = nutriments.sodiumMgPer100g,
    )

    val label = servingSize?.trim()?.takeIf { it.isNotEmpty() }
        ?: "${io.github.augustinavicius.nutrition.core.Format.amount(servingGrams)} g"

    return Food(
        name = name,
        brand = brands?.trim()?.takeIf { it.isNotEmpty() }?.substringBefore(','),
        barcode = barcode,
        servingLabel = label,
        servingGrams = servingGrams,
        perServing = per100g * (servingGrams / 100.0),
        source = FoodSource.OPEN_FOOD_FACTS,
        imageUrl = imageUrl,
    )
}

private const val DEFAULT_SERVING_GRAMS = 100.0
