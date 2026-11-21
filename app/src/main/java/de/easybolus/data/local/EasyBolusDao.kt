package de.easybolus.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EasyBolusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlucoseReading(reading: GlucoseReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlucoseReadings(readings: List<GlucoseReadingEntity>)

    @Query("SELECT * FROM glucose_readings ORDER BY timestampMillis DESC LIMIT 1")
    fun observeLatestGlucose(): Flow<GlucoseReadingEntity?>

    @Query("SELECT * FROM glucose_readings ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatestGlucose(): GlucoseReadingEntity?

    @Query("SELECT * FROM glucose_readings WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    fun observeGlucoseSince(sinceMillis: Long): Flow<List<GlucoseReadingEntity>>

    @Query("SELECT * FROM glucose_readings WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    suspend fun getGlucoseSince(sinceMillis: Long): List<GlucoseReadingEntity>

    @Query("SELECT * FROM glucose_readings ORDER BY timestampMillis ASC")
    suspend fun getAllGlucoseReadings(): List<GlucoseReadingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiaryEntry(entry: DiaryEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiaryEntries(entries: List<DiaryEntryEntity>)

    @Update
    suspend fun updateDiaryEntry(entry: DiaryEntryEntity)

    @Delete
    suspend fun deleteDiaryEntry(entry: DiaryEntryEntity)

    @Query("SELECT * FROM diary_entries ORDER BY timestampMillis DESC")
    fun observeAllDiaryEntries(): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries ORDER BY timestampMillis DESC")
    suspend fun getAllDiaryEntries(): List<DiaryEntryEntity>

    @Query("SELECT * FROM diary_entries WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    fun observeDiaryEntriesSince(sinceMillis: Long): Flow<List<DiaryEntryEntity>>

    @Query("DELETE FROM glucose_readings WHERE source = 'Demo'")
    suspend fun clearDemoGlucose()

    @Query("DELETE FROM diary_entries WHERE note LIKE '[Demo]%'")
    suspend fun clearDemoDiary()
}
