package de.easybolus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromTrendArrow(value: TrendArrow?): String? = value?.name

    @TypeConverter
    fun toTrendArrow(value: String?): TrendArrow? =
        value?.let { runCatching { TrendArrow.valueOf(it) }.getOrDefault(TrendArrow.UNKNOWN) }

    @TypeConverter
    fun fromDiaryEntryType(value: DiaryEntryType): String = value.name

    @TypeConverter
    fun toDiaryEntryType(value: String): DiaryEntryType =
        runCatching { DiaryEntryType.valueOf(value) }.getOrDefault(DiaryEntryType.NOTE)
}

@Database(
    entities = [GlucoseReadingEntity::class, DiaryEntryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EasyBolusDatabase : RoomDatabase() {
    abstract fun dao(): EasyBolusDao

    companion object {
        @Volatile
        private var INSTANCE: EasyBolusDatabase? = null

        fun getInstance(context: Context): EasyBolusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EasyBolusDatabase::class.java,
                    "easybolus.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
