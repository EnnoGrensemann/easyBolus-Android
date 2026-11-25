package de.easybolus.domain

import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
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
    val minuteOfDay: Int, // 15, 45, ..., 1425
    val p5: Float,
    val p25: Float,
    val p50: Float,
    val p75: Float,
    val p95: Float
)

enum class DaySegment {
    NIGHT,     // 00:00 - 06:00
    MORNING,   // 06:00 - 12:00
    AFTERNOON, // 12:00 - 18:00
    EVENING    // 18:00 - 24:00
}

data class TimeOfDayStats(
    val segment: DaySegment,
    val readingCount: Int,
    val averageBg: Double?,
    val inRangePct: Double?,
    val lowPct: Double?,
    val highPct: Double?
)

data class HypoSummary(
    val lowEventsCount: Int,
    val veryLowEventsCount: Int,
    val nightHypoCount: Int,
    val dayHypoCount: Int,
    val lowestBgMgDl: Int?
)

data class TherapySummary(
    val avgDailyBasal: Double,
    val avgDailyBolus: Double,
    val avgTotalDailyInsulin: Double,
    val bolusCountPerDay: Double,
    val avgBolusSize: Double?,
    val avgDailyCarbs: Double
)

data class VariabilitySummary(
    val sdMgDl: Double?,
    val minBgMgDl: Int?,
    val maxBgMgDl: Int?,
    val sensorActivePct: Double
)

enum class PatternType {
    LOW,
    HIGH,
    IN_RANGE
}

data class PatternInsight(
    val type: PatternType,
    val segment: DaySegment?
)

data class DailySparkline(
    val dayStartMillis: Long,
    val points: List<Pair<Float, Int>> // (hourFraction 0f..24f, mgDl)
)

data class ReportDetails(
    val timeOfDay: List<TimeOfDayStats>,
    val patternInsight: PatternInsight,
    val dailySparklines: List<DailySparkline>,
    val hypo: HypoSummary,
    val therapy: TherapySummary,
    val variability: VariabilitySummary
)

data class HourlyMinMax(
    val hour: Int,
    val minMgDl: Int?,
    val maxMgDl: Int?
)

data class DailyReportData(
    val dayStartMillis: Long,
    val dayEndMillis: Long,
    val readings: List<GlucoseReadingEntity>,
    val entries: List<DiaryEntryEntity>,
    val hourlyMinMax: List<HourlyMinMax>,
    val averageBg: Double?,
    val inRangePct: Double?,
    val totalBolus: Double,
    val totalCarbs: Double,
    val totalBasal: Double
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
    val gmiMmolMol: Double?,
    val cvPercent: Double?,
    val avgDailyBolus: Double,
    val avgDailyCarbs: Double,
    val agpBuckets: List<AgpBucket>,
    val details: ReportDetails
)

object ReportCalculator {

    const val VERY_HIGH_THRESHOLD = 250
    const val VERY_LOW_THRESHOLD = 54
    const val LOW_HYPO_THRESHOLD = 70
    private const val HYPO_EPISODE_GAP_MS = 15L * 60_000L

