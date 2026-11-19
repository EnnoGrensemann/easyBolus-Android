package de.easybolus.domain

import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import org.junit.Assert.assertEquals
import org.junit.Test

class IobCobCalculatorTest {

    @Test
    fun calculatesLinearIobDecayOverDia() {
        val t0 = 1_000_000_000L
        val entries = listOf(
            DiaryEntryEntity(
                timestampMillis = t0,
                type = DiaryEntryType.BOLUS,
                bolusUnits = 4.0
            )
        )
        // Nach 1 Stunde bei 4h DIA -> 75% aktiv = 3.0 IE
        assertEquals(3.0, IobCobCalculator.calculateLinearIob(entries, t0 + 3600_000L, 4.0), 0.001)
        // Nach 2 Stunden bei 4h DIA -> 50% aktiv = 2.0 IE
        assertEquals(2.0, IobCobCalculator.calculateLinearIob(entries, t0 + 2 * 3600_000L, 4.0), 0.001)
        // Nach 4 Stunden -> 0.0 IE
        assertEquals(0.0, IobCobCalculator.calculateLinearIob(entries, t0 + 4 * 3600_000L, 4.0), 0.001)
    }

    @Test
    fun calculatesLinearCobDecay() {
        val t0 = 1_000_000_000L
        val entries = listOf(
            DiaryEntryEntity(
                timestampMillis = t0,
                type = DiaryEntryType.CARBS,
                carbsGrams = 60.0
            )
        )
        // Nach 1.5h bei 3h Resorption -> 30.0 g
        assertEquals(30.0, IobCobCalculator.calculateLinearCob(entries, t0 + 90 * 60_000L, 3.0), 0.001)
    }

    @Test
    fun calculatesExponentialIobCurve() {
        val t0 = 1_000_000_000L
        val entries = listOf(
            DiaryEntryEntity(
                timestampMillis = t0,
                type = DiaryEntryType.BOLUS,
                bolusUnits = 4.0
            )
        )
        // Direkt bei Abgabe: 100% aktiv = 4.0 IE
        assertEquals(4.0, IobCobCalculator.calculateExponentialIob(entries, t0, 4.0, 75.0), 0.001)
        // Nach 1h (Rapid 75 min Peak): noch ~73.7% aktiv (flacher Start, nicht linear 75%)
        val after1hRapid = IobCobCalculator.calculateExponentialIob(entries, t0 + 60 * 60_000L, 4.0, 75.0)
        assertEquals(2.95, after1hRapid, 0.05)
        // Ultra-Rapid (55 min Peak) wirkt nach 1h schneller als Rapid -> weniger Rest-IOB
        val after1hUltra = IobCobCalculator.calculateExponentialIob(entries, t0 + 60 * 60_000L, 4.0, 55.0)
        assert(after1hUltra < after1hRapid)
        // Nach 4h (DIA): 0.0 IE
        assertEquals(0.0, IobCobCalculator.calculateExponentialIob(entries, t0 + 4 * 3600_000L, 4.0, 75.0), 0.001)
    }
}
