package de.easybolus.domain

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.easybolus.data.local.DiaryEntryEntity
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

    fun shareCsv(context: Context, entries: List<DiaryEntryEntity>) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "easyBOLUS_Tagebuch_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMANY).format(Date())}.csv"
        val file = File(exportDir, fileName)
        file.writeText(buildCsvContent(entries), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "easyBOLUS Tagebuch Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Tagebuch exportieren"))
    }
}
