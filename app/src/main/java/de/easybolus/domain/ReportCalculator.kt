package de.easybolus.domain

import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.settings.UserSettings
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

enum class ReportPeriod(val days: Int) {
    DAYS_7(7),
    DAYS_14(14),
    DAYS_30(30),
    DAYS_90(90)
}

data class AgpBucket(
    val minuteOfDay: Int, // 0, 30, 60, ..., 1410
    val p5: Float,
    val p25: Float,
    val p50: Float,
    val p75: Float,
    val p95: Float
)

data class ReportSummary(
    val period: ReportPeriod,
    val fromMillis: Long,
    val toMillis: Long,
    val totalReadings: Int,
    val activeDays: Int,
    val veryHighPct: Double, // > 250 mg/dl
    val highPct: Double,     // targetRangeMax+1 .. 250
    val inRangePct: Double,  // targetRangeMin .. targetRangeMax
    val lowPct: Double,      // 54 .. targetRangeMin-1
    val veryLowPct: Double,  // < 54 mg/dl
    val averageBg: Double?,
    val gmiPercent: Double?,
    val cvPercent: Double?,
    val avgDailyBolus: Double,
    val avgDailyCarbs: Double,
    val agpBuckets: List<AgpBucket>
)

object ReportCalculator {

    const val VERY_HIGH_THRESHOLD = 250
    const val VERY_LOW_THRESHOLD = 54

    fun calculate(
        readings: List<GlucoseReadingEntity>,
        entries: List<DiaryEntryEntity>,
        period: ReportPeriod,
        settings: UserSettings,
        nowMillis: Long = System.currentTimeMillis()
    ): ReportSummary {
        val fromMillis = nowMillis - period.days * 24L * 3600_000L
        val periodReadings = readings.filter { it.timestampMillis in fromMillis..nowMillis }
        val periodEntries = entries.filter { it.timestampMillis in fromMillis..nowMillis }

        val cal = Calendar.getInstance()
        val distinctDays = HashSet<Int>()
        val buckets = Array(48) { mutableListOf<Int>() }

        var veryHigh = 0
        var high = 0
        var inRange = 0
        var low = 0
        var veryLow = 0

        for (r in periodReadings) {
            cal.timeInMillis = r.timestampMillis
            val dayKey = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
            distinctDays.add(dayKey)

            val bucketIdx = ((cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) / 30).coerceIn(0, 47)
            buckets[bucketIdx].add(r.mgDl)

            when {
                r.mgDl > VERY_HIGH_THRESHOLD -> veryHigh++
                r.mgDl > settings.targetRangeMax -> high++
                r.mgDl >= settings.targetRangeMin -> inRange++
                r.mgDl >= VERY_LOW_THRESHOLD -> low++
                else -> veryLow++
            }
        }

        val count = periodReadings.size
        val avgBg = if (count > 0) periodReadings.map { it.mgDl.toDouble() }.average() else null
        val gmi = avgBg?.let { 3.31 + 0.02392 * it }
        val cv = if (count >= 2 && avgBg != null && avgBg > 0.0) {
            val variance = periodReadings.sumOf { (it.mgDl - avgBg) * (it.mgDl - avgBg) } / count
            (sqrt(variance) / avgBg) * 100.0
        } else {
            null
        }

        val activeDays = distinctDays.size.coerceAtMost(period.days)
        val divisorDays = activeDays.coerceAtLeast(1).toDouble()
        val totalBolus = periodEntries.sumOf { it.bolusUnits ?: 0.0 }
        val totalCarbs = periodEntries.sumOf { it.carbsGrams ?: 0.0 }

        val agpBuckets = buckets.mapIndexedNotNull { idx, values ->
            if (values.isEmpty()) return@mapIndexedNotNull null
            val sorted = values.sorted()
            AgpBucket(
                minuteOfDay = idx * 30 + 15,
                p5 = percentile(sorted, 0.05),
                p25 = percentile(sorted, 0.25),
                p50 = percentile(sorted, 0.50),
                p75 = percentile(sorted, 0.75),
                p95 = percentile(sorted, 0.95)
            )
        }

        return ReportSummary(
            period = period,
            fromMillis = fromMillis,
            toMillis = nowMillis,
            totalReadings = count,
            activeDays = activeDays,
            veryHighPct = if (count > 0) veryHigh * 100.0 / count else 0.0,
            highPct = if (count > 0) high * 100.0 / count else 0.0,
            inRangePct = if (count > 0) inRange * 100.0 / count else 0.0,
            lowPct = if (count > 0) low * 100.0 / count else 0.0,
            veryLowPct = if (count > 0) veryLow * 100.0 / count else 0.0,
            averageBg = avgBg,
            gmiPercent = gmi,
            cvPercent = cv,
            avgDailyBolus = totalBolus / divisorDays,
            avgDailyCarbs = totalCarbs / divisorDays,
            agpBuckets = agpBuckets
        )
    }

    private fun percentile(sorted: List<Int>, p: Double): Float {
        if (sorted.size == 1) return sorted[0].toFloat()
        val index = p * (sorted.size - 1)
        val lower = floor(index).toInt().coerceIn(0, sorted.lastIndex)
        val upper = ceil(index).toInt().coerceIn(0, sorted.lastIndex)
        if (lower == upper) return sorted[lower].toFloat()
        val weight = index - lower
        return (sorted[lower] * (1.0 - weight) + sorted[upper] * weight).toFloat()
    }
}
