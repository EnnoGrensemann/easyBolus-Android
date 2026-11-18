package de.easybolus.domain

import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.data.local.TrendArrow
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    @Test
    fun buildsValidGermanCsvWithHeaderAndRows() {
        val entries = listOf(
            DiaryEntryEntity(
                id = 1,
                timestampMillis = 1_700_000_000_000L,
                type = DiaryEntryType.BOLUS,
                bolusUnits = 2.5,
                bgAtTimeMgDl = 135,
                trendAtTime = TrendArrow.UP_RIGHT,
                note = "Mittag"
            )
        )
        val csv = CsvExporter.buildCsvContent(entries)
        assertTrue(csv.startsWith("Zeitpunkt;Typ;BZ (mg/dl);Trend;Bolus (IE);Kohlenhydrate (g);Basal (IE);Notiz"))
        assertTrue(csv.contains(";Bolus;135;↗;2,50;;;Mittag"))
    }
}
