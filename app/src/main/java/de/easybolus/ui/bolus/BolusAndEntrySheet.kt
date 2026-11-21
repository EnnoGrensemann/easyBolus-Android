package de.easybolus.ui.bolus

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.easybolus.R
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.data.settings.CarbFactorMode
import de.easybolus.data.settings.GlucoseUnit
import de.easybolus.data.settings.UserSettings
import de.easybolus.domain.BolusCalculator
import de.easybolus.domain.CarbFactorFormatter
import de.easybolus.domain.GlucoseFormatter
import de.easybolus.ui.theme.CobPillBg
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Moderner Pill-Switcher (Bolus-Rechner | Schnell-Eintrag)
            SegmentedPillSwitcher(
                leftLabel = stringResource(R.string.tab_bolus_calc),
                rightLabel = stringResource(R.string.tab_quick_entry),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.fillMaxWidth()
            )

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
                    defaultBasalUnits = settings.defaultBasalUnits,
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
private fun SegmentedPillSwitcher(
    leftLabel: String,
    rightLabel: String,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(leftLabel, rightLabel).forEachIndexed { idx, label ->
                val selected = selectedIndex == idx
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelect(idx) }
                ) {
                    Text(
                        text = label,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SoftFilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp, maxLines = 1) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent
        ),
        textStyle = TextStyle(
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum"
        ),
        modifier = modifier
    )
}

