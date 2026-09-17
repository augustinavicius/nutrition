package io.github.augustinavicius.nutrition.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import io.github.augustinavicius.nutrition.core.DiaryEntry
import io.github.augustinavicius.nutrition.core.Food
import io.github.augustinavicius.nutrition.core.FoodSource
import io.github.augustinavicius.nutrition.core.MealType
import io.github.augustinavicius.nutrition.core.Nutrients
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToEpochDay(value: LocalDate): Long = value.toEpochDay()
    @TypeConverter fun epochDayToDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    @TypeConverter fun mealToName(value: MealType): String = value.name
    @TypeConverter fun nameToMeal(value: String): MealType =
        runCatching { MealType.valueOf(value) }.getOrDefault(MealType.SNACK)

    @TypeConverter fun sourceToName(value: FoodSource): String = value.name
    @TypeConverter fun nameToSource(value: String): FoodSource =
        runCatching { FoodSource.valueOf(value) }.getOrDefault(FoodSource.CUSTOM)
}

@Entity(
    tableName = "foods",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["name"]),
        Index(value = ["lastUsedAt"]),
    ],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String?,
    val barcode: String?,
    @Embedded(prefix = "n_") val per100g: Nutrients,
    val source: FoodSource,
    val imageUrl: String?,
    val favorite: Boolean = false,
    val lastUsedAt: Long? = null,
    val useCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * A logged amount, in grams.
 *
 * Deliberately *not* a foreign key onto [FoodEntity]: the name and per-100 g nutrients are
 * snapshotted at log time so that correcting or deleting a food later never silently rewrites
 * what a past day says you ate. [foodId] is a soft link, kept only so "log again" can find
 * the original.
 */
@Entity(
    tableName = "diary_entries",
    indices = [Index(value = ["date"]), Index(value = ["foodId"])],
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val meal: MealType,
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val grams: Double,
    @Embedded(prefix = "n_") val per100g: Nutrients,
    val createdAt: Long = System.currentTimeMillis(),
)

fun FoodEntity.toDomain() = Food(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    per100g = per100g,
    source = source,
    imageUrl = imageUrl,
    favorite = favorite,
    lastUsedAt = lastUsedAt,
    useCount = useCount,
)

fun Food.toEntity(createdAt: Long = System.currentTimeMillis()) = FoodEntity(
    id = id,
    name = name.trim(),
    brand = brand?.trim()?.takeIf { it.isNotEmpty() },
    barcode = barcode?.trim()?.takeIf { it.isNotEmpty() },
    per100g = per100g,
    source = source,
    imageUrl = imageUrl,
    favorite = favorite,
    lastUsedAt = lastUsedAt,
    useCount = useCount,
    createdAt = createdAt,
    updatedAt = System.currentTimeMillis(),
)

fun DiaryEntryEntity.toDomain() = DiaryEntry(
    id = id,
    date = date,
    meal = meal,
    foodId = foodId,
    name = name,
    brand = brand,
    grams = grams,
    per100g = per100g,
    createdAt = createdAt,
)

fun DiaryEntry.toEntity() = DiaryEntryEntity(
    id = id,
    date = date,
    meal = meal,
    foodId = foodId,
    name = name,
    brand = brand,
    grams = grams,
    per100g = per100g,
    createdAt = createdAt,
)
