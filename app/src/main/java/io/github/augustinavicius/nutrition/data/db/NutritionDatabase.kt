package io.github.augustinavicius.nutrition.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [FoodEntity::class, DiaryEntryEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class NutritionDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryDao(): DiaryDao

    companion object {
        fun build(context: Context): NutritionDatabase =
            Room.databaseBuilder(context, NutritionDatabase::class.java, "nutrition.db")
                .build()
    }
}
