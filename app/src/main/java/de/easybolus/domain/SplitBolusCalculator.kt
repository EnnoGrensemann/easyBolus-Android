package de.easybolus.domain

import kotlin.math.round

data class SplitBolusAllocation(
    val part1Units: Double,
    val part2Units: Double,
    val part1Percent: Int,
    val part2Percent: Int
)

object SplitBolusCalculator {

    const val MIN_DELAY_MINUTES = 30
    const val MAX_DELAY_MINUTES = 300
    const val STEP_DELAY_MINUTES = 30
    const val DEFAULT_DELAY_MINUTES = 120

    val DELAY_OPTIONS_MINUTES: List<Int> =
        (MIN_DELAY_MINUTES..MAX_DELAY_MINUTES step STEP_DELAY_MINUTES).toList()

    fun roundToStep(units: Double, stepSize: Double): Double {
        val safeStep = if (stepSize >= 1.0) 1.0 else 0.5
        return (round(units / safeStep) * safeStep).coerceAtLeast(0.0)
    }

    fun calculateInitialSplit(
        totalUnits: Double,
        part1Percent: Int,
        stepSize: Double
    ): SplitBolusAllocation {
        val safeTotal = roundToStep(totalUnits.coerceAtLeast(0.0), stepSize)
        val clampedPct = part1Percent.coerceIn(10, 90)
        val part1 = roundToStep(safeTotal * (clampedPct / 100.0), stepSize).coerceIn(0.0, safeTotal)
        val part2 = roundToStep((safeTotal - part1).coerceAtLeast(0.0), stepSize)
        return buildAllocation(part1, part2)
    }

    fun shiftSplit(
        currentPart1: Double,
        totalUnits: Double,
        deltaSteps: Int,
        stepSize: Double
    ): SplitBolusAllocation {
        val safeStep = if (stepSize >= 1.0) 1.0 else 0.5
        val safeTotal = roundToStep(totalUnits.coerceAtLeast(0.0), safeStep)
        val nextPart1 = roundToStep(currentPart1 + deltaSteps * safeStep, safeStep)
            .coerceIn(0.0, safeTotal)
        val nextPart2 = roundToStep((safeTotal - nextPart1).coerceAtLeast(0.0), safeStep)
        return buildAllocation(nextPart1, nextPart2)
    }

    private fun buildAllocation(part1: Double, part2: Double): SplitBolusAllocation {
        val sum = part1 + part2
        val pct1 = if (sum > 0.0) round((part1 / sum) * 100.0).toInt().coerceIn(0, 100) else 60
        val pct2 = 100 - pct1
        return SplitBolusAllocation(
            part1Units = part1,
            part2Units = part2,
            part1Percent = pct1,
            part2Percent = pct2
        )
    }
}
