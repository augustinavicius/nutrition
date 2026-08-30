package io.github.augustinavicius.nutrition.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import io.github.augustinavicius.nutrition.core.Nutrients
import io.github.augustinavicius.nutrition.core.Recipe
import io.github.augustinavicius.nutrition.core.RecipeIngredient

@Entity(tableName = "recipes", indices = [Index(value = ["uid"], unique = true)])
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable across devices, unlike the row id. Sync matches records on this. */
    @ColumnInfo(defaultValue = "") val uid: String = newUid(),
    val name: String,
    /** What the finished dish weighed; null until it has been weighed. */
    val cookedGrams: Double?,
    /** The food row this recipe keeps in step, so it can be searched and logged like any other. */
    val foodId: Long?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "recipe_ingredients",
    indices = [Index(value = ["recipeId"]), Index(value = ["foodId"])],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    /** Soft link only; the snapshot below is what the recipe actually uses. */
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val grams: Double,
    @Embedded(prefix = "n_") val per100g: Nutrients,
    val position: Int,
)

data class RecipeWithIngredients(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<RecipeIngredientEntity>,
)

fun RecipeWithIngredients.toDomain() = Recipe(
    id = recipe.id,
    uid = recipe.uid,
    name = recipe.name,
    cookedGrams = recipe.cookedGrams,
    foodId = recipe.foodId,
    updatedAt = recipe.updatedAt,
    ingredients = ingredients.sortedBy { it.position }.map { it.toDomain() },
)

fun RecipeIngredientEntity.toDomain() = RecipeIngredient(
    id = id,
    foodId = foodId,
    name = name,
    brand = brand,
    grams = grams,
    per100g = per100g,
)

fun Recipe.toEntity() = RecipeEntity(
    id = id,
    uid = uid.ifBlank { newUid() },
    name = name.trim(),
    cookedGrams = cookedGrams,
    foodId = foodId,
)

fun RecipeIngredient.toEntity(recipeId: Long, position: Int) = RecipeIngredientEntity(
    id = id,
    recipeId = recipeId,
    foodId = foodId,
    name = name.trim(),
    brand = brand?.trim()?.takeIf { it.isNotEmpty() },
    grams = grams,
    per100g = per100g,
    position = position,
)
