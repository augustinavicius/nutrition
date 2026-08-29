package io.github.augustinavicius.nutrition.data.off

import io.github.augustinavicius.nutrition.data.Network
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OffMapperTest {

    @Test
    fun `products map straight onto the per-100 g basis`() {
        val food = product(
            code = "3017620422003",
            name = "Nutella",
            brands = "Ferrero, Nutella",
            nutriments = OffNutriments(
                energyKcalRaw = JsonPrimitive(539),
                proteinsRaw = JsonPrimitive(6.3),
                carbohydratesRaw = JsonPrimitive(57.5),
                fatRaw = JsonPrimitive(30.9),
                sugarsRaw = JsonPrimitive(56.3),
                sodiumRaw = JsonPrimitive(0.0428),
            ),
        ).toFood()!!

        assertEquals(539.0, food.per100g.kcal, 1e-9)
        assertEquals(6.3, food.per100g.protein, 1e-9)
        // Only the first brand is kept; the rest is crowd-sourced noise.
        assertEquals("Ferrero", food.brand)
        // OFF reports sodium in grams.
        assertEquals(42.8, food.per100g.sodiumMg!!, 1e-6)
    }

    @Test
    fun `energy falls back to kilojoules when kcal is missing`() {
        val food = product(nutriments = OffNutriments(energyKjRaw = JsonPrimitive(2255))).toFood()!!
        assertEquals(2255 / 4.184, food.per100g.kcal, 1e-6)
    }

    @Test
    fun `sodium falls back to salt using the standard ratio`() {
        val food = product(nutriments = OffNutriments(saltRaw = JsonPrimitive(0.107))).toFood()!!
        assertEquals(42.8, food.per100g.sodiumMg!!, 1e-3)
    }

    @Test
    fun `numeric fields arrive as strings on some products`() {
        val food = product(nutriments = OffNutriments(energyKcalRaw = JsonPrimitive("250"))).toFood()!!
        assertEquals(250.0, food.per100g.kcal, 1e-9)
    }

    @Test
    fun `non-numeric junk is treated as missing, not as an error`() {
        val food = product(nutriments = OffNutriments(energyKcalRaw = JsonPrimitive("unknown"))).toFood()!!
        assertEquals(0.0, food.per100g.kcal, 1e-9)
    }

    @Test
    fun `products without a code or a name are rejected`() {
        assertNull(product(code = null).toFood())
        assertNull(product(name = null).toFood())
        assertNull(product(name = "   ").toFood())
    }

    @Test
    fun `a real API payload decodes with the production json settings`() {
        val payload = """
            {
              "code": "20047559",
              "status": 1,
              "status_verbose": "product found",
              "product": {
                "code": "20047559",
                "product_name": "Yogurt Greek Style",
                "brands": "Pilos",
                "serving_size": "150 g",
                "serving_quantity": "150",
                "image_front_small_url": "https://images.example/front.jpg",
                "some_unknown_field": {"nested": true},
                "nutriments": {
                  "energy-kcal_100g": 96.1759082217972,
                  "proteins_100g": "8.5",
                  "carbohydrates_100g": 4.1,
                  "fat_100g": 5,
                  "salt_100g": 0.1,
                  "another-unmapped_100g": 1
                }
              }
            }
        """.trimIndent()

        val response = Network.json.decodeFromString(OffProductResponse.serializer(), payload)
        assertEquals(1, response.status)
        val food = response.product!!.toFood()
        assertNotNull(food)
        assertEquals("Yogurt Greek Style", food!!.name)
        assertEquals(96.1759082217972, food.per100g.kcal, 1e-9)
        assertEquals(8.5, food.per100g.protein, 1e-9)
        // Serving fields are no longer requested or read, and must not break decoding.
        assertTrue(response.product.nutriments.hasAnyData)
    }

    private fun product(
        code: String? = "1234567890123",
        name: String? = "Test food",
        brands: String? = null,
        nutriments: OffNutriments = OffNutriments(),
    ) = OffProduct(code = code, productName = name, brands = brands, nutriments = nutriments)
}
