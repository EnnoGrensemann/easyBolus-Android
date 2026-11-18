package de.easybolus.domain

import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import kotlin.math.max

object IobCobCalculator {

    /**
     * Einfache lineare IOB-Berechnung (Insulin on Board):
     * IOB = Summe(Dosis * max(0, 1 - vergangeneMinuten / (DIA * 60)))
     */
    fun calculateLinearIob(
        entries: List<DiaryEntryEntity>,
        atTimeMillis: Long,
        diaHours: Double
    ): Double {
        if (diaHours <= 0.0) return 0.0
        val diaMillis = diaHours * 3600_000.0
        return entries.sumOf { entry ->
            if (entry.type != DiaryEntryType.BOLUS) return@sumOf 0.0
            val units = entry.bolusUnits ?: return@sumOf 0.0
            if (units <= 0.0 || entry.timestampMillis > atTimeMillis) return@sumOf 0.0
            val elapsed = (atTimeMillis - entry.timestampMillis).toDouble()
            val remainingRatio = max(0.0, 1.0 - (elapsed / diaMillis))
            units * remainingRatio
        }
    }

    /**
     * Einfache lineare COB-Berechnung für die Anzeige im Badge & Chart
     * (wird NICHT vom Bolus abgezogen).
     */
    fun calculateLinearCob(
        entries: List<DiaryEntryEntity>,
        atTimeMillis: Long,
        absorptionHours: Double
    ): Double {
        if (absorptionHours <= 0.0) return 0.0
        val durationMillis = absorptionHours * 3600_000.0
        return entries.sumOf { entry ->
            val carbs = entry.carbsGrams ?: return@sumOf 0.0
            if (carbs <= 0.0 || entry.timestampMillis > atTimeMillis) return@sumOf 0.0
            val elapsed = (atTimeMillis - entry.timestampMillis).toDouble()
            val remainingRatio = max(0.0, 1.0 - (elapsed / durationMillis))
            carbs * remainingRatio
        }
    }
}
