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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.ui.theme.BasalMarkerPurple
import de.easybolus.ui.theme.BolusMarkerBlue
import de.easybolus.ui.theme.CarbsMarkerOrange
import de.easybolus.ui.theme.HeaderGradientTop
import de.easybolus.ui.theme.HeaderGradientTopDark
import de.easybolus.ui.theme.NoteMarkerSlate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    entries: List<DiaryEntryEntity>,
    onExportCsv: () -> Unit,
    onUpdateEntry: (DiaryEntryEntity) -> Unit,
    onDeleteEntry: (DiaryEntryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingEntry by remember { mutableStateOf<DiaryEntryEntity?>(null) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tagebuch",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = onExportCsv, enabled = entries.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "CSV Export",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) HeaderGradientTopDark else HeaderGradientTop
                )
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Noch keine Tagebucheinträge vorhanden.\nTippe auf der Startseite auf +, um einen Bolus oder Mahlzeiten zu erfassen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }
        } else {
            var activeFilter by remember { mutableStateOf<DiaryEntryType?>(null) }
            val filteredEntries = remember(entries, activeFilter) {
                if (activeFilter == null) entries else entries.filter { it.type == activeFilter }
            }
            val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
            val displayDayFormat = remember { SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMANY) }
            val todayKey = remember { dayFormat.format(Date()) }
            val yesterdayKey = remember {
                Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time.let { dayFormat.format(it) }
            }
            val groupedDays = remember(filteredEntries) {
                filteredEntries.groupBy { dayFormat.format(Date(it.timestampMillis)) }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Filter-Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeFilter == null,
                        onClick = { activeFilter = null },
                        label = { Text("Alle") }
                    )
                    DiaryEntryType.entries.forEach { type ->
                        FilterChip(
                            selected = activeFilter == type,
                            onClick = { activeFilter = if (activeFilter == type) null else type },
                            label = { Text(type.label) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    groupedDays.forEach { (dayKey, dayEntries) ->
                        val dayTitle = when (dayKey) {
                            todayKey -> "Heute"
                            yesterdayKey -> "Gestern"
                            else -> displayDayFormat.format(Date(dayEntries.first().timestampMillis))
                        }
                        val totalBolus = dayEntries.sumOf { it.bolusUnits ?: 0.0 }
                        val totalCarbs = dayEntries.sumOf { it.carbsGrams ?: 0.0 }

                        item(key = "header_$dayKey") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dayTitle,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = String.format(Locale.GERMANY, "%.1f IE · %.0f g KH", totalBolus, totalCarbs),
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                            }
                        }

                        itemsIndexed(dayEntries, key = { _, item -> item.id }) { index, entry ->
                            TimelineEntryRow(
                                entry = entry,
                                isFirst = index == 0,
                                isLast = index == dayEntries.lastIndex,
                                onClick = { editingEntry = entry }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = { updated ->
                onUpdateEntry(updated)
                editingEntry = null
            },
            onDelete = {
                onDeleteEntry(entry)
                editingEntry = null
            }
        )
    }
}

@Composable
private fun TimelineEntryRow(
    entry: DiaryEntryEntity,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY) }
    val (icon, color, primaryText) = when (entry.type) {
        DiaryEntryType.BOLUS -> Triple(
            Icons.Default.Vaccines,
            BolusMarkerBlue,
            String.format(Locale.GERMANY, "Bolus: %.1f IE", entry.bolusUnits ?: 0.0)
        )
        DiaryEntryType.CARBS -> Triple(
            Icons.Default.Grain,
            CarbsMarkerOrange,
            String.format(Locale.GERMANY, "Kohlenhydrate: %.0f g", entry.carbsGrams ?: 0.0)
        )
        DiaryEntryType.BASAL -> Triple(
            Icons.Default.Medication,
            BasalMarkerPurple,
            String.format(Locale.GERMANY, "Basal: %.1f IE", entry.basalUnits ?: 0.0)
        )
        DiaryEntryType.NOTE -> Triple(
            Icons.Default.EditNote,
            NoteMarkerSlate,
            entry.note ?: "Notiz"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Linke Timeline-Spalte (Linie + rundes Icon)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(if (isFirst) Color.Transparent else Color(0xFFCBD5E1))
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = entry.type.label,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Rechte Inhaltskarte (mySugr-like)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                        text = timeFormat.format(Date(entry.timestampMillis)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (entry.bgAtTimeMgDl != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val arrow = entry.trendAtTime?.symbol ?: ""
                            Text(
                                text = "${entry.bgAtTimeMgDl} mg/dl $arrow".trim(),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = primaryText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (entry.type != DiaryEntryType.NOTE && !entry.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.note,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EditEntryDialog(
    entry: DiaryEntryEntity,
    onDismiss: () -> Unit,
    onSave: (DiaryEntryEntity) -> Unit,
    onDelete: () -> Unit
) {
    var note by remember { mutableStateOf(entry.note ?: "") }
    var valueText by remember {
        mutableStateOf(
            when (entry.type) {
                DiaryEntryType.BOLUS -> entry.bolusUnits?.toString() ?: ""
                DiaryEntryType.CARBS -> entry.carbsGrams?.toString() ?: ""
                DiaryEntryType.BASAL -> entry.basalUnits?.toString() ?: ""
                DiaryEntryType.NOTE -> ""
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entry.type.label} bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entry.type != DiaryEntryType.NOTE) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text("Wert") },
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notiz") }
                )
            }
        },
        dismissButton = {
            Row {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = Color(0xFFE53935))
                }
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val num = valueText.replace(',', '.').toDoubleOrNull()
                val updated = when (entry.type) {
                    DiaryEntryType.BOLUS -> entry.copy(bolusUnits = num ?: entry.bolusUnits, note = note.ifBlank { null })
                    DiaryEntryType.CARBS -> entry.copy(carbsGrams = num ?: entry.carbsGrams, note = note.ifBlank { null })
                    DiaryEntryType.BASAL -> entry.copy(basalUnits = num ?: entry.basalUnits, note = note.ifBlank { null })
                    DiaryEntryType.NOTE -> entry.copy(note = note.ifBlank { entry.note })
                }
                onSave(updated)
            }) {
                Text("Speichern")
            }
        }
    )
}
