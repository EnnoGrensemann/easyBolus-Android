package de.easybolus.domain

import de.easybolus.data.settings.CarbFactorMode
import java.util.Locale

object CarbFactorFormatter {

    /**
     * Wandelt intern gespeicherte g KH / 1 IE in den Anzeigewert für den gewählten Faktor-Modus um.
     */
    fun fromGramsPerIe(gramsPerIe: Double, mode: CarbFactorMode): Double {
        if (gramsPerIe <= 0.0) return 0.0
        return when (mode) {
            CarbFactorMode.GRAMS_PER_IE -> gramsPerIe
            CarbFactorMode.IE_PER_10G -> 10.0 / gramsPerIe
            CarbFactorMode.IE_PER_BE -> 12.0 / gramsPerIe
        }
    }

    /**
     * Wandelt einen Eingabewert im gewählten Faktor-Modus zurück in intern gespeicherte g KH / 1 IE.
     */
    fun toGramsPerIe(displayValue: Double, mode: CarbFactorMode): Double? {
        if (displayValue <= 0.0) return null
        val gPerIe = when (mode) {
            CarbFactorMode.GRAMS_PER_IE -> displayValue
            CarbFactorMode.IE_PER_10G -> 10.0 / displayValue
            CarbFactorMode.IE_PER_BE -> 12.0 / displayValue
        }
        return gPerIe.coerceIn(0.5, 200.0)
    }

    fun formatInput(gramsPerIe: Double, mode: CarbFactorMode): String {
        val display = fromGramsPerIe(gramsPerIe, mode)
        return when (mode) {
            CarbFactorMode.GRAMS_PER_IE -> {
                if (display % 1.0 == 0.0) display.toInt().toString()
                else String.format(Locale.US, "%.1f", display)
            }
            CarbFactorMode.IE_PER_10G, CarbFactorMode.IE_PER_BE -> {
                String.format(Locale.US, "%.2f", display).trimEnd('0').trimEnd('.')
            }
        }
    }

    fun formatDisplay(
        gramsPerIe: Double,
        mode: CarbFactorMode,
        locale: Locale = Locale.getDefault()
    ): String {
        val display = fromGramsPerIe(gramsPerIe, mode)
        return when (mode) {
            CarbFactorMode.GRAMS_PER_IE -> String.format(locale, "%.1f", display)
            CarbFactorMode.IE_PER_10G, CarbFactorMode.IE_PER_BE -> String.format(locale, "%.2f", display)
        }
    }

    fun parseToGramsPerIe(input: String, mode: CarbFactorMode): Double? {
        val parsed = input.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return toGramsPerIe(parsed, mode)
    }
}
