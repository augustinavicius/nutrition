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
    fun `serving weight is read from free text serving sizes`() {
        assertEquals(30.0, parseServingGrams("30 g")!!, EPSILON)
        assertEquals(45.0, parseServingGrams("45g")!!, EPSILON)
        assertEquals(12.5, parseServingGrams("12,5 g")!!, EPSILON)
        // The useful number sits in the parenthetical, not the leading count.
        assertEquals(240.0, parseServingGrams("1 cup (240 ml)")!!, EPSILON)
    }

    @Test
    fun `unusable serving sizes yield no weight`() {
        assertNull(parseServingGrams(null))
        assertNull(parseServingGrams(""))
        assertNull(parseServingGrams("1 slice"))
        assertNull(parseServingGrams("0 g"))
    }

    @Test
    fun `products map onto a per-serving basis`() {
        val food = product(
            code = "3017620422003",
            name = "Nutella",
            brands = "Ferrero, Nutella",
            servingSize = "15 g",
            nutriments = OffNutriments(
                energyKcalRaw = JsonPrimitive(539),
                proteinsRaw = JsonPrimitive(6.3),
                carbohydratesRaw = JsonPrimitive(57.5),
                fatRaw = JsonPrimitive(30.9),
                sugarsRaw = JsonPrimitive(56.3),
                sodiumRaw = JsonPrimitive(0.0428),
            ),
        ).toFood()!!

        assertEquals(15.0, food.servingGrams!!, EPSILON)
        assertEquals(539 * 0.15, food.perServing.kcal, 1e-6)
        assertEquals(539.0, food.per100g!!.kcal, 1e-6)
        // Only the first brand is kept; the rest is crowd-sourced noise.
        assertEquals("Ferrero", food.brand)
        // OFF reports sodium in grams.
        assertEquals(42.8 * 0.15, food.perServing.sodiumMg!!, 1e-6)
    }

    @Test
    fun `energy falls back to kilojoules when kcal is missing`() {
        val food = product(
            nutriments = OffNutriments(energyKjRaw = JsonPrimitive(2255)),
        ).toFood()!!
        assertEquals(2255 / 4.184, food.per100g!!.kcal, 1e-6)
    }

    @Test
    fun `sodium falls back to salt using the standard ratio`() {
        val food = product(nutriments = OffNutriments(saltRaw = JsonPrimitive(0.107))).toFood()!!
        assertEquals(42.8, food.per100g!!.sodiumMg!!, 1e-3)
    }

    @Test
    fun `numeric fields arrive as strings on some products`() {
        val food = product(
            servingQuantity = JsonPrimitive("30"),
            nutriments = OffNutriments(energyKcalRaw = JsonPrimitive("250")),
        ).toFood()!!
        assertEquals(30.0, food.servingGrams!!, EPSILON)
        assertEquals(75.0, food.perServing.kcal, 1e-6)
    }

    @Test
    fun `non-numeric junk is treated as missing, not as an error`() {
        val food = product(nutriments = OffNutriments(energyKcalRaw = JsonPrimitive("unknown"))).toFood()!!
        assertEquals(0.0, food.perServing.kcal, EPSILON)
    }

    @Test
    fun `products without a code or a name are rejected`() {
        assertNull(product(code = null).toFood())
        assertNull(product(name = null).toFood())
        assertNull(product(name = "   ").toFood())
    }

    @Test
    fun `products with no declared serving default to 100 g`() {
        val food = product(servingSize = null, servingQuantity = null).toFood()!!
        assertEquals(100.0, food.servingGrams!!, EPSILON)
        assertEquals("100 g", food.servingLabel)
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
        assertEquals(150.0, food.servingGrams!!, EPSILON)
        assertEquals(96.1759082217972 * 1.5, food.perServing.kcal, 1e-6)
        assertEquals(8.5, food.per100g!!.protein, 1e-6)
        assertTrue(response.product.nutriments.hasAnyData)
    }

    private fun product(
        code: String? = "1234567890123",
        name: String? = "Test food",
        brands: String? = null,
        servingSize: String? = null,
        servingQuantity: JsonPrimitive? = null,
        nutriments: OffNutriments = OffNutriments(),
    ) = OffProduct(
        code = code,
        productName = name,
        brands = brands,
        servingSize = servingSize,
        servingQuantityRaw = servingQuantity,
        nutriments = nutriments,
    )

    private companion object {
        const val EPSILON = 1e-9
    }
}
