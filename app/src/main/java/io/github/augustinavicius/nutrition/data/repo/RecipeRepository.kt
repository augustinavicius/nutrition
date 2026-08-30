package io.github.augustinavicius.nutrition.data.repo

import io.github.augustinavicius.nutrition.core.Food
import io.github.augustinavicius.nutrition.core.FoodSource
import io.github.augustinavicius.nutrition.core.Nutrients
import io.github.augustinavicius.nutrition.core.Recipe
import io.github.augustinavicius.nutrition.data.db.DeletionEntity
import io.github.augustinavicius.nutrition.data.db.RecipeDao
import io.github.augustinavicius.nutrition.data.db.SyncDao
import io.github.augustinavicius.nutrition.data.db.SyncKind
import io.github.augustinavicius.nutrition.data.db.toDomain
import io.github.augustinavicius.nutrition.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Recipes, and the food each one keeps in step.
 *
 * A saved recipe writes its finished per-100 g figures into an ordinary [Food] row tagged
 * [FoodSource.RECIPE]. Everything downstream — search, logging, the diary — then treats a
 * cooked dish exactly like any other food, with no special cases.
 */
class RecipeRepository(
    private val dao: RecipeDao,
    private val syncDao: SyncDao,
    private val foods: FoodRepository,
) {

    fun observeAll(): Flow<List<Recipe>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun recipe(id: Long): Recipe? = dao.byId(id)?.toDomain()

    /** The recipe behind a food, so a cooked dish can be opened back up and edited. */
    suspend fun recipeIdForFood(foodId: Long): Long? = dao.byFoodId(foodId)?.id

    suspend fun save(recipe: Recipe): Long {
        val inserted = dao.upsertRecipe(recipe.toEntity())
        val recipeId = if (inserted > 0) inserted else recipe.id

        dao.replaceIngredients(
            recipeId,
            recipe.ingredients.mapIndexed { index, ingredient -> ingredient.toEntity(recipeId, index) },
        )

        val foodId = foods.save(
            Food(
                id = recipe.foodId ?: 0,
                name = recipe.name.trim(),
                per100g = recipe.per100g ?: Nutrients(0.0, 0.0, 0.0, 0.0),
                source = FoodSource.RECIPE,
            )
        )
        dao.setFoodId(recipeId, foodId)
        return recipeId
    }

    /**
     * Removes the recipe, its ingredients and the food it maintained. Days already logged
     * keep their numbers, because diary entries snapshot what they were logged from.
     */
    suspend fun delete(id: Long) {
        val existing = dao.byId(id) ?: return
        existing.recipe.foodId?.let { foodId -> foods.food(foodId)?.let { foods.delete(it) } }
        dao.deleteRecipe(id)
        existing.recipe.uid.takeIf { it.isNotBlank() }?.let { uid ->
            syncDao.recordDeletion(DeletionEntity(SyncKind.RECIPE, uid, System.currentTimeMillis()))
        }
    }
}
