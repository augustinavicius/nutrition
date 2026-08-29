package io.github.augustinavicius.nutrition.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        FoodEntity::class,
        DiaryEntryEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class NutritionDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun recipeDao(): RecipeDao

    companion object {
        fun build(context: Context): NutritionDatabase =
            Room.databaseBuilder(context, NutritionDatabase::class.java, "nutrition.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}

/** Recipes arrive; nothing existing changes. */
internal val MIGRATION_2_3 = object : Migration(2, 3) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `cookedGrams` REAL,
                `foodId` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipe_ingredients` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipeId` INTEGER NOT NULL,
                `foodId` INTEGER,
                `name` TEXT NOT NULL,
                `brand` TEXT,
                `grams` REAL NOT NULL,
                `n_kcal` REAL NOT NULL,
                `n_protein` REAL NOT NULL,
                `n_carbs` REAL NOT NULL,
                `n_fat` REAL NOT NULL,
                `n_fiber` REAL,
                `n_sugar` REAL,
                `n_sodiumMg` REAL,
                `position` INTEGER NOT NULL,
                FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_recipeId` " +
                "ON `recipe_ingredients` (`recipeId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_foodId` " +
                "ON `recipe_ingredients` (`foodId`)"
        )
    }
}

/**
 * Servings are gone: everything is grams, and nutrients are stored per 100 g.
 *
 * Rows are converted rather than dropped, so an existing diary keeps its numbers — the
 * arithmetic below is exact, since `perServing × servings` and `per100g × grams / 100` are the
 * same quantity. Foods that never had a gram weight (logged by the piece) are read as if a
 * serving weighed 100 g, which is the only assumption available; their totals are preserved.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(connection: SQLiteConnection) {
        // NULLIF guards against a stored zero weight turning into a division by zero.
        val weight = "COALESCE(NULLIF(servingGrams, 0), 100.0)"

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `foods_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `brand` TEXT,
                `barcode` TEXT,
                `n_kcal` REAL NOT NULL,
                `n_protein` REAL NOT NULL,
                `n_carbs` REAL NOT NULL,
                `n_fat` REAL NOT NULL,
                `n_fiber` REAL,
                `n_sugar` REAL,
                `n_sodiumMg` REAL,
                `source` TEXT NOT NULL,
                `imageUrl` TEXT,
                `favorite` INTEGER NOT NULL,
                `lastUsedAt` INTEGER,
                `useCount` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO `foods_new` (
                id, name, brand, barcode,
                n_kcal, n_protein, n_carbs, n_fat, n_fiber, n_sugar, n_sodiumMg,
                source, imageUrl, favorite, lastUsedAt, useCount, createdAt, updatedAt
            )
            SELECT
                id, name, brand, barcode,
                n_kcal * 100.0 / $weight,
                n_protein * 100.0 / $weight,
                n_carbs * 100.0 / $weight,
                n_fat * 100.0 / $weight,
                n_fiber * 100.0 / $weight,
                n_sugar * 100.0 / $weight,
                n_sodiumMg * 100.0 / $weight,
                source, imageUrl, favorite, lastUsedAt, useCount, createdAt, updatedAt
            FROM `foods`
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE `foods`")
        connection.execSQL("ALTER TABLE `foods_new` RENAME TO `foods`")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_foods_barcode` ON `foods` (`barcode`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_foods_name` ON `foods` (`name`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_foods_lastUsedAt` ON `foods` (`lastUsedAt`)")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `diary_entries_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` INTEGER NOT NULL,
                `meal` TEXT NOT NULL,
                `foodId` INTEGER,
                `name` TEXT NOT NULL,
                `brand` TEXT,
                `grams` REAL NOT NULL,
                `n_kcal` REAL NOT NULL,
                `n_protein` REAL NOT NULL,
                `n_carbs` REAL NOT NULL,
                `n_fat` REAL NOT NULL,
                `n_fiber` REAL,
                `n_sugar` REAL,
                `n_sodiumMg` REAL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO `diary_entries_new` (
                id, date, meal, foodId, name, brand, grams,
                n_kcal, n_protein, n_carbs, n_fat, n_fiber, n_sugar, n_sodiumMg, createdAt
            )
            SELECT
                id, date, meal, foodId, name, brand,
                servings * $weight,
                n_kcal * 100.0 / $weight,
                n_protein * 100.0 / $weight,
                n_carbs * 100.0 / $weight,
                n_fat * 100.0 / $weight,
                n_fiber * 100.0 / $weight,
                n_sugar * 100.0 / $weight,
                n_sodiumMg * 100.0 / $weight,
                createdAt
            FROM `diary_entries`
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE `diary_entries`")
        connection.execSQL("ALTER TABLE `diary_entries_new` RENAME TO `diary_entries`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_diary_entries_date` ON `diary_entries` (`date`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_diary_entries_foodId` ON `diary_entries` (`foodId`)")
    }
}
