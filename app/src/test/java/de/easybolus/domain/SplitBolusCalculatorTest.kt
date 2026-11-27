package de.easybolus.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitBolusCalculatorTest {

    @Test
    fun calculates6040SplitRoundedToHalfUnit() {
        val alloc = SplitBolusCalculator.calculateInitialSplit(
            totalUnits = 6.0,
            part1Percent = 60,
            stepSize = 0.5
        )
        // 60% of 6.0 = 3.6 -> rounded to 0.5 step = 3.5; part2 = 2.5
        assertEquals(3.5, alloc.part1Units, 0.001)
        assertEquals(2.5, alloc.part2Units, 0.001)
        assertEquals(58, alloc.part1Percent)
        assertEquals(42, alloc.part2Percent)
    }

    @Test
    fun calculatesCustomRatioRoundedToFullUnit() {
        val alloc = SplitBolusCalculator.calculateInitialSplit(
            totalUnits = 10.0,
            part1Percent = 70,
            stepSize = 1.0
        )
        assertEquals(7.0, alloc.part1Units, 0.001)
        assertEquals(3.0, alloc.part2Units, 0.001)
        assertEquals(70, alloc.part1Percent)
        assertEquals(30, alloc.part2Percent)
    }

    @Test
    fun shiftsSplitBetweenPartsWithoutChangingSum() {
        val shiftedDown = SplitBolusCalculator.shiftSplit(
            currentPart1 = 3.5,
            totalUnits = 6.0,
            deltaSteps = -1,
            stepSize = 0.5
        )
        assertEquals(3.0, shiftedDown.part1Units, 0.001)
        assertEquals(3.0, shiftedDown.part2Units, 0.001)

        val clamped = SplitBolusCalculator.shiftSplit(
            currentPart1 = 0.0,
            totalUnits = 6.0,
            deltaSteps = -2,
            stepSize = 0.5
        )
        assertEquals(0.0, clamped.part1Units, 0.001)
        assertEquals(6.0, clamped.part2Units, 0.001)
    }

    @Test
    fun delayOptionsCoverHalfHourStepsUpToFiveHours() {
        assertEquals(10, SplitBolusCalculator.DELAY_OPTIONS_MINUTES.size)
        assertEquals(30, SplitBolusCalculator.DELAY_OPTIONS_MINUTES.first())
        assertEquals(300, SplitBolusCalculator.DELAY_OPTIONS_MINUTES.last())
    }
}
