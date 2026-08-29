package io.github.augustinavicius.nutrition.data.off

import io.github.augustinavicius.nutrition.core.Food
import io.github.augustinavicius.nutrition.core.FoodSource
import io.github.augustinavicius.nutrition.core.Nutrients

/**
 * Maps an Open Food Facts product onto the app's per-100 g model, which is the basis OFF
 * already reports, so nothing has to be rescaled.
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

    return Food(
        name = name,
        brand = brands?.trim()?.takeIf { it.isNotEmpty() }?.substringBefore(','),
        barcode = barcode,
        per100g = Nutrients(
            kcal = nutriments.kcalPer100g ?: 0.0,
            protein = nutriments.proteinPer100g ?: 0.0,
            carbs = nutriments.carbsPer100g ?: 0.0,
            fat = nutriments.fatPer100g ?: 0.0,
            fiber = nutriments.fiberPer100g,
            sugar = nutriments.sugarPer100g,
            sodiumMg = nutriments.sodiumMgPer100g,
        ),
        source = FoodSource.OPEN_FOOD_FACTS,
        imageUrl = imageUrl,
    )
}
