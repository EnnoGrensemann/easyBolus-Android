package de.easybolus.domain

import de.easybolus.data.settings.GlucoseUnit
import java.util.Locale
import kotlin.math.roundToInt

object GlucoseFormatter {
    const val MG_DL_PER_MMOL = 18.0182

    fun mgDlToMmol(mgDl: Double): Double = mgDl / MG_DL_PER_MMOL

    fun mmolToMgDl(mmol: Double): Double = mmol * MG_DL_PER_MMOL

    fun formatValue(
        mgDl: Int,
        unit: GlucoseUnit,
        locale: Locale = Locale.getDefault()
    ): String = when (unit) {
        GlucoseUnit.MG_DL -> mgDl.toString()
        GlucoseUnit.MMOL_L -> String.format(locale, "%.1f", mgDlToMmol(mgDl.toDouble()))
    }

    fun formatValue(
        mgDl: Double,
        unit: GlucoseUnit,
        locale: Locale = Locale.getDefault()
    ): String = when (unit) {
        GlucoseUnit.MG_DL -> mgDl.roundToInt().toString()
        GlucoseUnit.MMOL_L -> String.format(locale, "%.1f", mgDlToMmol(mgDl))
    }

    fun formatInputValue(
        mgDl: Int,
        unit: GlucoseUnit
    ): String = when (unit) {
        GlucoseUnit.MG_DL -> mgDl.toString()
        GlucoseUnit.MMOL_L -> String.format(Locale.US, "%.1f", mgDlToMmol(mgDl.toDouble()))
    }

    fun formatFactorInput(
        isfMgDl: Double,
        unit: GlucoseUnit
    ): String = when (unit) {
        GlucoseUnit.MG_DL -> {
            if (isfMgDl % 1.0 == 0.0) isfMgDl.toInt().toString()
            else String.format(Locale.US, "%.1f", isfMgDl)
        }
        GlucoseUnit.MMOL_L -> String.format(Locale.US, "%.1f", mgDlToMmol(isfMgDl))
    }

    fun parseToMgDl(input: String, unit: GlucoseUnit): Int? {
        val parsed = input.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (parsed <= 0.0) return null
        return when (unit) {
            GlucoseUnit.MG_DL -> parsed.roundToInt()
            GlucoseUnit.MMOL_L -> mmolToMgDl(parsed).roundToInt()
        }
    }

    fun parseFactorToMgDl(input: String, unit: GlucoseUnit): Double? {
        val parsed = input.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (parsed <= 0.0) return null
        return when (unit) {
            GlucoseUnit.MG_DL -> parsed
            GlucoseUnit.MMOL_L -> mmolToMgDl(parsed)
        }
    }

    fun unitLabel(unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_DL -> "mg/dl"
        GlucoseUnit.MMOL_L -> "mmol/L"
    }
}
