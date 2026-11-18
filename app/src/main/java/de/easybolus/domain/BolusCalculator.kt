package de.easybolus.domain

import de.easybolus.data.settings.UserSettings
import kotlin.math.max
import kotlin.math.round

data class BolusCalculationResult(
    val mealBolus: Double,
    val correctionBolus: Double,
    val activeIob: Double,
    val rawTotal: Double,
    val recommendedUnits: Double
)

object BolusCalculator {

    /**
     * Vereinfachter Pen-Bolusrechner:
     * - Feste Faktoren (KH-Faktor, ISF, Zielwert)
     * - Lineares IOB wird abgezogen
     * - Kein COB-Abzug
     * - Rundung auf Pen-Schrittweite (0,5 IE oder 1,0 IE)
     */
    fun calculate(
        currentBgMgDl: Int,
        carbsGrams: Double,
        activeIob: Double,
        settings: UserSettings
    ): BolusCalculationResult {
        val mealBolus = if (carbsGrams > 0.0 && settings.carbRatio > 0.0) {
            carbsGrams / settings.carbRatio
        } else {
            0.0
        }

        val correctionBolus = if (settings.isf > 0.0) {
            (currentBgMgDl - settings.targetBg) / settings.isf
        } else {
            0.0
        }

        val rawTotal = max(0.0, mealBolus + correctionBolus - activeIob)
        val recommended = roundToPenStep(rawTotal, settings.penStepSize)

        return BolusCalculationResult(
            mealBolus = mealBolus,
            correctionBolus = correctionBolus,
            activeIob = activeIob,
            rawTotal = rawTotal,
            recommendedUnits = recommended
        )
    }

    fun roundToPenStep(value: Double, stepSize: Double): Double {
        if (value <= 0.0) return 0.0
        val step = if (stepSize >= 1.0) 1.0 else 0.5
        return round(value / step) * step
    }
}
