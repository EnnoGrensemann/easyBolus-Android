package de.easybolus.domain

import de.easybolus.data.settings.UserSettings
import java.util.Calendar
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

data class BolusCalculationResult(
    val mealBolus: Double,
    val correctionBolus: Double,
    val activeIob: Double,
    val rawTotal: Double,
    val recommendedUnits: Double,
    val recommendedCarbsGrams: Int = 0
)

object BolusCalculator {

    /**
     * Vereinfachter Pen-Bolusrechner:
     * - Feste oder stündliche Faktoren (KH-Faktor, ISF, Zielwert)
     * - Aktives IOB wird abgezogen
     * - Kein COB-Abzug
     * - Rundung auf Pen-Schrittweite (0,5 IE oder 1,0 IE)
     * - Bei negativem Gesamtergebnis (niedriger BZ / Überschuss-IOB) Empfehlung zusätzlicher KH in Gramm
     */
    fun calculate(
        currentBgMgDl: Int,
        carbsGrams: Double,
        activeIob: Double,
        settings: UserSettings,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): BolusCalculationResult {
        val activeCarbRatio = settings.carbRatioForHour(hourOfDay)
        val mealBolus = if (carbsGrams > 0.0 && activeCarbRatio > 0.0) {
            carbsGrams / activeCarbRatio
        } else {
            0.0
        }

        val correctionBolus = if (settings.isf > 0.0) {
            (currentBgMgDl - settings.targetBg) / settings.isf
        } else {
            0.0
        }

        val signedTotal = mealBolus + correctionBolus - activeIob
        val rawTotal = max(0.0, signedTotal)
        val recommended = roundToPenStep(rawTotal, settings.penStepSize)

        val recommendedCarbsGrams = if (signedTotal < 0.0 && activeCarbRatio > 0.0) {
            val neededGrams = -signedTotal * activeCarbRatio
            val shouldSuggest = neededGrams >= 3.0 ||
                (currentBgMgDl < settings.targetRangeMin && neededGrams >= 1.0)
            if (shouldSuggest) neededGrams.roundToInt().coerceAtLeast(1) else 0
        } else {
            0
        }

        return BolusCalculationResult(
            mealBolus = mealBolus,
            correctionBolus = correctionBolus,
            activeIob = activeIob,
            rawTotal = rawTotal,
            recommendedUnits = recommended,
            recommendedCarbsGrams = recommendedCarbsGrams
        )
    }

    fun roundToPenStep(value: Double, stepSize: Double): Double {
        if (value <= 0.0) return 0.0
        val step = if (stepSize >= 1.0) 1.0 else 0.5
        return round(value / step) * step
    }
}
