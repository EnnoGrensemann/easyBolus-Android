package de.easybolus.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.easybolus.R
import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.data.local.TrendArrow
import de.easybolus.data.settings.GlucoseUnit
import de.easybolus.domain.GlucoseFormatter
import de.easybolus.ui.theme.BasalMarkerPurple
import de.easybolus.ui.theme.BgLowRed
import de.easybolus.ui.theme.BolusMarkerBlue
import de.easybolus.ui.theme.CarbsMarkerOrange
import de.easybolus.ui.theme.NoteMarkerSlate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private data class DiaryEntryGroup(
    val id: String,
    val timestampMillis: Long,
    val entries: List<DiaryEntryEntity>
)

private fun groupSimultaneousEntries(
    dayEntries: List<DiaryEntryEntity>,
    windowMillis: Long = 120_000L
): List<DiaryEntryGroup> {
    if (dayEntries.isEmpty()) return emptyList()
    val result = mutableListOf<MutableList<DiaryEntryEntity>>()
    for (entry in dayEntries) {
        val lastGroup = result.lastOrNull()
        if (lastGroup != null && abs(lastGroup.first().timestampMillis - entry.timestampMillis) <= windowMillis) {
            lastGroup.add(entry)
        } else {
            result.add(mutableListOf(entry))
        }
    }
    return result.map { grp ->
        DiaryEntryGroup(
            id = grp.joinToString("_") { it.id.toString() },
            timestampMillis = grp.first().timestampMillis,
            entries = grp
        )
    }
}

@Composable
private fun diaryTypeLabel(type: DiaryEntryType): String = when (type) {
    DiaryEntryType.BOLUS -> stringResource(R.string.type_bolus)
    DiaryEntryType.CARBS -> stringResource(R.string.type_carbs)
    DiaryEntryType.BASAL -> stringResource(R.string.type_basal)
    DiaryEntryType.NOTE -> stringResource(R.string.type_note)
}

