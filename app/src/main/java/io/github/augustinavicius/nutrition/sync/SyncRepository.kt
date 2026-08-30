package io.github.augustinavicius.nutrition.sync

import android.util.Log
import io.github.augustinavicius.nutrition.core.FoodSource
import io.github.augustinavicius.nutrition.core.Nutrients
import io.github.augustinavicius.nutrition.data.db.DeletionEntity
import io.github.augustinavicius.nutrition.data.db.FoodDao
import io.github.augustinavicius.nutrition.data.db.FoodEntity
import io.github.augustinavicius.nutrition.data.db.RecipeDao
import io.github.augustinavicius.nutrition.data.db.RecipeEntity
import io.github.augustinavicius.nutrition.data.db.RecipeIngredientEntity
import io.github.augustinavicius.nutrition.data.db.SyncDao
import io.github.augustinavicius.nutrition.data.db.SyncKind
import io.github.augustinavicius.nutrition.data.prefs.SecretStore
import io.github.augustinavicius.nutrition.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

sealed interface SyncOutcome {
    data object NotConfigured : SyncOutcome
    data class Success(val pulled: Int, val published: Int, val at: Long) : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

/**
 * Reconciles this device's library with the one on the user's own WebDAV server.
 *
 * Read the remote document, merge it with what is here, write back what changed locally, then
 * publish the result — conditionally, so a device that synced in between is never clobbered.
 */
class SyncRepository(
    private val syncDao: SyncDao,
    private val foodDao: FoodDao,
    private val recipeDao: RecipeDao,
    private val secrets: SecretStore,
    private val settings: SettingsStore,
    private val webdav: WebDavClient,
    private val json: Json,
) {

    suspend fun config(): WebDavConfig {
        val stored = settings.syncSettings.first()
        return WebDavConfig(
            serverUrl = stored.serverUrl,
            username = stored.username,
            password = secrets.get(SecretStore.WEBDAV_PASSWORD).orEmpty(),
            folder = stored.folder,
        )
    }

    suspend fun sync(): SyncOutcome = withContext(Dispatchers.IO) {
        val config = config()
        if (!config.isComplete) return@withContext SyncOutcome.NotConfigured

        var attempt = 0
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val outcome = runCatching { syncOnce(config) }.getOrElse { error ->
                if (error is WebDavConflictException) return@getOrElse null
                Log.w(TAG, "Sync failed", error)
                return@withContext SyncOutcome.Failed(error.message ?: "Sync failed")
            }
            if (outcome != null) return@withContext outcome
            // Someone else published between our read and write; merge afresh against theirs.
        }
        SyncOutcome.Failed("The library kept changing on the server. Try again.")
    }

