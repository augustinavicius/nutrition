package io.github.augustinavicius.nutrition.data.repo

import io.github.augustinavicius.nutrition.core.DiaryEntry
import io.github.augustinavicius.nutrition.core.Food
import io.github.augustinavicius.nutrition.core.MealType
import io.github.augustinavicius.nutrition.data.db.DiaryDao
import io.github.augustinavicius.nutrition.data.db.toDomain
import io.github.augustinavicius.nutrition.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class DiaryRepository(
    private val dao: DiaryDao,
    private val foods: FoodRepository,
) {

    fun observeDay(date: LocalDate): Flow<List<DiaryEntry>> =
        dao.observeForDate(date).map { list -> list.map { it.toDomain() } }

    fun observeEnergyBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Double>> =
        dao.observeDailyEnergy(from, to).map { rows -> rows.associate { it.date to it.kcal } }

    suspend fun entry(id: Long): DiaryEntry? = dao.byId(id)?.toDomain()

    /** Logs [grams] of [food]; also bumps the food's usage counters so it ranks higher later. */
    suspend fun log(food: Food, grams: Double, meal: MealType, date: LocalDate): Long {
        val id = dao.insert(
            DiaryEntry(
                date = date,
                meal = meal,
                foodId = food.id.takeIf { it != 0L },
                name = food.name,
                brand = food.brand,
                grams = grams,
                per100g = food.per100g,
            ).toEntity()
        )
        food.id.takeIf { it != 0L }?.let { foods.markUsed(it) }
        return id
    }

    suspend fun update(entry: DiaryEntry) = dao.update(entry.toEntity())

    /** Re-inserts a deleted entry under a fresh id, backing the diary's undo action. */
    suspend fun restore(entry: DiaryEntry): Long = dao.insert(entry.copy(id = 0).toEntity())

    suspend fun delete(entry: DiaryEntry) = dao.delete(entry.toEntity())

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