@Composable
fun DiaryScreen(
    entries: List<DiaryEntryEntity>,
    glucoseUnit: GlucoseUnit,
    onExportCsv: () -> Unit,
    onUpdateEntry: (DiaryEntryEntity) -> Unit,
    onDeleteEntry: (DiaryEntryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val ieUnit = stringResource(R.string.unit_ie)
    val carbsShort = stringResource(R.string.unit_carbs_short)
    var editingGroup by remember { mutableStateOf<DiaryEntryGroup?>(null) }
    var activeFilter by remember { mutableStateOf<DiaryEntryType?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Obere Filter-Zeile + CSV-Export (kein farbiger Header, kein Add-Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeFilter == null,
                    onClick = { activeFilter = null },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                DiaryEntryType.entries.forEach { type ->
                    FilterChip(
                        selected = activeFilter == type,
                        onClick = { activeFilter = if (activeFilter == type) null else type },
                        label = { Text(diaryTypeLabel(type)) }
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onExportCsv,
                enabled = entries.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = stringResource(R.string.diary_export_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.diary_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }
        } else {
            val filteredEntries = remember(entries, activeFilter) {
                if (activeFilter == null) entries else entries.filter { it.type == activeFilter }
            }
            val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
            val displayDayFormat = remember(locale) { SimpleDateFormat("EEEE, dd.MM.yyyy", locale) }
            val todayKey = remember { dayFormat.format(Date()) }
            val yesterdayKey = remember {
                Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time.let { dayFormat.format(it) }
            }
            val groupedDays = remember(filteredEntries) {
                filteredEntries.groupBy { dayFormat.format(Date(it.timestampMillis)) }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                groupedDays.forEach { (dayKey, dayEntries) ->
                    val totalBolus = dayEntries.sumOf { it.bolusUnits ?: 0.0 }
                    val totalCarbs = dayEntries.sumOf { it.carbsGrams ?: 0.0 }
                    val entryGroups = groupSimultaneousEntries(dayEntries)

                    item(key = "header_$dayKey") {
                        val dayTitle = when (dayKey) {
                            todayKey -> stringResource(R.string.day_today)
                            yesterdayKey -> stringResource(R.string.day_yesterday)
                            else -> displayDayFormat.format(Date(dayEntries.first().timestampMillis))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dayTitle,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = String.format(locale, "%.1f %s · %.0f g %s", totalBolus, ieUnit, totalCarbs, carbsShort),
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFeatureSettings = "tnum"
                                )
                            )
                        }
                    }

                    itemsIndexed(entryGroups, key = { _, grp -> grp.id }) { index, group ->
                        TimelineGroupRow(
                            group = group,
                            glucoseUnit = glucoseUnit,
                            isFirst = index == 0,
                            isLast = index == entryGroups.lastIndex,
                            onClick = { editingGroup = group }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }

    editingGroup?.let { group ->
        EditEntryGroupDialog(
            group = group,
            onDismiss = { editingGroup = null },
            onSaveEntries = { updatedList ->
                updatedList.forEach { onUpdateEntry(it) }
                editingGroup = null
            },
            onDeleteEntry = { entryToDelete ->
                onDeleteEntry(entryToDelete)
                val remaining = group.entries.filter { it.id != entryToDelete.id }
                editingGroup = if (remaining.isEmpty()) null else group.copy(entries = remaining)
            }
        )
    }
}

@Composable
private fun TimelineGroupRow(
    group: DiaryEntryGroup,
    glucoseUnit: GlucoseUnit,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val locale = Locale.getDefault()
    val timeFormat = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val ieUnit = stringResource(R.string.unit_ie)
    val unitLabel = GlucoseFormatter.unitLabel(glucoseUnit)

    val totalBolus = group.entries.sumOf { it.bolusUnits ?: 0.0 }
    val totalCarbs = group.entries.sumOf { it.carbsGrams ?: 0.0 }
    val totalBasal = group.entries.sumOf { it.basalUnits ?: 0.0 }
    val notes = group.entries.mapNotNull { it.note?.trim()?.takeIf { n -> n.isNotEmpty() } }.distinct()
    val bgEntry = group.entries.firstOrNull { it.bgAtTimeMgDl != null }

    val primaryParts = mutableListOf<String>()
    if (totalBolus > 0.0) {
        primaryParts.add(
            stringResource(R.string.diary_bolus_fmt, String.format(locale, "%.1f", totalBolus), ieUnit)
        )
    }
    if (totalCarbs > 0.0) {
        primaryParts.add(
            stringResource(R.string.diary_carbs_fmt, String.format(locale, "%.0f", totalCarbs))
        )
    }
    if (totalBasal > 0.0) {
        primaryParts.add(
            stringResource(R.string.diary_basal_fmt, String.format(locale, "%.1f", totalBasal), ieUnit)
        )
    }
    val primaryText = if (primaryParts.isNotEmpty()) {
        primaryParts.joinToString(" · ")
    } else {
        notes.firstOrNull() ?: stringResource(R.string.type_note)
    }
    val subtitleNotes = if (primaryParts.isNotEmpty()) notes else notes.drop(1)

    val hasBolus = totalBolus > 0.0
    val hasCarbs = totalCarbs > 0.0
    val hasBasal = totalBasal > 0.0
    val isCombo = (if (hasBolus) 1 else 0) + (if (hasCarbs) 1 else 0) + (if (hasBasal) 1 else 0) > 1

    val singleColor = when {
        hasBolus -> BolusMarkerBlue
        hasCarbs -> CarbsMarkerOrange
        hasBasal -> BasalMarkerPurple
        else -> NoteMarkerSlate
    }
    val singleIcon = when {
        hasBolus -> Icons.Default.Vaccines
        hasCarbs -> Icons.Default.Grain
        hasBasal -> Icons.Default.Medication
        else -> Icons.Default.EditNote
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Linke Timeline-Spalte
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(42.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(if (isFirst) Color.Transparent else MaterialTheme.colorScheme.outlineVariant)
            )
            if (isCombo) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(BolusMarkerBlue, CarbsMarkerOrange)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vaccines,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Grain,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(singleColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = singleIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Rechte Inhaltskarte
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 10.dp)
                .clickable(onClick = onClick)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormat.format(Date(group.timestampMillis)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum")
                    )
                    if (bgEntry?.bgAtTimeMgDl != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val arrow = bgEntry.trendAtTime?.takeIf { it != TrendArrow.UNKNOWN }?.symbol ?: ""
                            val bgFormatted = GlucoseFormatter.formatValue(bgEntry.bgAtTimeMgDl, glucoseUnit, locale)
                            Text(
                                text = "$bgFormatted $unitLabel $arrow".trim(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFeatureSettings = "tnum"
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = primaryText,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    )
                )

                if (subtitleNotes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitleNotes.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EditEntryGroupDialog(
    group: DiaryEntryGroup,
    onDismiss: () -> Unit,
    onSaveEntries: (List<DiaryEntryEntity>) -> Unit,
    onDeleteEntry: (DiaryEntryEntity) -> Unit
) {
    val locale = Locale.getDefault()
    val timeFormat = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val valueMap = remember(group.entries) {
        mutableStateMapOf<Long, String>().apply {
            group.entries.forEach { entry ->
                put(
                    entry.id,
                    when (entry.type) {
                        DiaryEntryType.BOLUS -> entry.bolusUnits?.toString() ?: ""
                        DiaryEntryType.CARBS -> entry.carbsGrams?.toString() ?: ""
                        DiaryEntryType.BASAL -> entry.basalUnits?.toString() ?: ""
                        DiaryEntryType.NOTE -> ""
                    }
                )
            }
        }
    }
    val noteMap = remember(group.entries) {
        mutableStateMapOf<Long, String>().apply {
            group.entries.forEach { entry ->
                put(entry.id, entry.note ?: "")
            }
        }
    }

    val titleText = if (group.entries.size == 1) {
        stringResource(R.string.diary_edit_title)
    } else {
        stringResource(R.string.diary_edit_group_title, timeFormat.format(Date(group.timestampMillis)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                group.entries.forEachIndexed { idx, entry ->
                    if (idx > 0) {
                        HorizontalDivider()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = diaryTypeLabel(entry.type),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        IconButton(onClick = { onDeleteEntry(entry) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = BgLowRed
                            )
                        }
                    }
                    if (entry.type != DiaryEntryType.NOTE) {
                        OutlinedTextField(
                            value = valueMap[entry.id] ?: "",
                            onValueChange = { valueMap[entry.id] = it },
                            label = { Text(stringResource(R.string.diary_value_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = noteMap[entry.id] ?: "",
                        onValueChange = { noteMap[entry.id] = it },
                        label = { Text(stringResource(R.string.diary_note_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = group.entries.map { entry ->
                    val num = (valueMap[entry.id] ?: "").replace(',', '.').toDoubleOrNull()
                    val note = (noteMap[entry.id] ?: "").trim()
                    when (entry.type) {
                        DiaryEntryType.BOLUS -> entry.copy(bolusUnits = num ?: entry.bolusUnits, note = note.ifBlank { null })
                        DiaryEntryType.CARBS -> entry.copy(carbsGrams = num ?: entry.carbsGrams, note = note.ifBlank { null })
                        DiaryEntryType.BASAL -> entry.copy(basalUnits = num ?: entry.basalUnits, note = note.ifBlank { null })
                        DiaryEntryType.NOTE -> entry.copy(note = note.ifBlank { entry.note })
                    }
                }
                onSaveEntries(updated)
            }) {
                Text(stringResource(R.string.action_save))
            }
        }
    )
}