    private suspend fun syncOnce(config: WebDavConfig): SyncOutcome {
        val remoteRaw = webdav.get(config).getOrThrow()
        val remote = remoteRaw.body
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<SyncDocument>(it) }.getOrNull() }
            ?: SyncDocument()

        val local = localDocument()
        val now = System.currentTimeMillis()
        val result = merge(local, remote, now)

        applyLocally(result)

        val body = json.encodeToString(result.merged)
        webdav.put(config, body, remoteRaw.etag).getOrThrow()
        settings.setSyncLastAt(now)

        return SyncOutcome.Success(
            pulled = result.foodsToApply.size + result.recipesToApply.size + result.deletionsToApply.size,
            published = result.merged.foods.size + result.merged.recipes.size,
            at = now,
        )
    }

    // ---------------------------------------------------------------- local <-> document

    private suspend fun localDocument(): SyncDocument {
        val foods = syncDao.allFoods()
        val foodUidById = foods.associate { it.id to it.uid }

        return SyncDocument(
            foods = foods.map { food ->
                SyncFood(
                    uid = food.uid,
                    name = food.name,
                    brand = food.brand,
                    barcode = food.barcode,
                    source = food.source.name,
                    imageUrl = food.imageUrl,
                    per100g = food.per100g.toSync(),
                    updatedAt = food.updatedAt,
                )
            },
            recipes = syncDao.allRecipes().map { recipe ->
                SyncRecipe(
                    uid = recipe.uid,
                    name = recipe.name,
                    cookedGrams = recipe.cookedGrams,
                    foodUid = recipe.foodId?.let { foodUidById[it] },
                    updatedAt = recipe.updatedAt,
                    ingredients = syncDao.ingredientsFor(recipe.id).map { ingredient ->
                        SyncIngredient(
                            name = ingredient.name,
                            brand = ingredient.brand,
                            grams = ingredient.grams,
                            per100g = ingredient.per100g.toSync(),
                            foodUid = ingredient.foodId?.let { foodUidById[it] },
                        )
                    },
                )
            },
            deletions = syncDao.allDeletions().map {
                SyncDeletion(it.kind.name, it.uid, it.deletedAt)
            },
        )
    }

    private suspend fun applyLocally(result: MergeResult) {
        // Foods first: recipes reference them by uid.
        result.foodsToApply.forEach { remote ->
            val existing = syncDao.foodByUid(remote.uid)
            foodDao.upsert(
                FoodEntity(
                    id = existing?.id ?: 0,
                    uid = remote.uid,
                    name = remote.name,
                    brand = remote.brand,
                    barcode = remote.barcode,
                    per100g = remote.per100g.toNutrients(),
                    source = runCatching { FoodSource.valueOf(remote.source) }
                        .getOrDefault(FoodSource.CUSTOM),
                    imageUrl = remote.imageUrl,
                    // Favourites and usage counts describe how *this* device is used, so they
                    // stay put rather than travelling between devices.
                    favorite = existing?.favorite ?: false,
                    lastUsedAt = existing?.lastUsedAt,
                    useCount = existing?.useCount ?: 0,
                    createdAt = existing?.createdAt ?: remote.updatedAt,
                    updatedAt = remote.updatedAt,
                )
            )
        }

        result.recipesToApply.forEach { remote ->
            val existing = syncDao.recipeByUid(remote.uid)
            val recipeId = recipeDao.upsertRecipe(
                RecipeEntity(
                    id = existing?.id ?: 0,
                    uid = remote.uid,
                    name = remote.name,
                    cookedGrams = remote.cookedGrams,
                    foodId = remote.foodUid?.let { syncDao.foodByUid(it)?.id },
                    createdAt = existing?.createdAt ?: remote.updatedAt,
                    updatedAt = remote.updatedAt,
                )
            ).takeIf { it > 0 } ?: existing?.id ?: return@forEach

            recipeDao.replaceIngredients(
                recipeId,
                remote.ingredients.mapIndexed { index, ingredient ->
                    RecipeIngredientEntity(
                        recipeId = recipeId,
                        foodId = ingredient.foodUid?.let { syncDao.foodByUid(it)?.id },
                        name = ingredient.name,
                        brand = ingredient.brand,
                        grams = ingredient.grams,
                        per100g = ingredient.per100g.toNutrients(),
                        position = index,
                    )
                },
            )
        }

        result.deletionsToApply.forEach { deletion ->
            val kind = runCatching { SyncKind.valueOf(deletion.kind) }.getOrNull() ?: return@forEach
            when (kind) {
                SyncKind.FOOD -> syncDao.deleteFoodByUid(deletion.uid)
                SyncKind.RECIPE -> syncDao.deleteRecipeByUid(deletion.uid)
            }
            // Remember it locally too, or the next sync would offer the record straight back.
            syncDao.recordDeletion(DeletionEntity(kind, deletion.uid, deletion.deletedAt))
        }
    }

    private companion object {
        const val TAG = "SyncRepository"
        const val MAX_ATTEMPTS = 3
    }
}

private fun Nutrients.toSync() = SyncNutrients(kcal, protein, carbs, fat, fiber, sugar, sodiumMg)

private fun SyncNutrients.toNutrients() = Nutrients(kcal, protein, carbs, fat, fiber, sugar, sodiumMg)