@Composable
private fun CarbQuickAddRow(
    onAddGrams: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        listOf(10, 15, 30).forEach { addGrams ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onAddGrams(addGrams) }
            ) {
                Text(
                    text = "+$addGrams g",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum"
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
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
    val factorDisplay = CarbFactorFormatter.formatDisplay(activeCarbRatio, settings.carbFactorMode, locale)
    val factorUnitSuffix = when (settings.carbFactorMode) {
        CarbFactorMode.GRAMS_PER_IE -> stringResource(R.string.factor_mode_g_per_ie)
        CarbFactorMode.IE_PER_10G -> stringResource(R.string.factor_mode_ie_per_10g)
        CarbFactorMode.IE_PER_BE -> stringResource(R.string.factor_mode_ie_per_be)
    }

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
    var editingDoseInline by remember { mutableStateOf(false) }
    val activeBolus = manualOverride.replace(',', '.').toDoubleOrNull() ?: result.recommendedUnits

    // 1. Berechnungskarte OBEN (bleibt auch bei geöffneter Tastatur immer im Blick!)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (settings.useHourlyCarbRatio) {
                Text(
                    text = String.format(locale, "%02d:00 · %s %s", currentHour, factorDisplay, factorUnitSuffix),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            BreakdownRow(
                label = stringResource(
                    R.string.label_meal_bolus,
                    carbsVal.toInt(),
                    "$factorDisplay $factorUnitSuffix"
                ),
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Integrierter runder − / [Dosis] / + Bereich
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        editingDoseInline = false
                        val next = (activeBolus - settings.penStepSize).coerceAtLeast(0.0)
                        manualOverride = String.format(Locale.US, "%.1f", next)
                    },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "−",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    if (editingDoseInline) {
                        SoftFilledTextField(
                            value = manualOverride,
                            onValueChange = { manualOverride = it },
                            label = stringResource(R.string.label_injected_bolus, ieUnit),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = String.format(locale, "%.1f %s", activeBolus, ieUnit),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = TextStyle(
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { editingDoseInline = true }
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.label_rounded_step,
                                String.format(locale, "%.1f", settings.penStepSize),
                                ieUnit
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    onClick = {
                        editingDoseInline = false
                        val next = activeBolus + settings.penStepSize
                        manualOverride = String.format(Locale.US, "%.1f", next)
                    },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "+",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (result.recommendedCarbsGrams > 0) {
                val isDark = MaterialTheme.colorScheme.background.red < 0.2f
                val textAccent = if (isDark) Color(0xFF9AC9A6) else Color(0xFF3E6347)
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = CobPillBg.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = stringResource(R.string.bolus_eat_carbs_title, result.recommendedCarbsGrams),
                                color = textAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = stringResource(R.string.bolus_eat_carbs_sub),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            onClick = {
                                val updated = (carbsVal + result.recommendedCarbsGrams).roundToInt()
                                carbsInput = updated.toString()
                            },
                            color = CobPillBg,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = stringResource(R.string.bolus_eat_carbs_apply),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Eingabefelder UNTER dem Berechnungsteil (bei aktiver Sensor-Anbindung BZ kompakter, KH größer)
    val bgWeight = if (settings.jugglucoEnabled) 0.36f else 0.5f
    val carbsWeight = if (settings.jugglucoEnabled) 0.64f else 0.5f
    val bgLabel = if (settings.jugglucoEnabled) {
        "${stringResource(R.string.mode_bg)} ($unitLabel)"
    } else {
        stringResource(R.string.label_bg_unit, unitLabel)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SoftFilledTextField(
            value = bgInput,
            onValueChange = { bgInput = it },
            label = bgLabel,
            keyboardType = if (unit == GlucoseUnit.MG_DL) KeyboardType.Number else KeyboardType.Decimal,
            modifier = Modifier.weight(bgWeight)
        )
        SoftFilledTextField(
            value = carbsInput,
            onValueChange = { carbsInput = it },
            label = stringResource(R.string.label_carbs_g),
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(carbsWeight)
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    CarbQuickAddRow(
        onAddGrams = { addGrams ->
            val updated = (carbsVal + addGrams).toInt()
            carbsInput = updated.toString()
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    SoftFilledTextField(
        value = noteInput,
        onValueChange = { noteInput = it },
        label = stringResource(R.string.label_note_optional_hint),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = {
            val finalBolus = manualOverride.replace(',', '.').toDoubleOrNull() ?: result.recommendedUnits
            onSave(finalBolus, carbsVal, noteInput, bgValMgDl)
        },
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
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
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            )
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
    defaultBasalUnits: Double,
    onSave: (type: DiaryEntryType, bolus: Double?, carbs: Double?, basal: Double?, note: String?, timestampMillis: Long) -> Unit,
    onSaveCombo: (bolus: Double, carbs: Double, note: String?, timestampMillis: Long) -> Unit,
    onSaveGlucose: (mgDl: Int, timestampMillis: Long) -> Unit
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
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

    // Tonal Pill-Auswahl für die Eintragsart
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickEntryMode.entries.forEach { mode ->
            val selected = selectedMode == mode
            val modeLabel = when (mode) {
                QuickEntryMode.CARBS_AND_BOLUS -> stringResource(R.string.mode_carbs_and_bolus)
                QuickEntryMode.GLUCOSE -> stringResource(R.string.mode_bg)
                QuickEntryMode.CARBS -> stringResource(R.string.type_carbs)
                QuickEntryMode.BOLUS -> stringResource(R.string.type_bolus)
                QuickEntryMode.BASAL -> stringResource(R.string.type_basal)
                QuickEntryMode.NOTE -> stringResource(R.string.type_note)
            }
            Surface(
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        selectedMode = mode
                        valueInput = if (mode == QuickEntryMode.BASAL && defaultBasalUnits > 0.0) {
                            if (defaultBasalUnits % 1.0 == 0.0) defaultBasalUnits.toInt().toString()
                            else String.format(Locale.US, "%.1f", defaultBasalUnits)
                        } else {
                            ""
                        }
                        carbsInput = ""
                        bolusInput = ""
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = modeLabel,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Uhrzeit-Pill links + Heute/Gestern Segmented Pill rechts
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .clickable {
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
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 11.dp, horizontal = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = stringResource(R.string.label_time_pick_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(locale, "%02d:%02d", selectedHour, selectedMinute),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum"
                    )
                )
            }
        }

        SegmentedPillSwitcher(
            leftLabel = stringResource(R.string.day_today),
            rightLabel = stringResource(R.string.day_yesterday),
            selectedIndex = if (isYesterday) 1 else 0,
            onSelect = { isYesterday = (it == 1) },
            modifier = Modifier.weight(1.15f)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    when (selectedMode) {
        QuickEntryMode.CARBS_AND_BOLUS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftFilledTextField(
                    value = carbsInput,
                    onValueChange = { carbsInput = it },
                    label = stringResource(R.string.label_carbs_g),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                SoftFilledTextField(
                    value = bolusInput,
                    onValueChange = { bolusInput = it },
                    label = stringResource(R.string.label_bolus_unit, ieUnit),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            CarbQuickAddRow(
                onAddGrams = { addGrams ->
                    val current = carbsInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                    carbsInput = (current + addGrams).toInt().toString()
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        QuickEntryMode.GLUCOSE -> {
            SoftFilledTextField(
                value = valueInput,
                onValueChange = { valueInput = it },
                label = stringResource(R.string.label_bg_unit, unitLabel),
                keyboardType = if (glucoseUnit == GlucoseUnit.MG_DL) KeyboardType.Number else KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        QuickEntryMode.CARBS -> {
            SoftFilledTextField(
                value = valueInput,
                onValueChange = { valueInput = it },
                label = stringResource(R.string.label_carbs_g),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            CarbQuickAddRow(
                onAddGrams = { addGrams ->
                    val current = valueInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                    valueInput = (current + addGrams).toInt().toString()
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        QuickEntryMode.NOTE -> Unit
        else -> {
            val label = when (selectedMode) {
                QuickEntryMode.BOLUS -> stringResource(R.string.label_bolus_unit, ieUnit)
                QuickEntryMode.BASAL -> stringResource(R.string.label_basal_unit, ieUnit)
                else -> ""
            }
            SoftFilledTextField(
                value = valueInput,
                onValueChange = { valueInput = it },
                label = label,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (selectedMode != QuickEntryMode.GLUCOSE) {
        SoftFilledTextField(
            value = noteInput,
            onValueChange = { noteInput = it },
            label = stringResource(
                if (selectedMode == QuickEntryMode.NOTE) R.string.label_note_event
                else R.string.label_note_optional
            ),
            singleLine = selectedMode != QuickEntryMode.NOTE,
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
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(stringResource(R.string.btn_save_entry), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
