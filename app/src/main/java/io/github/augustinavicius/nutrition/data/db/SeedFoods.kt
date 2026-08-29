package io.github.augustinavicius.nutrition.data.db

import io.github.augustinavicius.nutrition.core.FoodSource
import io.github.augustinavicius.nutrition.core.Nutrients

/**
 * A small starter pantry so search and the diary are useful before the first scan, and so
 * the app still works with no network. Values are per 100 g of the raw/plain item, rounded
 * from USDA FoodData Central reference entries.
 */
internal object SeedFoods {

    fun all(): List<FoodEntity> = listOf(
        food("Chicken breast, skinless, raw", 120.0, 22.5, 0.0, 2.6, sodiumMg = 45.0),
        food("Chicken thigh, skinless, raw", 121.0, 19.7, 0.0, 4.1, sodiumMg = 86.0),
        food("Beef mince, 5% fat, raw", 137.0, 21.4, 0.0, 5.0, sodiumMg = 66.0),
        food("Pork loin, raw", 143.0, 21.0, 0.0, 6.0, sodiumMg = 50.0),
        food("Salmon, Atlantic, raw", 208.0, 20.4, 0.0, 13.4, sodiumMg = 59.0),
        food("Cod, raw", 82.0, 18.0, 0.0, 0.7, sodiumMg = 54.0),
        food("Tuna, canned in water, drained", 116.0, 25.5, 0.0, 0.8, sodiumMg = 247.0),
        food("Egg, whole, raw", 143.0, 12.6, 0.7, 9.5, sugar = 0.4, sodiumMg = 142.0),
        food("Egg white, raw", 52.0, 10.9, 0.7, 0.2, sugar = 0.7, sodiumMg = 166.0),

        food("Milk, semi-skimmed 2%", 50.0, 3.3, 4.8, 2.0, sugar = 4.8, sodiumMg = 44.0),
        food("Greek yoghurt, 0% fat", 59.0, 10.3, 3.6, 0.4, sugar = 3.2, sodiumMg = 36.0),
        food("Cottage cheese, 2%", 84.0, 11.1, 4.3, 2.3, sugar = 4.1, sodiumMg = 308.0),
        food("Cheddar cheese", 403.0, 24.9, 1.3, 33.1, sugar = 0.5, sodiumMg = 621.0),
        food("Butter", 717.0, 0.9, 0.1, 81.1, sugar = 0.1, sodiumMg = 643.0),

        food("Oats, rolled, dry", 379.0, 13.2, 67.7, 6.5, fiber = 10.1, sugar = 0.9, sodiumMg = 6.0),
        food("Rice, white, dry", 358.0, 6.5, 79.3, 0.5, fiber = 1.0, sugar = 0.1, sodiumMg = 5.0),
        food("Rice, brown, dry", 367.0, 7.9, 76.2, 2.9, fiber = 3.5, sugar = 0.7, sodiumMg = 4.0),
        food("Pasta, dry", 371.0, 13.0, 74.7, 1.5, fiber = 3.2, sugar = 2.7, sodiumMg = 6.0),
        food("Potato, raw", 77.0, 2.0, 17.5, 0.1, fiber = 2.1, sugar = 0.8, sodiumMg = 6.0),
        food("Sweet potato, raw", 86.0, 1.6, 20.1, 0.1, fiber = 3.0, sugar = 4.2, sodiumMg = 55.0),
        food("Bread, wholemeal", 247.0, 13.0, 41.0, 3.4, fiber = 7.0, sugar = 4.3, sodiumMg = 450.0),
        food("Bread, white", 265.0, 9.0, 49.0, 3.2, fiber = 2.7, sugar = 5.0, sodiumMg = 490.0),

        food("Lentils, dry", 352.0, 24.6, 63.4, 1.1, fiber = 10.7, sugar = 2.0, sodiumMg = 6.0),
        food("Chickpeas, canned, drained", 139.0, 7.1, 22.6, 2.6, fiber = 6.4, sugar = 3.8, sodiumMg = 246.0),
        food("Black beans, canned, drained", 114.0, 7.5, 20.4, 0.5, fiber = 8.3, sugar = 0.3, sodiumMg = 264.0),
        food("Tofu, firm", 144.0, 15.8, 4.3, 8.7, fiber = 2.3, sugar = 0.6, sodiumMg = 14.0),

        food("Banana", 89.0, 1.1, 22.8, 0.3, fiber = 2.6, sugar = 12.2, sodiumMg = 1.0),
        food("Apple", 52.0, 0.3, 13.8, 0.2, fiber = 2.4, sugar = 10.4, sodiumMg = 1.0),
        food("Blueberries", 57.0, 0.7, 14.5, 0.3, fiber = 2.4, sugar = 10.0, sodiumMg = 1.0),
        food("Orange", 47.0, 0.9, 11.8, 0.1, fiber = 2.4, sugar = 9.4, sodiumMg = 0.0),
        food("Avocado", 160.0, 2.0, 8.5, 14.7, fiber = 6.7, sugar = 0.7, sodiumMg = 7.0),

        food("Broccoli, raw", 34.0, 2.8, 6.6, 0.4, fiber = 2.6, sugar = 1.7, sodiumMg = 33.0),
        food("Spinach, raw", 23.0, 2.9, 3.6, 0.4, fiber = 2.2, sugar = 0.4, sodiumMg = 79.0),
        food("Carrot, raw", 41.0, 0.9, 9.6, 0.2, fiber = 2.8, sugar = 4.7, sodiumMg = 69.0),
        food("Tomato, raw", 18.0, 0.9, 3.9, 0.2, fiber = 1.2, sugar = 2.6, sodiumMg = 5.0),
        food("Onion, raw", 40.0, 1.1, 9.3, 0.1, fiber = 1.7, sugar = 4.2, sodiumMg = 4.0),

        food("Olive oil", 884.0, 0.0, 0.0, 100.0, sodiumMg = 2.0),
        food("Almonds", 579.0, 21.2, 21.6, 49.9, fiber = 12.5, sugar = 4.4, sodiumMg = 1.0),
        food("Peanut butter", 588.0, 25.1, 20.0, 50.4, fiber = 6.0, sugar = 9.2, sodiumMg = 429.0),
        food("Sugar, white", 387.0, 0.0, 100.0, 0.0, sugar = 100.0, sodiumMg = 0.0),
        food("Honey", 304.0, 0.3, 82.4, 0.0, sugar = 82.1, sodiumMg = 4.0),

        food("Coffee, black, brewed", 1.0, 0.1, 0.0, 0.0, sodiumMg = 2.0),
        food("Beer, lager 5%", 43.0, 0.5, 3.6, 0.0, sugar = 0.0, sodiumMg = 4.0),
        food("Red wine", 85.0, 0.1, 2.6, 0.0, sugar = 0.6, sodiumMg = 4.0),
    )

    private fun food(
        name: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fiber: Double? = null,
        sugar: Double? = null,
        sodiumMg: Double? = null,
    ) = FoodEntity(
        name = name,
        brand = null,
        barcode = null,
        per100g = Nutrients(kcal, protein, carbs, fat, fiber, sugar, sodiumMg),
        source = FoodSource.CUSTOM,
        imageUrl = null,
    )
}
