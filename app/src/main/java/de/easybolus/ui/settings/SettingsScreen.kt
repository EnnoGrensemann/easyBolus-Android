package de.easybolus.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import de.easybolus.data.settings.GlucoseUnit
import de.easybolus.data.settings.InsulinType
import de.easybolus.data.settings.ThemeMode
import de.easybolus.data.settings.UserSettings
import de.easybolus.domain.GlucoseFormatter
import de.easybolus.ui.theme.BgInRangeGreen
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: UserSettings,
    hasJugglucoData: Boolean = false,
    onSaveSettings: (UserSettings) -> Unit,
    onInsertDemoData: () -> Unit,
    onClearDemoData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val ieUnit = stringResource(R.string.unit_ie)

    var glucoseUnit by remember(settings) { mutableStateOf(settings.glucoseUnit) }
    var themeMode by remember(settings) { mutableStateOf(settings.themeMode) }
    var jugglucoEnabled by remember(settings) { mutableStateOf(settings.jugglucoEnabled) }

    var carbRatio by remember(settings) { mutableStateOf(settings.carbRatio.toString()) }
    var useHourlyCarbRatio by remember(settings) { mutableStateOf(settings.useHourlyCarbRatio) }
    var hourlyExpanded by remember { mutableStateOf(false) }
    val hourlyCarbRatios = remember(settings) {
        mutableStateListOf<String>().apply {
            for (h in 0 until 24) {
                add((settings.hourlyCarbRatios.getOrNull(h) ?: settings.carbRatio).toString())
            }
        }
    }

    var isf by remember(settings) {
        mutableStateOf(GlucoseFormatter.formatFactorInput(settings.isf, settings.glucoseUnit))
    }
    var targetBg by remember(settings) {
        mutableStateOf(GlucoseFormatter.formatInputValue(settings.targetBg, settings.glucoseUnit))
    }
    var rangeMin by remember(settings) {
        mutableStateOf(GlucoseFormatter.formatInputValue(settings.targetRangeMin, settings.glucoseUnit))
    }
    var rangeMax by remember(settings) {
        mutableStateOf(GlucoseFormatter.formatInputValue(settings.targetRangeMax, settings.glucoseUnit))
    }
    var diaHours by remember(settings) { mutableStateOf(settings.diaHours.toString()) }
    var insulinType by remember(settings) { mutableStateOf(settings.insulinType) }
    var penStep by remember(settings) { mutableStateOf(settings.penStepSize) }

    val unitLabel = GlucoseFormatter.unitLabel(glucoseUnit)
    val bgKeyboardType = if (glucoseUnit == GlucoseUnit.MG_DL) KeyboardType.Number else KeyboardType.Decimal
    val savedToastText = stringResource(R.string.settings_saved_feedback)

    fun switchDisplayUnit(newUnit: GlucoseUnit) {
        if (newUnit == glucoseUnit) return
        val curIsfMgDl = GlucoseFormatter.parseFactorToMgDl(isf, glucoseUnit) ?: settings.isf
        val curTargetMgDl = GlucoseFormatter.parseToMgDl(targetBg, glucoseUnit) ?: settings.targetBg
        val curMinMgDl = GlucoseFormatter.parseToMgDl(rangeMin, glucoseUnit) ?: settings.targetRangeMin
        val curMaxMgDl = GlucoseFormatter.parseToMgDl(rangeMax, glucoseUnit) ?: settings.targetRangeMax
        glucoseUnit = newUnit
        isf = GlucoseFormatter.formatFactorInput(curIsfMgDl, newUnit)
        targetBg = GlucoseFormatter.formatInputValue(curTargetMgDl, newUnit)
        rangeMin = GlucoseFormatter.formatInputValue(curMinMgDl, newUnit)
        rangeMax = GlucoseFormatter.formatInputValue(curMaxMgDl, newUnit)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Darstellung & Einheiten (ganz oben)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_appearance),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val modeLabel = when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            }
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { themeMode = mode },
                                label = { Text(modeLabel) }
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.settings_bg_unit),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlucoseUnit.entries.forEach { u ->
                            FilterChip(
                                selected = glucoseUnit == u,
                                onClick = { switchDisplayUnit(u) },
                                label = { Text(GlucoseFormatter.unitLabel(u)) }
                            )
                        }
                    }
                }
            }

            // 2. Bolus- & Insulin-Faktoren
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_factors_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = carbRatio,
                            onValueChange = { carbRatio = it },
                            label = { Text(stringResource(R.string.settings_carb_ratio, ieUnit)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = isf,
                            onValueChange = { isf = it },
                            label = { Text(stringResource(R.string.settings_isf, unitLabel)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = diaHours,
                        onValueChange = { diaHours = it },
                        label = { Text(stringResource(R.string.settings_dia)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.settings_insulin_profile),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InsulinType.entries.forEach { type ->
                            val typeLabel = when (type) {
                                InsulinType.RAPID -> stringResource(R.string.insulin_rapid_short)
                                InsulinType.ULTRA_RAPID -> stringResource(R.string.insulin_ultra_rapid_short)
                            }
                            FilterChip(
                                selected = insulinType == type,
                                onClick = { insulinType = type },
                                label = { Text(typeLabel) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_hourly_carb_ratio),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = stringResource(R.string.settings_hourly_carb_ratio_sub),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = useHourlyCarbRatio,
                            onCheckedChange = {
                                useHourlyCarbRatio = it
                                if (it) hourlyExpanded = true
                            }
                        )
                    }

                    if (useHourlyCarbRatio) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { hourlyExpanded = !hourlyExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    if (hourlyExpanded) R.string.settings_hourly_collapse
                                    else R.string.settings_hourly_expand
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Icon(
                                imageVector = if (hourlyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    AnimatedVisibility(visible = useHourlyCarbRatio && hourlyExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    val fillValue = carbRatio.ifBlank { settings.carbRatio.toString() }
                                    for (h in 0 until 24) {
                                        hourlyCarbRatios[h] = fillValue
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_hourly_copy_base, carbRatio))
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
                                            label = { Text(String.format(locale, "%02d:00", hour)) },
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
            }

            // 3. Zielbereich & Pen
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_target_pen_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    OutlinedTextField(
                        value = targetBg,
                        onValueChange = { targetBg = it },
                        label = { Text(stringResource(R.string.settings_target_bg, unitLabel)) },
                        keyboardOptions = KeyboardOptions(keyboardType = bgKeyboardType),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = rangeMin,
                            onValueChange = { rangeMin = it },
                            label = { Text(stringResource(R.string.settings_range_min, unitLabel)) },
                            keyboardOptions = KeyboardOptions(keyboardType = bgKeyboardType),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = rangeMax,
                            onValueChange = { rangeMax = it },
                            label = { Text(stringResource(R.string.settings_range_max, unitLabel)) },
                            keyboardOptions = KeyboardOptions(keyboardType = bgKeyboardType),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_pen_step),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = penStep == 0.5,
                            onClick = { penStep = 0.5 },
                            label = { Text(stringResource(R.string.settings_pen_half)) }
                        )
                        FilterChip(
                            selected = penStep == 1.0,
                            onClick = { penStep = 1.0 },
                            label = { Text(stringResource(R.string.settings_pen_full)) }
                        )
                    }
                }
            }

            // 4. Sensor-Anbindung (Juggluco / xDrip abschaltbar) & Testdaten
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_sensor_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_juggluco_toggle),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stringResource(R.string.settings_juggluco_toggle_sub),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = jugglucoEnabled,
                            onCheckedChange = { jugglucoEnabled = it }
                        )
                    }

                    if (!jugglucoEnabled) {
                        Text(
                            text = stringResource(R.string.settings_manual_active),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (hasJugglucoData) {
                        Text(
                            text = stringResource(R.string.settings_juggluco_active),
                            color = BgInRangeGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_juggluco_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onInsertDemoData, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.btn_load_demo))
                        }
                        OutlinedButton(onClick = onClearDemoData, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.btn_clear_demo))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Fixierte Speichern-Leiste direkt oberhalb der Navigationsleiste
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Button(
                onClick = {
                    val baseCarbRatio = carbRatio.replace(',', '.').toDoubleOrNull() ?: settings.carbRatio
                    val parsedHourly = List(24) { hour ->
                        hourlyCarbRatios[hour].replace(',', '.').toDoubleOrNull()
                            ?: settings.hourlyCarbRatios.getOrNull(hour)
                            ?: baseCarbRatio
                    }
                    val isfMgDl = GlucoseFormatter.parseFactorToMgDl(isf, glucoseUnit) ?: settings.isf
                    val targetMgDl = GlucoseFormatter.parseToMgDl(targetBg, glucoseUnit) ?: settings.targetBg
                    val minMgDl = GlucoseFormatter.parseToMgDl(rangeMin, glucoseUnit) ?: settings.targetRangeMin
                    val maxMgDl = GlucoseFormatter.parseToMgDl(rangeMax, glucoseUnit) ?: settings.targetRangeMax

                    onSaveSettings(
                        settings.copy(
                            carbRatio = baseCarbRatio,
                            useHourlyCarbRatio = useHourlyCarbRatio,
                            hourlyCarbRatios = parsedHourly,
                            isf = isfMgDl,
                            targetBg = targetMgDl,
                            targetRangeMin = minMgDl,
                            targetRangeMax = maxMgDl,
                            diaHours = diaHours.replace(',', '.').toDoubleOrNull() ?: settings.diaHours,
                            insulinType = insulinType,
                            penStepSize = penStep,
                            themeMode = themeMode,
                            glucoseUnit = glucoseUnit,
                            jugglucoEnabled = jugglucoEnabled
                        )
                    )
                    Toast.makeText(context, savedToastText, Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .height(46.dp)
            ) {
                Text(stringResource(R.string.btn_save_settings), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
