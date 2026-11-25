package de.easybolus.domain

import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.GlucoseReadingEntity
import java.util.Locale

data class MergeDiffResult(
    val newReadings: List<GlucoseReadingEntity>,
    val newEntries: List<DiaryEntryEntity>
)

/**
 * Deduplicates incoming backup readings and diary entries against existing local records
 * so repeated restores or Drive syncs never create duplicate rows.
 */
object BackupMerger {

    fun readingKey(r: GlucoseReadingEntity): String =
        "${r.timestampMillis}|${r.mgDl}|${r.source}"

    fun entryKey(e: DiaryEntryEntity): String {
        val bolus = e.bolusUnits?.let { String.format(Locale.US, "%.3f", it) } ?: ""
        val carbs = e.carbsGrams?.let { String.format(Locale.US, "%.3f", it) } ?: ""
        val basal = e.basalUnits?.let { String.format(Locale.US, "%.3f", it) } ?: ""
        val note = e.note?.trim().orEmpty()
        return "${e.timestampMillis}|${e.type.name}|$bolus|$carbs|$basal|$note"
    }

    fun computeDiff(
        existingReadings: List<GlucoseReadingEntity>,
        existingEntries: List<DiaryEntryEntity>,
        incomingReadings: List<GlucoseReadingEntity>,
        incomingEntries: List<DiaryEntryEntity>
    ): MergeDiffResult {
        val existingReadingKeys = HashSet<String>(existingReadings.size * 2)
        for (r in existingReadings) {
            existingReadingKeys.add(readingKey(r))
        }

        val newReadings = ArrayList<GlucoseReadingEntity>()
        for (r in incomingReadings) {
            val key = readingKey(r)
            if (existingReadingKeys.add(key)) {
                newReadings.add(r.copy(id = 0))
            }
        }

        val existingEntryKeys = HashSet<String>(existingEntries.size * 2)
        for (e in existingEntries) {
            existingEntryKeys.add(entryKey(e))
        }

        val newEntries = ArrayList<DiaryEntryEntity>()
        for (e in incomingEntries) {
            val key = entryKey(e)
            if (existingEntryKeys.add(key)) {
                newEntries.add(e.copy(id = 0))
            }
        }

        return MergeDiffResult(newReadings = newReadings, newEntries = newEntries)
    }
}
