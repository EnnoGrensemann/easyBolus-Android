package de.easybolus.domain

import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.settings.CarbFactorMode
import de.easybolus.data.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainEnhancementsTest {

    @Test
    fun convertsCarbFactorModesLosslessly() {
        // 10 g/IE == 1.0 IE/10KH == 1.2 IE/BE (12g)
        assertEquals(10.0, CarbFactorFormatter.fromGramsPerIe(10.0, CarbFactorMode.GRAMS_PER_IE), 0.001)
        assertEquals(1.0, CarbFactorFormatter.fromGramsPerIe(10.0, CarbFactorMode.IE_PER_10G), 0.001)
        assertEquals(1.2, CarbFactorFormatter.fromGramsPerIe(10.0, CarbFactorMode.IE_PER_BE), 0.001)

        assertEquals(10.0, CarbFactorFormatter.toGramsPerIe(1.0, CarbFactorMode.IE_PER_10G)!!, 0.001)
        assertEquals(10.0, CarbFactorFormatter.toGramsPerIe(1.2, CarbFactorMode.IE_PER_BE)!!, 0.001)
        // 2.0 IE/BE -> 6 g/IE
        assertEquals(6.0, CarbFactorFormatter.toGramsPerIe(2.0, CarbFactorMode.IE_PER_BE)!!, 0.001)
    }

    @Test
    fun evaluatesCorrectionAlertWithWaitTimeAndCooldown() {
        val settings = UserSettings(
            isf = 40.0,
            targetBg = 100,
            penStepSize = 0.5,
            correctionAlertEnabled = true,
            correctionAlertMinUnits = 1.5,
            correctionAlertWaitMinutes = 20
        )
        val t0 = 1_000_000_000L
        // BG 180 -> raw correction = 2.0 IE, IOB = 0.2 -> net = 1.8 IE (>= 1.5)
        val d1 = CorrectionAlertEvaluator.evaluate(180, 0.2, settings, CorrectionAlertState(), t0)
        assertFalse(d1.shouldNotify)
        assertEquals(t0, d1.nextState.conditionSinceMillis)

        // Nach 10 min noch nicht warten vorbei
        val d2 = CorrectionAlertEvaluator.evaluate(180, 0.2, settings, d1.nextState, t0 + 10 * 60_000L)
        assertFalse(d2.shouldNotify)

        // Nach 20 min -> auslösen!
        val d3 = CorrectionAlertEvaluator.evaluate(180, 0.2, settings, d2.nextState, t0 + 20 * 60_000L)
        assertTrue(d3.shouldNotify)
        assertEquals(2.0, d3.recommendedCorrectionUnits, 0.001)

        // 5 min später -> Cooldown blockiert
        val d4 = CorrectionAlertEvaluator.evaluate(185, 0.1, settings, d3.nextState, t0 + 25 * 60_000L)
        assertFalse(d4.shouldNotify)

        // BZ fällt unter Schwelle -> conditionSince zurückgesetzt
        val d5 = CorrectionAlertEvaluator.evaluate(130, 0.0, settings, d4.nextState, t0 + 30 * 60_000L)
        assertFalse(d5.shouldNotify)
        assertEquals(0L, d5.nextState.conditionSinceMillis)
    }

    @Test
    fun calculatesFiveTierTirAndAgpBuckets() {
        val now = 1_700_000_000_000L
        val settings = UserSettings(targetRangeMin = 70, targetRangeMax = 180)
        val readings = listOf(
            GlucoseReadingEntity(timestampMillis = now - 60_000L, mgDl = 50),   // veryLow
            GlucoseReadingEntity(timestampMillis = now - 120_000L, mgDl = 65),  // low
            GlucoseReadingEntity(timestampMillis = now - 180_000L, mgDl = 120), // inRange
            GlucoseReadingEntity(timestampMillis = now - 240_000L, mgDl = 200), // high
            GlucoseReadingEntity(timestampMillis = now - 300_000L, mgDl = 260)  // veryHigh
        )
        val report = ReportCalculator.calculate(readings, emptyList(), ReportPeriod.DAYS_7, settings, now)
        assertEquals(5, report.totalReadings)
        assertEquals(20.0, report.veryLowPct, 0.01)
        assertEquals(20.0, report.lowPct, 0.01)
        assertEquals(20.0, report.inRangePct, 0.01)
        assertEquals(20.0, report.highPct, 0.01)
        assertEquals(20.0, report.veryHighPct, 0.01)
        assertTrue(report.agpBuckets.isNotEmpty())
    }
}
