package de.easybolus.domain

import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BackupMergerAndReportsTest {

    @Test
    fun backupMergerDeduplicatesExistingReadingsAndEntries() {
        val t1 = 1_700_000_000_000L
        val t2 = t1 + 60_000L
        val existingReadings = listOf(
            GlucoseReadingEntity(id = 10, timestampMillis = t1, mgDl = 120, source = "Juggluco")
        )
        val incomingReadings = listOf(
            GlucoseReadingEntity(id = 0, timestampMillis = t1, mgDl = 120, source = "Juggluco"), // duplicate
            GlucoseReadingEntity(id = 0, timestampMillis = t2, mgDl = 135, source = "Juggluco")  // new
        )

        val existingEntries = listOf(
            DiaryEntryEntity(id = 5, timestampMillis = t1, type = DiaryEntryType.BOLUS, bolusUnits = 2.0, note = "Lunch")
        )
        val incomingEntries = listOf(
            DiaryEntryEntity(id = 0, timestampMillis = t1, type = DiaryEntryType.BOLUS, bolusUnits = 2.0, note = "Lunch"), // duplicate
            DiaryEntryEntity(id = 0, timestampMillis = t2, type = DiaryEntryType.CARBS, carbsGrams = 30.0) // new
        )

        val diff = BackupMerger.computeDiff(existingReadings, existingEntries, incomingReadings, incomingEntries)
        assertEquals(1, diff.newReadings.size)
        assertEquals(135, diff.newReadings[0].mgDl)
        assertEquals(1, diff.newEntries.size)
        assertEquals(30.0, diff.newEntries[0].carbsGrams ?: 0.0, 0.001)
    }

    @Test
    fun reportCalculatorComputesDetailsAndDailyReport() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 20, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val day10am = cal.timeInMillis
        val settings = UserSettings(targetRangeMin = 80, targetRangeMax = 180)

        val readings = listOf(
            GlucoseReadingEntity(timestampMillis = day10am - 8 * 3600_000L, mgDl = 62),  // 02:00 (Night, hypo)
            GlucoseReadingEntity(timestampMillis = day10am - 7 * 3600_000L, mgDl = 48),  // 03:00 (Night, very low)
            GlucoseReadingEntity(timestampMillis = day10am, mgDl = 115),                 // 10:00 (Morning)
            GlucoseReadingEntity(timestampMillis = day10am + 4 * 3600_000L, mgDl = 145)  // 14:00 (Afternoon)
        )
        val entries = listOf(
            DiaryEntryEntity(timestampMillis = day10am, type = DiaryEntryType.BOLUS, bolusUnits = 3.0),
            DiaryEntryEntity(timestampMillis = day10am + 1000, type = DiaryEntryType.CARBS, carbsGrams = 40.0),
            DiaryEntryEntity(timestampMillis = day10am + 2000, type = DiaryEntryType.BASAL, basalUnits = 12.0)
        )

        val summary = ReportCalculator.calculate(
            readings = readings,
            entries = entries,
            period = ReportPeriod.DAYS_7,
            settings = settings,
            nowMillis = day10am + 10 * 3600_000L
        )

        assertEquals(2, summary.details.hypo.lowEventsCount)
        assertEquals(1, summary.details.hypo.veryLowEventsCount)
        assertEquals(48, summary.details.hypo.lowestBgMgDl)
        assertNotNull(summary.details.variability.sdMgDl)
        assertEquals(48, summary.details.variability.minBgMgDl)
        assertEquals(145, summary.details.variability.maxBgMgDl)
        assertTrue(summary.details.therapy.avgDailyBasal > 0.0)

        val daily = ReportCalculator.calculateDailyReport(
            readings = readings,
            entries = entries,
            dayOffset = 0,
            settings = settings,
            nowMillis = day10am + 6 * 3600_000L
        )
        assertEquals(4, daily.readings.size)
        assertEquals(3.0, daily.totalBolus, 0.001)
        assertEquals(40.0, daily.totalCarbs, 0.001)
        assertEquals(12.0, daily.totalBasal, 0.001)
    }
}
