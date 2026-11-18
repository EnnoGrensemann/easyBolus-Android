package de.easybolus.domain

import de.easybolus.data.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class BolusCalculatorTest {

    private val defaultSettings = UserSettings(
        carbRatio = 10.0,
        isf = 40.0,
        targetBg = 100,
        diaHours = 4.0,
        penStepSize = 0.5
    )

    @Test
    fun calculatesMealAndCorrectionMinusIobWithHalfUnitRounding() {
        // 45g KH / 10 = 4.5 IE; (140 - 100) / 40 = 1.0 IE; IOB = 1.2 IE -> 4.3 -> 4.5 IE
        val result = BolusCalculator.calculate(
            currentBgMgDl = 140,
            carbsGrams = 45.0,
            activeIob = 1.2,
            settings = defaultSettings
        )
        assertEquals(4.5, result.mealBolus, 0.001)
        assertEquals(1.0, result.correctionBolus, 0.001)
        assertEquals(4.3, result.rawTotal, 0.001)
        assertEquals(4.5, result.recommendedUnits, 0.001)
    }

    @Test
    fun roundsToWholeUnitWhenPenStepIsOne() {
        val settings = defaultSettings.copy(penStepSize = 1.0)
        // 34g / 10 = 3.4 IE -> rounds to 3.0 IE
        val result = BolusCalculator.calculate(
            currentBgMgDl = 100,
            carbsGrams = 34.0,
            activeIob = 0.0,
            settings = settings
        )
        assertEquals(3.0, result.recommendedUnits, 0.001)
    }

    @Test
    fun neverReturnsNegativeBolusWhenIobExceedsNeed() {
        val result = BolusCalculator.calculate(
            currentBgMgDl = 80,
            carbsGrams = 10.0,
            activeIob = 3.0,
            settings = defaultSettings
        )
        assertEquals(0.0, result.rawTotal, 0.001)
        assertEquals(0.0, result.recommendedUnits, 0.001)
    }

    @Test
    fun usesHourlyCarbRatioWhenEnabled() {
        val hourly = MutableList(24) { 10.0 }.apply {
            this[7] = 8.0
            this[13] = 12.0
        }
        val settings = defaultSettings.copy(
            useHourlyCarbRatio = true,
            hourlyCarbRatios = hourly
        )

        val morning = BolusCalculator.calculate(
            currentBgMgDl = 100,
            carbsGrams = 40.0,
            activeIob = 0.0,
            settings = settings,
            hourOfDay = 7
        )
        assertEquals(5.0, morning.mealBolus, 0.001)
        assertEquals(5.0, morning.recommendedUnits, 0.001)

        val afternoon = BolusCalculator.calculate(
            currentBgMgDl = 100,
            carbsGrams = 36.0,
            activeIob = 0.0,
            settings = settings,
            hourOfDay = 13
        )
        assertEquals(3.0, afternoon.mealBolus, 0.001)
        assertEquals(3.0, afternoon.recommendedUnits, 0.001)
    }
}
