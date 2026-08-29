package io.github.augustinavicius.nutrition.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class DayEnergy(val date: LocalDate, val kcal: Double)

@Dao
interface FoodDao {

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE id = :id")
    fun observeById(id: Long): Flow<FoodEntity?>

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    /**
     * Ranks exact prefix matches above mid-word matches, then favourites, then how often
     * the food has been logged — so typing "chi" surfaces "Chicken breast" you eat weekly
     * before "Zucchini" that merely contains the letters.
     */
    @Query(
        """
        SELECT * FROM foods
        WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' OR barcode = :query
        ORDER BY
            CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END,
            favorite DESC,
            useCount DESC,
            name COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    fun search(query: String, limit: Int = 50): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods ORDER BY favorite DESC, lastUsedAt DESC, useCount DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<FoodEntity>>

    @Upsert
    suspend fun upsert(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Delete
    suspend fun delete(food: FoodEntity)

    @Query("UPDATE foods SET lastUsedAt = :at, useCount = useCount + 1 WHERE id = :id")
    suspend fun markUsed(id: Long, at: Long = System.currentTimeMillis())

    @Query("UPDATE foods SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int
}

@Dao
interface DiaryDao {

    @Query("SELECT * FROM diary_entries WHERE date = :date ORDER BY meal ASC, createdAt ASC")
    fun observeForDate(date: LocalDate): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun byId(id: Long): DiaryEntryEntity?

    @Query(
        """
        SELECT date AS date, SUM(n_kcal * grams / 100.0) AS kcal
        FROM diary_entries
        WHERE date BETWEEN :from AND :to
        GROUP BY date
        """
    )
    fun observeDailyEnergy(from: LocalDate, to: LocalDate): Flow<List<DayEnergy>>

    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Update
    suspend fun update(entry: DiaryEntryEntity)

    @Delete
    suspend fun delete(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
