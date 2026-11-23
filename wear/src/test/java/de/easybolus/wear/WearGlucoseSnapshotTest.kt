package de.easybolus.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WearGlucoseSnapshotTest {

    @Test
    fun formatsFreshMgDlAndMmolReadingsCorrectly() {
        val now = 1_700_000_000_000L
        val mgDlSnap = WearGlucoseSnapshot(
            hasData = true,
            mgDl = 114,
            trendSymbol = "→",
            hasDelta = true,
            deltaMgDl = 2,
            readingTimestampMillis = now - 2 * 60_000L,
            isMmol = false,
            targetRangeMin = 70,
            targetRangeMax = 180
        )
        assertFalse(mgDlSnap.isStale(now))
        assertEquals("114", mgDlSnap.formattedBg(now, Locale.GERMANY))
        assertEquals("114 →", mgDlSnap.formattedBgWithTrend(now, Locale.GERMANY))
        assertEquals("Δ +2", mgDlSnap.formattedDelta(now, Locale.GERMANY))
        assertEquals(WearGlucoseSnapshot.COLOR_IN_RANGE, mgDlSnap.statusColorArgb(now))

        val mmolSnap = mgDlSnap.copy(isMmol = true, mgDl = 180, deltaMgDl = -18)
        assertEquals("10,0", mmolSnap.formattedBg(now, Locale.GERMANY))
        assertEquals("Δ -1,0", mmolSnap.formattedDelta(now, Locale.GERMANY))
    }

    @Test
    fun marksCgmReadingOlderThan15MinutesAsStale() {
        val now = 1_700_000_000_000L
        val staleSnap = WearGlucoseSnapshot(
            hasData = true,
            mgDl = 65,
            trendSymbol = "↓",
            hasDelta = true,
            deltaMgDl = -5,
            readingTimestampMillis = now - 16 * 60_000L,
            jugglucoEnabled = true
        )
        assertTrue(staleSnap.isStale(now))
        assertEquals("---", staleSnap.formattedBg(now, Locale.GERMANY))
        assertEquals("---", staleSnap.formattedBgWithTrend(now, Locale.GERMANY))
        assertNull(staleSnap.formattedDelta(now, Locale.GERMANY))
        assertEquals(WearGlucoseSnapshot.COLOR_MUTED, staleSnap.statusColorArgb(now))
    }

    @Test
    fun returnsWarningRedWhenBelowTargetRange() {
        val now = 1_700_000_000_000L
        val lowSnap = WearGlucoseSnapshot(
            hasData = true,
            mgDl = 64,
            readingTimestampMillis = now - 60_000L,
            targetRangeMin = 70,
            targetRangeMax = 180
        )
        assertEquals(WearGlucoseSnapshot.COLOR_LOW, lowSnap.statusColorArgb(now))
    }
}