    fun calculate(
        readings: List<GlucoseReadingEntity>,
        entries: List<DiaryEntryEntity>,
        period: ReportPeriod,
        settings: UserSettings,
        nowMillis: Long = System.currentTimeMillis()
    ): ReportSummary {
        val fromMillis = nowMillis - period.days * 24L * 3600_000L
        val periodReadings = readings
            .filter { it.timestampMillis in fromMillis..nowMillis }
            .sortedBy { it.timestampMillis }
        val periodEntries = entries.filter { it.timestampMillis in fromMillis..nowMillis }

        val cal = Calendar.getInstance()
        val distinctDays = HashSet<Int>()
        val buckets = Array(48) { mutableListOf<Int>() }
        val segmentReadings = DaySegment.entries.associateWith { mutableListOf<Int>() }

        var veryHigh = 0
        var high = 0
        var inRange = 0
        var low = 0
        var veryLow = 0

        for (r in periodReadings) {
            cal.timeInMillis = r.timestampMillis
            val dayKey = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
            distinctDays.add(dayKey)

            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val bucketIdx = ((hour * 60 + cal.get(Calendar.MINUTE)) / 30).coerceIn(0, 47)
            buckets[bucketIdx].add(r.mgDl)

            val segment = when (hour) {
                in 0..5 -> DaySegment.NIGHT
                in 6..11 -> DaySegment.MORNING
                in 12..17 -> DaySegment.AFTERNOON
                else -> DaySegment.EVENING
            }
            segmentReadings[segment]?.add(r.mgDl)

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
        val gmiMmol = gmi?.let { 10.929 * (it - 2.15) }
        val sd = if (count >= 2 && avgBg != null) {
            val variance = periodReadings.sumOf { (it.mgDl - avgBg) * (it.mgDl - avgBg) } / count
            sqrt(variance)
        } else {
            null
        }
        val cv = if (sd != null && avgBg != null && avgBg > 0.0) {
            (sd / avgBg) * 100.0
        } else {
            null
        }

        val activeDays = distinctDays.size.coerceAtMost(period.days)
        val divisorDays = activeDays.coerceAtLeast(1).toDouble()
        val totalBolus = periodEntries.sumOf { it.bolusUnits ?: 0.0 }
        val totalCarbs = periodEntries.sumOf { it.carbsGrams ?: 0.0 }
        val totalBasal = periodEntries.sumOf { it.basalUnits ?: 0.0 }
        val bolusEntries = periodEntries.filter { (it.bolusUnits ?: 0.0) > 0.0 || it.type == DiaryEntryType.BOLUS }
        val bolusValues = bolusEntries.mapNotNull { it.bolusUnits?.takeIf { u -> u > 0.0 } }

        val rawBuckets: Array<AgpBucket?> = Array(48) { idx ->
            val values = buckets[idx]
            if (values.isEmpty()) {
                null
            } else {
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
        }
        val smoothedAgpBuckets = smoothAgpBuckets(rawBuckets)

        val timeOfDayStats = DaySegment.entries.map { seg ->
            val vals = segmentReadings[seg].orEmpty()
            val c = vals.size
            if (c == 0) {
                TimeOfDayStats(seg, 0, null, null, null, null)
            } else {
                val segInRange = vals.count { it in settings.targetRangeMin..settings.targetRangeMax }
                val segLow = vals.count { it < settings.targetRangeMin }
                val segHigh = vals.count { it > settings.targetRangeMax }
                TimeOfDayStats(
                    segment = seg,
                    readingCount = c,
                    averageBg = vals.average(),
                    inRangePct = segInRange * 100.0 / c,
                    lowPct = segLow * 100.0 / c,
                    highPct = segHigh * 100.0 / c
                )
            }
        }

        val patternInsight = detectPrimaryPattern(timeOfDayStats)
        val dailySparklines = buildRecentSparklines(periodReadings, nowMillis, daysCount = 7)
        val hypoSummary = computeHypoSummary(periodReadings, settings)

        // Expected 5-min readings: 288/day -> cap at 100%
        val expectedReadings = period.days * 288.0
        val sensorActivePct = if (expectedReadings > 0.0) {
            (count * 100.0 / expectedReadings).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        val details = ReportDetails(
            timeOfDay = timeOfDayStats,
            patternInsight = patternInsight,
            dailySparklines = dailySparklines,
            hypo = hypoSummary,
            therapy = TherapySummary(
                avgDailyBasal = totalBasal / divisorDays,
                avgDailyBolus = totalBolus / divisorDays,
                avgTotalDailyInsulin = (totalBolus + totalBasal) / divisorDays,
                bolusCountPerDay = bolusValues.size / divisorDays,
                avgBolusSize = if (bolusValues.isNotEmpty()) bolusValues.average() else null,
                avgDailyCarbs = totalCarbs / divisorDays
            ),
            variability = VariabilitySummary(
                sdMgDl = sd,
                minBgMgDl = periodReadings.minOfOrNull { it.mgDl },
                maxBgMgDl = periodReadings.maxOfOrNull { it.mgDl },
                sensorActivePct = sensorActivePct
            )
        )

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
            gmiMmolMol = gmiMmol,
            cvPercent = cv,
            avgDailyBolus = totalBolus / divisorDays,
            avgDailyCarbs = totalCarbs / divisorDays,
            agpBuckets = smoothedAgpBuckets,
            details = details
        )
    }

    fun calculateDailyReport(
        readings: List<GlucoseReadingEntity>,
        entries: List<DiaryEntryEntity>,
        dayOffset: Int,
        settings: UserSettings,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyReportData {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -dayOffset.coerceIn(0, 13))
        }
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24L * 3600_000L - 1L

        val dayReadings = readings
            .filter { it.timestampMillis in dayStart..dayEnd }
            .sortedBy { it.timestampMillis }
        val dayEntries = entries
            .filter { it.timestampMillis in dayStart..dayEnd }
            .sortedBy { it.timestampMillis }

        val hourlyBuckets = Array(24) { mutableListOf<Int>() }
        for (r in dayReadings) {
            cal.timeInMillis = r.timestampMillis
            val h = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            hourlyBuckets[h].add(r.mgDl)
        }
        val hourlyMinMax = (0..23).map { h ->
            val vals = hourlyBuckets[h]
            HourlyMinMax(
                hour = h,
                minMgDl = vals.minOrNull(),
                maxMgDl = vals.maxOrNull()
            )
        }

        val count = dayReadings.size
        val avgBg = if (count > 0) dayReadings.map { it.mgDl.toDouble() }.average() else null
        val inRange = if (count > 0) {
            dayReadings.count { it.mgDl in settings.targetRangeMin..settings.targetRangeMax } * 100.0 / count
        } else {
            null
        }

        return DailyReportData(
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            readings = dayReadings,
            entries = dayEntries,
            hourlyMinMax = hourlyMinMax,
            averageBg = avgBg,
            inRangePct = inRange,
            totalBolus = dayEntries.sumOf { it.bolusUnits ?: 0.0 },
            totalCarbs = dayEntries.sumOf { it.carbsGrams ?: 0.0 },
            totalBasal = dayEntries.sumOf { it.basalUnits ?: 0.0 }
        )
    }

    private fun detectPrimaryPattern(stats: List<TimeOfDayStats>): PatternInsight {
        val worstLow = stats
            .filter { it.readingCount > 0 && (it.lowPct ?: 0.0) >= 4.0 }
            .maxByOrNull { it.lowPct ?: 0.0 }
        if (worstLow != null) {
            return PatternInsight(PatternType.LOW, worstLow.segment)
        }
        val worstHigh = stats
            .filter { it.readingCount > 0 && (it.highPct ?: 0.0) >= 25.0 }
            .maxByOrNull { it.highPct ?: 0.0 }
        if (worstHigh != null) {
            return PatternInsight(PatternType.HIGH, worstHigh.segment)
        }
        return PatternInsight(PatternType.IN_RANGE, null)
    }

    private fun buildRecentSparklines(
        sortedReadings: List<GlucoseReadingEntity>,
        nowMillis: Long,
        daysCount: Int
    ): List<DailySparkline> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis

        return (daysCount - 1 downTo 0).map { offset ->
            val dayStart = todayStart - offset * 24L * 3600_000L
            val dayEnd = dayStart + 24L * 3600_000L - 1L
            val dayPts = sortedReadings
                .filter { it.timestampMillis in dayStart..dayEnd }
                .let { list ->
                    val step = (list.size / 36).coerceAtLeast(1)
                    list.filterIndexed { idx, _ -> idx % step == 0 || idx == list.lastIndex }
                }
                .map { r ->
                    val hourFrac = ((r.timestampMillis - dayStart).toFloat() / 3600_000f).coerceIn(0f, 24f)
                    hourFrac to r.mgDl
                }
            DailySparkline(dayStartMillis = dayStart, points = dayPts)
        }
    }

