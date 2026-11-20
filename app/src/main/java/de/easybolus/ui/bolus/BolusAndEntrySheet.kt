package de.easybolus.ui.bolus

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.easybolus.R
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.data.settings.GlucoseUnit
import de.easybolus.data.settings.UserSettings
import de.easybolus.domain.BolusCalculator
import de.easybolus.domain.GlucoseFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BolusAndEntrySheet(
    currentBgMgDl: Int?,
    currentIob: Double,
    settings: UserSettings,
    onDismiss: () -> Unit,
    onSaveBolusAndCarbs: (bolusUnits: Double, carbsGrams: Double, note: String?, bgAtTime: Int?, timestampMillis: Long) -> Unit,
    onAddQuickEntry: (type: DiaryEntryType, bolus: Double?, carbs: Double?, basal: Double?, note: String?, timestampMillis: Long) -> Unit,
    onSaveManualGlucose: (mgDl: Int, timestampMillis: Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_bolus_calc)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_quick_entry)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                BolusCalculatorTab(
                    initialBgMgDl = currentBgMgDl ?: settings.targetBg,
                    currentIob = currentIob,
                    settings = settings,
                    onSave = { bolus, carbs, note, bg ->
                        onSaveBolusAndCarbs(bolus, carbs, note, bg, System.currentTimeMillis())
                        onDismiss()
                    }
                )
            } else {
                QuickEntryTab(
                    glucoseUnit = settings.glucoseUnit,
                    onSave = { type, bolus, carbs, basal, note, timestampMillis ->
                        onAddQuickEntry(type, bolus, carbs, basal, note, timestampMillis)
                        onDismiss()
                    },
                    onSaveCombo = { bolus, carbs, note, timestampMillis ->
                        onSaveBolusAndCarbs(bolus, carbs, note, null, timestampMillis)
                        onDismiss()
                    },
                    onSaveGlucose = { mgDl, timestampMillis ->
                        onSaveManualGlucose(mgDl, timestampMillis)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun BolusCalculatorTab(
    initialBgMgDl: Int,
    currentIob: Double,
    settings: UserSettings,
    onSave: (bolusUnits: Double, carbsGrams: Double, note: String?, bgAtTime: Int?) -> Unit
) {
    val locale = Locale.getDefault()
    val unit = settings.glucoseUnit
    val unitLabel = GlucoseFormatter.unitLabel(unit)
    val ieUnit = stringResource(R.string.unit_ie)

    var bgInput by remember { mutableStateOf(GlucoseFormatter.formatInputValue(initialBgMgDl, unit)) }
    var carbsInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    val bgValMgDl = GlucoseFormatter.parseToMgDl(bgInput, unit) ?: initialBgMgDl
    val carbsVal = carbsInput.replace(',', '.').toDoubleOrNull() ?: 0.0
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val activeCarbRatio = settings.carbRatioForHour(currentHour)

    val result = BolusCalculator.calculate(
        currentBgMgDl = bgValMgDl,
        carbsGrams = carbsVal,
        activeIob = currentIob,
        settings = settings,
        hourOfDay = currentHour
    )

    var manualOverride by remember(result.recommendedUnits) {
        mutableStateOf(String.format(Locale.US, "%.1f", result.recommendedUnits))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = bgInput,
            onValueChange = { bgInput = it },
            label = { Text(stringResource(R.string.label_bg_unit, unitLabel)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (unit == GlucoseUnit.MG_DL) KeyboardType.Number else KeyboardType.Decimal
            ),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = carbsInput,
            onValueChange = { carbsInput = it },
            label = { Text(stringResource(R.string.label_carbs_g)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // KH-Schnelltasten
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(10, 15, 30).forEach { addGrams ->
            FilterChip(
                selected = false,
                onClick = {
                    val updated = (carbsVal + addGrams).toInt()
                    carbsInput = updated.toString()
                },
                label = { Text("+$addGrams g", fontSize = 12.sp) }
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Berechnungskarte
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (settings.useHourlyCarbRatio) {
                Text(
                    text = stringResource(
                        R.string.label_hourly_active,
                        currentHour,
                        String.format(locale, "%.1f", activeCarbRatio),
                        ieUnit
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            BreakdownRow(
                label = stringResource(R.string.label_meal_bolus, carbsVal.toInt(), String.format(locale, "%.1f", activeCarbRatio)),
                value = result.mealBolus,
                ieUnit = ieUnit
            )
            BreakdownRow(
                label = stringResource(
                    R.string.label_correction,
                    GlucoseFormatter.formatValue(bgValMgDl, unit, locale),
                    GlucoseFormatter.formatValue(settings.targetBg, unit, locale),
                    unitLabel
                ),
                value = result.correctionBolus,
                ieUnit = ieUnit
            )
            BreakdownRow(
                label = stringResource(R.string.label_active_iob),
                value = -result.activeIob,
                ieUnit = ieUnit
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.label_pen_recommendation),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Text(
                        text = stringResource(
                            R.string.label_rounded_step,
                            String.format(locale, "%.1f", settings.penStepSize),
                            ieUnit
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = String.format(locale, "%.1f %s", result.recommendedUnits, ieUnit),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Bolus-Eingabe mit - / + Stepper in Pen-Schrittweite
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = {
                val cur = manualOverride.replace(',', '.').toDoubleOrNull() ?: result.recommendedUnits
                val next = (cur - settings.penStepSize).coerceAtLeast(0.0)
                manualOverride = String.format(Locale.US, "%.1f", next)
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = manualOverride,
            onValueChange = { manualOverride = it },
            label = { Text(stringResource(R.string.label_injected_bolus, ieUnit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = {
                val cur = manualOverride.replace(',', '.').toDoubleOrNull() ?: result.recommendedUnits
                val next = cur + settings.penStepSize
                manualOverride = String.format(Locale.US, "%.1f", next)
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
        value = noteInput,
        onValueChange = { noteInput = it },
        label = { Text(stringResource(R.string.label_note_optional_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = {
            val finalBolus = manualOverride.replace(',', '.').toDoubleOrNull() ?: result.recommendedUnits
            onSave(finalBolus, carbsVal, noteInput, bgValMgDl)
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(stringResource(R.string.btn_save_and_log), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BreakdownRow(label: String, value: Double, ieUnit: String) {
    val locale = Locale.getDefault()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        val sign = if (value > 0) "+" else ""
        Text(
            text = String.format(locale, "%s%.2f %s", sign, value, ieUnit),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private enum class QuickEntryMode {
    CARBS_AND_BOLUS,
    GLUCOSE,
    CARBS,
    BOLUS,
    BASAL,
    NOTE
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickEntryTab(
    glucoseUnit: GlucoseUnit,
    onSave: (type: DiaryEntryType, bolus: Double?, carbs: Double?, basal: Double?, note: String?, timestampMillis: Long) -> Unit,
    onSaveCombo: (bolus: Double, carbs: Double, note: String?, timestampMillis: Long) -> Unit,
    onSaveGlucose: (mgDl: Int, timestampMillis: Long) -> Unit
) {
    val context = LocalContext.current
    val unitLabel = GlucoseFormatter.unitLabel(glucoseUnit)
    val ieUnit = stringResource(R.string.unit_ie)

    val initialCal = remember { Calendar.getInstance() }
    var selectedHour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }
    var isYesterday by remember { mutableStateOf(false) }

    var selectedMode by remember { mutableStateOf(QuickEntryMode.CARBS_AND_BOLUS) }
    var valueInput by remember { mutableStateOf("") }
    var carbsInput by remember { mutableStateOf("") }
    var bolusInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    fun buildTimestampMillis(): Long {
        val cal = Calendar.getInstance().apply {
            if (isYesterday) add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        QuickEntryMode.entries.forEach { mode ->
            val modeLabel = when (mode) {
                QuickEntryMode.CARBS_AND_BOLUS -> stringResource(R.string.mode_carbs_and_bolus)
                QuickEntryMode.GLUCOSE -> stringResource(R.string.mode_bg)
                QuickEntryMode.CARBS -> stringResource(R.string.type_carbs)
                QuickEntryMode.BOLUS -> stringResource(R.string.type_bolus)
                QuickEntryMode.BASAL -> stringResource(R.string.type_basal)
                QuickEntryMode.NOTE -> stringResource(R.string.type_note)
            }
            FilterChip(
                selected = selectedMode == mode,
                onClick = {
                    selectedMode = mode
                    valueInput = ""
                    carbsInput = ""
                    bolusInput = ""
                },
                label = { Text(modeLabel, fontSize = 12.sp) }
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Uhrzeit & Tag (Heute / Gestern)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        selectedHour = hour
                        selectedMinute = minute
                    },
                    selectedHour,
                    selectedMinute,
                    true
                ).show()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = stringResource(R.string.label_time_pick_cd),
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(stringResource(R.string.label_time_pick, selectedHour, selectedMinute))
        }

        FilterChip(
            selected = isYesterday,
            onClick = { isYesterday = !isYesterday },
            label = {
                Text(stringResource(if (isYesterday) R.string.day_yesterday else R.string.day_today))
            }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    when (selectedMode) {
        QuickEntryMode.CARBS_AND_BOLUS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = carbsInput,
                    onValueChange = { carbsInput = it },
                    label = { Text(stringResource(R.string.label_carbs_g)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = bolusInput,
                    onValueChange = { bolusInput = it },
                    label = { Text(stringResource(R.string.label_bolus_unit, ieUnit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        QuickEntryMode.GLUCOSE -> {
            OutlinedTextField(
                value = valueInput,
                onValueChange = { valueInput = it },
                label = { Text(stringResource(R.string.label_bg_unit, unitLabel)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (glucoseUnit == GlucoseUnit.MG_DL) KeyboardType.Number else KeyboardType.Decimal
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        QuickEntryMode.NOTE -> Unit
        else -> {
            val label = when (selectedMode) {
                QuickEntryMode.BOLUS -> stringResource(R.string.label_bolus_unit, ieUnit)
                QuickEntryMode.CARBS -> stringResource(R.string.label_carbs_g)
                QuickEntryMode.BASAL -> stringResource(R.string.label_basal_unit, ieUnit)
                else -> ""
            }
            OutlinedTextField(
                value = valueInput,
                onValueChange = { valueInput = it },
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (selectedMode != QuickEntryMode.GLUCOSE) {
        OutlinedTextField(
            value = noteInput,
            onValueChange = { noteInput = it },
            label = {
                Text(
                    stringResource(
                        if (selectedMode == QuickEntryMode.NOTE) R.string.label_note_event
                        else R.string.label_note_optional
                    )
                )
            },
            singleLine = false,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = {
            val ts = buildTimestampMillis()
            when (selectedMode) {
                QuickEntryMode.CARBS_AND_BOLUS -> {
                    val carbs = carbsInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val bolus = bolusInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (carbs > 0.0 || bolus > 0.0) {
                        onSaveCombo(bolus, carbs, noteInput, ts)
                    }
                }
                QuickEntryMode.GLUCOSE -> {
                    val mgDl = GlucoseFormatter.parseToMgDl(valueInput, glucoseUnit)
                    if (mgDl != null) {
                        onSaveGlucose(mgDl, ts)
                    }
                }
                QuickEntryMode.BOLUS -> {
                    val num = valueInput.replace(',', '.').toDoubleOrNull()
                    if ((num ?: 0.0) > 0.0) onSave(DiaryEntryType.BOLUS, num, null, null, noteInput, ts)
                }
                QuickEntryMode.CARBS -> {
                    val num = valueInput.replace(',', '.').toDoubleOrNull()
                    if ((num ?: 0.0) > 0.0) onSave(DiaryEntryType.CARBS, null, num, null, noteInput, ts)
                }
                QuickEntryMode.BASAL -> {
                    val num = valueInput.replace(',', '.').toDoubleOrNull()
                    if ((num ?: 0.0) > 0.0) onSave(DiaryEntryType.BASAL, null, null, num, noteInput, ts)
                }
                QuickEntryMode.NOTE -> {
                    if (noteInput.isNotBlank()) onSave(DiaryEntryType.NOTE, null, null, null, noteInput, ts)
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(stringResource(R.string.btn_save_entry), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
