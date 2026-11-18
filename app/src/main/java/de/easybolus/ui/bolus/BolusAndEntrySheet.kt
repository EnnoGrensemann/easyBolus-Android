package de.easybolus.ui.bolus

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.data.settings.UserSettings
import de.easybolus.domain.BolusCalculator
import de.easybolus.ui.theme.FabTeal
import de.easybolus.ui.theme.HeaderGradientBottom
import de.easybolus.ui.theme.TextPrimary
import de.easybolus.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BolusAndEntrySheet(
    currentBgMgDl: Int?,
    currentIob: Double,
    settings: UserSettings,
    onDismiss: () -> Unit,
    onSaveBolusAndCarbs: (bolusUnits: Double, carbsGrams: Double, note: String?, bgAtTime: Int?) -> Unit,
    onAddQuickEntry: (type: DiaryEntryType, bolus: Double?, carbs: Double?, basal: Double?, note: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
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
                    text = { Text("Bolus-Rechner") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Schnell-Eintrag") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                BolusCalculatorTab(
                    initialBg = currentBgMgDl ?: settings.targetBg,
                    currentIob = currentIob,
                    settings = settings,
                    onSave = { bolus, carbs, note, bg ->
                        onSaveBolusAndCarbs(bolus, carbs, note, bg)
                        onDismiss()
                    }
                )
            } else {
                QuickEntryTab(
                    onSave = { type, bolus, carbs, basal, note ->
                        onAddQuickEntry(type, bolus, carbs, basal, note)
                        onDismiss()
                    },
                    onSaveCombo = { bolus, carbs, note ->
                        onSaveBolusAndCarbs(bolus, carbs, note, null)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun BolusCalculatorTab(
    initialBg: Int,
    currentIob: Double,
    settings: UserSettings,
    onSave: (bolusUnits: Double, carbsGrams: Double, note: String?, bgAtTime: Int?) -> Unit
) {
    var bgInput by remember { mutableStateOf(initialBg.toString()) }
    var carbsInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    val bgVal = bgInput.toIntOrNull() ?: initialBg
    val carbsVal = carbsInput.replace(',', '.').toDoubleOrNull() ?: 0.0
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val activeCarbRatio = settings.carbRatioForHour(currentHour)

    val result = BolusCalculator.calculate(
        currentBgMgDl = bgVal,
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
            onValueChange = { bgInput = it.filter { ch -> ch.isDigit() } },
            label = { Text("Blutzucker (mg/dl)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = carbsInput,
            onValueChange = { carbsInput = it },
            label = { Text("Kohlenhydrate (g)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Berechnungskarte
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F8FA)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            BreakdownRow("Mahlzeiten-Bolus (${carbsVal.toInt()} g / $activeCarbRatio)", result.mealBolus)
            BreakdownRow("Korrektur (${bgVal} → ${settings.targetBg} mg/dl)", result.correctionBolus)
            BreakdownRow("Aktives Insulin (IOB)", -result.activeIob)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pen-Empfehlung",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Gerundet auf ${settings.penStepSize} IE",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = String.format(Locale.GERMANY, "%.1f IE", result.recommendedUnits),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeaderGradientBottom
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = manualOverride,
        onValueChange = { manualOverride = it },
        label = { Text("Tatsächlich gespritzter Bolus (IE)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
        value = noteInput,
        onValueChange = { noteInput = it },
        label = { Text("Notiz (optional, z.B. Pizza, Sport)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = {
            val finalBolus = manualOverride.replace(',', '.').toDoubleOrNull() ?: result.recommendedUnits
            onSave(finalBolus, carbsVal, noteInput, bgVal)
        },
        colors = ButtonDefaults.buttonColors(containerColor = FabTeal),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text("Speichern & Dokumentieren", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BreakdownRow(label: String, value: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        val sign = if (value > 0) "+" else ""
        Text(
            text = String.format(Locale.GERMANY, "%s%.2f IE", sign, value),
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private enum class QuickEntryMode(val label: String) {
    CARBS_AND_BOLUS("KH + IE"),
    CARBS(DiaryEntryType.CARBS.label),
    BOLUS(DiaryEntryType.BOLUS.label),
    BASAL(DiaryEntryType.BASAL.label),
    NOTE(DiaryEntryType.NOTE.label)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickEntryTab(
    onSave: (type: DiaryEntryType, bolus: Double?, carbs: Double?, basal: Double?, note: String?) -> Unit,
    onSaveCombo: (bolus: Double, carbs: Double, note: String?) -> Unit
) {
    var selectedMode by remember { mutableStateOf(QuickEntryMode.CARBS_AND_BOLUS) }
    var valueInput by remember { mutableStateOf("") }
    var carbsInput by remember { mutableStateOf("") }
    var bolusInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        QuickEntryMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = {
                    selectedMode = mode
                    valueInput = ""
                    carbsInput = ""
                    bolusInput = ""
                },
                label = { Text(mode.label, fontSize = 12.sp) }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    when (selectedMode) {
        QuickEntryMode.CARBS_AND_BOLUS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = carbsInput,
                    onValueChange = { carbsInput = it },
                    label = { Text("Kohlenhydrate (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = bolusInput,
                    onValueChange = { bolusInput = it },
                    label = { Text("Bolus (IE)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        QuickEntryMode.NOTE -> Unit
        else -> {
            val label = when (selectedMode) {
                QuickEntryMode.BOLUS -> "Bolus (IE)"
                QuickEntryMode.CARBS -> "Kohlenhydrate (g)"
                QuickEntryMode.BASAL -> "Basalinsulin (IE)"
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

    OutlinedTextField(
        value = noteInput,
        onValueChange = { noteInput = it },
        label = { Text(if (selectedMode == QuickEntryMode.NOTE) "Notiz / Sport / Ereignis" else "Notiz (optional)") },
        singleLine = false,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = {
            if (selectedMode == QuickEntryMode.CARBS_AND_BOLUS) {
                val carbs = carbsInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                val bolus = bolusInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                if (carbs > 0.0 || bolus > 0.0) {
                    onSaveCombo(bolus, carbs, noteInput)
                }
            } else {
                val num = valueInput.replace(',', '.').toDoubleOrNull()
                when (selectedMode) {
                    QuickEntryMode.BOLUS -> if ((num ?: 0.0) > 0.0) onSave(DiaryEntryType.BOLUS, num, null, null, noteInput)
                    QuickEntryMode.CARBS -> if ((num ?: 0.0) > 0.0) onSave(DiaryEntryType.CARBS, null, num, null, noteInput)
                    QuickEntryMode.BASAL -> if ((num ?: 0.0) > 0.0) onSave(DiaryEntryType.BASAL, null, null, num, noteInput)
                    QuickEntryMode.NOTE -> if (noteInput.isNotBlank()) onSave(DiaryEntryType.NOTE, null, null, null, noteInput)
                    QuickEntryMode.CARBS_AND_BOLUS -> Unit
                }
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = FabTeal),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text("Eintrag speichern", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