    private fun computeHypoSummary(
        sortedReadings: List<GlucoseReadingEntity>,
        settings: UserSettings
    ): HypoSummary {
        val lowThreshold = settings.targetRangeMin.coerceAtMost(LOW_HYPO_THRESHOLD)
        var lowEvents = 0
        var veryLowEvents = 0
        var nightHypos = 0
        var dayHypos = 0
        var lastLowTs: Long? = null
        var lastVeryLowTs: Long? = null
        val cal = Calendar.getInstance()

        for (r in sortedReadings) {
            if (r.mgDl < lowThreshold) {
                if (lastLowTs == null || (r.timestampMillis - lastLowTs) > HYPO_EPISODE_GAP_MS) {
                    lowEvents++
                    cal.timeInMillis = r.timestampMillis
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    if (hour < 6) nightHypos++ else dayHypos++
                }
                lastLowTs = r.timestampMillis
            } else {
                lastLowTs = null
            }

            if (r.mgDl < VERY_LOW_THRESHOLD) {
                if (lastVeryLowTs == null || (r.timestampMillis - lastVeryLowTs) > HYPO_EPISODE_GAP_MS) {
                    veryLowEvents++
                }
                lastVeryLowTs = r.timestampMillis
            } else {
                lastVeryLowTs = null
            }
        }

        return HypoSummary(
            lowEventsCount = lowEvents,
            veryLowEventsCount = veryLowEvents,
            nightHypoCount = nightHypos,
            dayHypoCount = dayHypos,
            lowestBgMgDl = sortedReadings.minOfOrNull { it.mgDl }
        )
    }

    /**
     * Circular 3-point weighted smoothing [0.25, 0.50, 0.25] across 48 half-hour buckets
     * to avoid jagged steps in the AGP curve while preserving daily patterns.
     */
    private fun smoothAgpBuckets(raw: Array<AgpBucket?>): List<AgpBucket> {
        val result = ArrayList<AgpBucket>(48)
        for (i in 0 until 48) {
            val center = raw[i] ?: continue
            val prev = raw[(i + 47) % 48]
            val next = raw[(i + 1) % 48]

            var wSum = 0.50f
            var p5 = center.p5 * 0.50f
            var p25 = center.p25 * 0.50f
            var p50 = center.p50 * 0.50f
            var p75 = center.p75 * 0.50f
            var p95 = center.p95 * 0.50f

            if (prev != null) {
                wSum += 0.25f
                p5 += prev.p5 * 0.25f
                p25 += prev.p25 * 0.25f
                p50 += prev.p50 * 0.25f
                p75 += prev.p75 * 0.25f
                p95 += prev.p95 * 0.25f
            }
            if (next != null) {
                wSum += 0.25f
                p5 += next.p5 * 0.25f
                p25 += next.p25 * 0.25f
                p50 += next.p50 * 0.25f
                p75 += next.p75 * 0.25f
                p95 += next.p95 * 0.25f
            }

            result.add(
                AgpBucket(
                    minuteOfDay = center.minuteOfDay,
                    p5 = p5 / wSum,
                    p25 = p25 / wSum,
                    p50 = p50 / wSum,
                    p75 = p75 / wSum,
                    p95 = p95 / wSum
                )
            )
        }
        return result
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
