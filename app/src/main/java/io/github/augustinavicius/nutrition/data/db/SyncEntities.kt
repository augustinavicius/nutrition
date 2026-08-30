package io.github.augustinavicius.nutrition.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert

/** What kind of record a tombstone refers to. */
enum class SyncKind { FOOD, RECIPE }

/**
 * A record of something deleted locally.
 *
 * Deletions have to travel too, or a record removed on one device would simply come back
 * from another the next time they sync. Keeping tombstones in their own table lets every
 * existing query stay as it is, rather than filtering a "deleted" flag everywhere.
 */
@Entity(tableName = "deletions", primaryKeys = ["kind", "uid"])
data class DeletionEntity(
    val kind: SyncKind,
    val uid: String,
    val deletedAt: Long,
)

@Dao
interface SyncDao {

    @Query("SELECT * FROM foods")
    suspend fun allFoods(): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE uid = :uid LIMIT 1")
    suspend fun foodByUid(uid: String): FoodEntity?

    @Query("SELECT * FROM recipes")
    suspend fun allRecipes(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE uid = :uid LIMIT 1")
    suspend fun recipeByUid(uid: String): RecipeEntity?

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY position")
    suspend fun ingredientsFor(recipeId: Long): List<RecipeIngredientEntity>

    @Query("SELECT * FROM deletions")
    suspend fun allDeletions(): List<DeletionEntity>

    @Upsert
    suspend fun recordDeletion(deletion: DeletionEntity)

    @Query("DELETE FROM deletions WHERE kind = :kind AND uid = :uid")
    suspend fun clearDeletion(kind: SyncKind, uid: String)

    @Query("DELETE FROM foods WHERE uid = :uid")
    suspend fun deleteFoodByUid(uid: String)

    @Query("DELETE FROM recipes WHERE uid = :uid")
    suspend fun deleteRecipeByUid(uid: String)
}
