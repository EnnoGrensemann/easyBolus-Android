package de.easybolus.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import de.easybolus.data.settings.ThemeMode
import de.easybolus.data.settings.UserSettings
import de.easybolus.ui.theme.FabTeal
import de.easybolus.ui.theme.HeaderGradientTop
import de.easybolus.ui.theme.HeaderGradientTopDark
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    hasJugglucoData: Boolean = false,
    onBack: () -> Unit,
    onSaveSettings: (UserSettings) -> Unit,
    onInsertDemoData: () -> Unit,
    onClearDemoData: () -> Unit
) {
    var carbRatio by remember(settings) { mutableStateOf(settings.carbRatio.toString()) }
    var useHourlyCarbRatio by remember(settings) { mutableStateOf(settings.useHourlyCarbRatio) }
    val hourlyCarbRatios = remember(settings) {
        mutableStateListOf<String>().apply {
            for (h in 0 until 24) {
                add((settings.hourlyCarbRatios.getOrNull(h) ?: settings.carbRatio).toString())
            }
        }
    }
    var isf by remember(settings) { mutableStateOf(settings.isf.toString()) }
    var targetBg by remember(settings) { mutableStateOf(settings.targetBg.toString()) }
    var rangeMin by remember(settings) { mutableStateOf(settings.targetRangeMin.toString()) }
    var rangeMax by remember(settings) { mutableStateOf(settings.targetRangeMax.toString()) }
    var diaHours by remember(settings) { mutableStateOf(settings.diaHours.toString()) }
    var penStep by remember(settings) { mutableStateOf(settings.penStepSize) }
    var themeMode by remember(settings) { mutableStateOf(settings.themeMode) }

    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen", color = Color.White, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Bolus- & Insulin-Faktoren", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = carbRatio,
                    onValueChange = { carbRatio = it },
                    label = { Text("KH-Faktor (g / 1 IE)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = isf,
                    onValueChange = { isf = it },
                    label = { Text("Korrekturfaktor (mg/dl)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Stündlicher KH-Faktor", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = "Für jede Stunde (00–23 Uhr) einen eigenen KH-Faktor festlegen",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = useHourlyCarbRatio,
                            onCheckedChange = { useHourlyCarbRatio = it }
                        )
                    }

                    if (useHourlyCarbRatio) {
                        OutlinedButton(
                            onClick = {
                                val fillValue = carbRatio.ifBlank { settings.carbRatio.toString() }
                                for (h in 0 until 24) {
                                    hourlyCarbRatios[h] = fillValue
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aktuellen KH-Faktor für alle Stunden übernehmen")
                        }

                        for (row in 0 until 8) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0 until 3) {
                                    val hour = row * 3 + col
                                    OutlinedTextField(
                                        value = hourlyCarbRatios[hour],
                                        onValueChange = { hourlyCarbRatios[hour] = it },
                                        label = { Text(String.format(Locale.GERMANY, "%02d:00", hour)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = targetBg,
                    onValueChange = { targetBg = it },
                    label = { Text("Zielwert (mg/dl)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = diaHours,
                    onValueChange = { diaHours = it },
                    label = { Text("Wirkdauer DIA (Std.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rangeMin,
                    onValueChange = { rangeMin = it },
                    label = { Text("Zielbereich Min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = rangeMax,
                    onValueChange = { rangeMax = it },
                    label = { Text("Zielbereich Max") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Pen-Schrittweite", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = penStep == 0.5,
                    onClick = { penStep = 0.5 },
                    label = { Text("0,5 IE (Halb-Einheiten-Pen)") }
                )
                FilterChip(
                    selected = penStep == 1.0,
                    onClick = { penStep = 1.0 },
                    label = { Text("1,0 IE (Standard-Pen)") }
                )
            }

            Text("Erscheinungsbild", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { themeMode = mode },
                        label = { Text(mode.label) }
                    )
                }
            }

            Button(
                onClick = {
                    val baseCarbRatio = carbRatio.replace(',', '.').toDoubleOrNull() ?: settings.carbRatio
                    val parsedHourly = List(24) { hour ->
                        hourlyCarbRatios[hour].replace(',', '.').toDoubleOrNull()
                            ?: settings.hourlyCarbRatios.getOrNull(hour)
                            ?: baseCarbRatio
                    }
                    onSaveSettings(
                        settings.copy(
                            carbRatio = baseCarbRatio,
                            useHourlyCarbRatio = useHourlyCarbRatio,
                            hourlyCarbRatios = parsedHourly,
                            isf = isf.replace(',', '.').toDoubleOrNull() ?: settings.isf,
                            targetBg = targetBg.toIntOrNull() ?: settings.targetBg,
                            targetRangeMin = rangeMin.toIntOrNull() ?: settings.targetRangeMin,
                            targetRangeMax = rangeMax.toIntOrNull() ?: settings.targetRangeMax,
                            diaHours = diaHours.replace(',', '.').toDoubleOrNull() ?: settings.diaHours,
                            penStepSize = penStep,
                            themeMode = themeMode
                        )
                    )
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = FabTeal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Einstellungen speichern", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Juggluco Broadcast & Testdaten", fontWeight = FontWeight.SemiBold)
                    if (!hasJugglucoData) {
                        Text(
                            text = "Aktiviere in Juggluco unter Einstellungen → Send to xDrip / Glucodata den Broadcast. easyBOLUS empfängt die Werte automatisch im Hintergrund.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onInsertDemoData, modifier = Modifier.weight(1f)) {
                            Text("Demo-Daten laden")
                        }
                        OutlinedButton(onClick = onClearDemoData, modifier = Modifier.weight(1f)) {
                            Text("Demo löschen")
                        }
                    }
                }
            }
        }
    }
}
