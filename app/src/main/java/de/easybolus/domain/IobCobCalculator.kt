package de.easybolus.domain

import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object IobCobCalculator {

    /**
     * Realistische exponentielle IOB-Kurve nach dem Loop / OpenAPS-Modell.
     * Nutzt die Wirkdauer (DIA in Stunden) und die Peak-Zeit (z. B. 75 min für Rapid, 55 min für Ultra-Rapid).
     */
    fun calculateExponentialIob(
        entries: List<DiaryEntryEntity>,
        atTimeMillis: Long,
        diaHours: Double,
        peakMinutes: Double = 75.0
    ): Double {
        if (diaHours <= 0.0) return 0.0
        val td = diaHours * 60.0
        val tp = min(peakMinutes.coerceAtLeast(15.0), td * 0.45)

        val tau = tp * (1.0 - tp / td) / (1.0 - 2.0 * tp / td)
        val a = 2.0 * tau / td
        val s = 1.0 / (1.0 - a + (1.0 + a) * exp(-td / tau))

        return entries.sumOf { entry ->
            if (entry.type != DiaryEntryType.BOLUS) return@sumOf 0.0
            val units = entry.bolusUnits ?: return@sumOf 0.0
            if (units <= 0.0 || entry.timestampMillis > atTimeMillis) return@sumOf 0.0
            val t = (atTimeMillis - entry.timestampMillis).toDouble() / 60_000.0
            if (t >= td) return@sumOf 0.0
            if (t <= 0.0) return@sumOf units

            val expTerm = exp(-t / tau)
            val distributed = ((t * t) / (tau * td) - (1.0 - a) * (t / tau + 1.0)) * expTerm + (1.0 - a)
            val remainingRatio = (1.0 - s * distributed).coerceIn(0.0, 1.0)
            units * remainingRatio
        }
    }

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
