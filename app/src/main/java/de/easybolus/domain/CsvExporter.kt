package de.easybolus.domain

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.settings.GlucoseUnit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY)

    fun buildCsvContent(entries: List<DiaryEntryEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("Zeitpunkt;Typ;BZ (mg/dl);Trend;Bolus (IE);Kohlenhydrate (g);Basal (IE);Notiz")
        for (entry in entries) {
            val timeStr = dateFormat.format(Date(entry.timestampMillis))
            val bgStr = entry.bgAtTimeMgDl?.toString() ?: ""
            val trendStr = entry.trendAtTime?.symbol ?: ""
            val bolusStr = entry.bolusUnits?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: ""
            val carbsStr = entry.carbsGrams?.let { String.format(Locale.GERMANY, "%.1f", it) } ?: ""
            val basalStr = entry.basalUnits?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: ""
            val noteStr = (entry.note ?: "").replace(";", ",").replace("\n", " ")
            sb.appendLine("$timeStr;${entry.type.label};$bgStr;$trendStr;$bolusStr;$carbsStr;$basalStr;$noteStr")
        }
        return sb.toString()
    }

    /**
     * Kombinierter chronologischer CSV-Export aller aufgezeichneten Glukosewerte + aller Tagebucheinträge.
     */
    fun buildFullCsvContent(
        readings: List<GlucoseReadingEntity>,
        entries: List<DiaryEntryEntity>,
        unit: GlucoseUnit = GlucoseUnit.MG_DL
    ): String {
        val unitLabel = GlucoseFormatter.unitLabel(unit)
        val locale = Locale.getDefault()
        val sb = StringBuilder()
        sb.appendLine("Zeitpunkt;Kategorie;BZ ($unitLabel);Trend;Bolus (IE);Kohlenhydrate (g);Basal (IE);Quelle/Notiz")

        data class RowItem(val ts: Long, val line: String)
        val rows = ArrayList<RowItem>(readings.size + entries.size)

        for (r in readings) {
            val timeStr = dateFormat.format(Date(r.timestampMillis))
            val bgStr = GlucoseFormatter.formatValue(r.mgDl, unit, locale)
            val trendStr = r.trendArrow.symbol
            val src = r.source.replace(";", ",")
            rows.add(RowItem(r.timestampMillis, "$timeStr;Glukose;$bgStr;$trendStr;;;;$src"))
        }

        for (e in entries) {
            val timeStr = dateFormat.format(Date(e.timestampMillis))
            val bgStr = e.bgAtTimeMgDl?.let { GlucoseFormatter.formatValue(it, unit, locale) } ?: ""
            val trendStr = e.trendAtTime?.symbol ?: ""
            val bolusStr = e.bolusUnits?.let { String.format(locale, "%.2f", it) } ?: ""
            val carbsStr = e.carbsGrams?.let { String.format(locale, "%.1f", it) } ?: ""
            val basalStr = e.basalUnits?.let { String.format(locale, "%.2f", it) } ?: ""
            val noteStr = (e.note ?: "").replace(";", ",").replace("\n", " ")
            rows.add(RowItem(e.timestampMillis, "$timeStr;${e.type.label};$bgStr;$trendStr;$bolusStr;$carbsStr;$basalStr;$noteStr"))
        }

        rows.sortBy { it.ts }
        for (row in rows) {
            sb.appendLine(row.line)
        }
        return sb.toString()
    }

    fun shareCsv(context: Context, entries: List<DiaryEntryEntity>) {
        shareFile(
            context = context,
            fileName = "easyBOLUS_Tagebuch_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMANY).format(Date())}.csv",
            mimeType = "text/csv",
            subject = "easyBOLUS Tagebuch Export",
            chooserTitle = "Tagebuch exportieren",
            content = buildCsvContent(entries)
        )
    }

    fun shareFullCsv(
        context: Context,
        readings: List<GlucoseReadingEntity>,
        entries: List<DiaryEntryEntity>,
        unit: GlucoseUnit
    ) {
        shareFile(
            context = context,
            fileName = "easyBOLUS_Komplett_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMANY).format(Date())}.csv",
            mimeType = "text/csv",
            subject = "easyBOLUS Daten-Export",
            chooserTitle = "Daten exportieren",
            content = buildFullCsvContent(readings, entries, unit)
        )
    }

    private fun shareFile(
        context: Context,
        fileName: String,
        mimeType: String,
        subject: String,
        chooserTitle: String,
        content: String
    ) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)
        file.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }
}
