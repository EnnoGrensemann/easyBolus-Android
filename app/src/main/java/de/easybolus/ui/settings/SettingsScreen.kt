package de.easybolus.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import de.easybolus.R
import de.easybolus.data.settings.CarbFactorMode
import de.easybolus.data.settings.GlucoseUnit
import de.easybolus.data.settings.InsulinType
import de.easybolus.data.settings.ThemeMode
import de.easybolus.data.settings.UserSettings
import de.easybolus.domain.BackupManager
import de.easybolus.domain.CarbFactorFormatter
import de.easybolus.domain.GlucoseFormatter
import de.easybolus.ui.theme.BgInRangeGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: UserSettings,
    hasJugglucoData: Boolean = false,
    onSaveSettings: (UserSettings) -> Unit,
    onInsertDemoData: () -> Unit,
    onClearDemoData: () -> Unit,
    onExportFullCsv: () -> Unit,
    onRequestFullBackupJson: ((String) -> Unit) -> Unit,
    onRestoreFullBackupJson: (String, (Boolean, Int, Int) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val ieUnit = stringResource(R.string.unit_ie)

    var glucoseUnit by remember(settings) { mutableStateOf(settings.glucoseUnit) }
    var themeMode by remember(settings) { mutableStateOf(settings.themeMode) }
    var jugglucoEnabled by remember(settings) { mutableStateOf(settings.jugglucoEnabled) }

    var carbFactorMode by remember(settings) { mutableStateOf(settings.carbFactorMode) }
    var carbRatio by remember(settings) {
        mutableStateOf(CarbFactorFormatter.formatInput(settings.carbRatio, settings.carbFactorMode))
    }
    var useHourlyCarbRatio by remember(settings) { mutableStateOf(settings.useHourlyCarbRatio) }
    var hourlyExpanded by remember { mutableStateOf(false) }
    val hourlyCarbRatios = remember(settings) {
        mutableStateListOf<String>().apply {
            for (h in 0 until 24) {
                val gPerIe = settings.hourlyCarbRatios.getOrNull(h) ?: settings.carbRatio
                add(CarbFactorFormatter.formatInput(gPerIe, settings.carbFactorMode))
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
    var diaHours by remember(settings) { mutableStateOf(formatCleanDecimal(settings.diaHours)) }
    var insulinType by remember(settings) { mutableStateOf(settings.insulinType) }
    var penStep by remember(settings) { mutableStateOf(settings.penStepSize) }
    var defaultBasal by remember(settings) {
        mutableStateOf(if (settings.defaultBasalUnits > 0.0) formatCleanDecimal(settings.defaultBasalUnits) else "")
    }

    var persistentNotificationEnabled by remember(settings) {
        mutableStateOf(settings.persistentNotificationEnabled)
    }
    var correctionAlertEnabled by remember(settings) {
        mutableStateOf(settings.correctionAlertEnabled)
    }
    var correctionMinUnits by remember(settings) {
        mutableStateOf(formatCleanDecimal(settings.correctionAlertMinUnits))
    }
    var correctionWaitMinutes by remember(settings) {
        mutableStateOf(settings.correctionAlertWaitMinutes.toString())
    }

    val unitLabel = GlucoseFormatter.unitLabel(glucoseUnit)
    val bgKeyboardType = if (glucoseUnit == GlucoseUnit.MG_DL) KeyboardType.Number else KeyboardType.Decimal
    val savedToastText = stringResource(R.string.settings_saved_feedback)
    val exportOkText = stringResource(R.string.toast_export_success)
    val importSettingsOkText = stringResource(R.string.toast_import_settings_ok)
    val importFailedText = stringResource(R.string.toast_import_failed)

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun buildCurrentSettings(): UserSettings {
        val baseCarbRatio = CarbFactorFormatter.parseToGramsPerIe(carbRatio, carbFactorMode)
            ?: settings.carbRatio
        val parsedHourly = List(24) { hour ->
            CarbFactorFormatter.parseToGramsPerIe(hourlyCarbRatios[hour], carbFactorMode)
                ?: settings.hourlyCarbRatios.getOrNull(hour)
                ?: baseCarbRatio
        }
        val isfMgDl = GlucoseFormatter.parseFactorToMgDl(isf, glucoseUnit) ?: settings.isf
        val targetMgDl = GlucoseFormatter.parseToMgDl(targetBg, glucoseUnit) ?: settings.targetBg
        val minMgDl = GlucoseFormatter.parseToMgDl(rangeMin, glucoseUnit) ?: settings.targetRangeMin
        val maxMgDl = GlucoseFormatter.parseToMgDl(rangeMax, glucoseUnit) ?: settings.targetRangeMax
        val parsedBasal = defaultBasal.trim().replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 200.0) ?: 0.0
        val parsedCorrMin = correctionMinUnits.trim().replace(',', '.').toDoubleOrNull()?.coerceIn(0.5, 20.0)
            ?: settings.correctionAlertMinUnits
        val parsedCorrWait = correctionWaitMinutes.trim().toIntOrNull()?.coerceIn(1, 180)
            ?: settings.correctionAlertWaitMinutes

        return settings.copy(
            carbRatio = baseCarbRatio,
            carbFactorMode = carbFactorMode,
            useHourlyCarbRatio = useHourlyCarbRatio,
            hourlyCarbRatios = parsedHourly,
            isf = isfMgDl,
            targetBg = targetMgDl,
            targetRangeMin = minMgDl,
            targetRangeMax = maxMgDl,
            diaHours = diaHours.replace(',', '.').toDoubleOrNull()?.coerceIn(2.0, 8.0) ?: settings.diaHours,
            insulinType = insulinType,
            penStepSize = penStep,
            defaultBasalUnits = parsedBasal,
            themeMode = themeMode,
            glucoseUnit = glucoseUnit,
            jugglucoEnabled = jugglucoEnabled,
            persistentNotificationEnabled = persistentNotificationEnabled,
            correctionAlertEnabled = correctionAlertEnabled,
            correctionAlertMinUnits = parsedCorrMin,
            correctionAlertWaitMinutes = parsedCorrWait
        )
    }

    fun writeTextToUri(uri: Uri, content: String): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Cannot open output stream")
            true
        }.getOrDefault(false)
    }

    fun readTextFromUri(uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }
        }.getOrNull()
    }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = BackupManager.serializeSettings(buildCurrentSettings())
            if (writeTextToUri(uri, json)) {
                Toast.makeText(context, exportOkText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = readTextFromUri(uri)
            val imported = text?.let { BackupManager.deserializeSettings(it) }
            if (imported != null) {
                onSaveSettings(imported)
                Toast.makeText(context, importSettingsOkText, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, importFailedText, Toast.LENGTH_LONG).show()
            }
        }
    }

    val exportFullJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onRequestFullBackupJson { json ->
                if (writeTextToUri(uri, json)) {
                    Toast.makeText(context, exportOkText, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importFullJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = readTextFromUri(uri)
            if (text != null) {
                onRestoreFullBackupJson(text) { success, readingsCount, entriesCount ->
                    val msg = if (success) {
                        context.getString(R.string.toast_import_backup_ok, readingsCount, entriesCount)
                    } else {
                        importFailedText
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, importFailedText, Toast.LENGTH_LONG).show()
            }
        }
    }

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

    fun switchCarbFactorMode(newMode: CarbFactorMode) {
        if (newMode == carbFactorMode) return
        val curBaseGPerIe = CarbFactorFormatter.parseToGramsPerIe(carbRatio, carbFactorMode)
            ?: settings.carbRatio
        val curHourlyGPerIe = List(24) { h ->
            CarbFactorFormatter.parseToGramsPerIe(hourlyCarbRatios[h], carbFactorMode)
                ?: settings.hourlyCarbRatios.getOrNull(h)
                ?: curBaseGPerIe
        }
        carbFactorMode = newMode
        carbRatio = CarbFactorFormatter.formatInput(curBaseGPerIe, newMode)
        for (h in 0 until 24) {
            hourlyCarbRatios[h] = CarbFactorFormatter.formatInput(curHourlyGPerIe[h], newMode)
        }
    }

    val carbRatioLabel = when (carbFactorMode) {
        CarbFactorMode.GRAMS_PER_IE -> stringResource(R.string.settings_carb_ratio_g_per_ie, ieUnit)
        CarbFactorMode.IE_PER_10G -> stringResource(R.string.settings_carb_ratio_ie_per_10g, ieUnit)
        CarbFactorMode.IE_PER_BE -> stringResource(R.string.settings_carb_ratio_ie_per_be, ieUnit)
    }

    val dateStamp = remember { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) }

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
            // 1. Darstellung & Einheiten
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

            // 2. Insulin & Pen
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

                    Text(
                        text = stringResource(R.string.settings_carb_factor_mode),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CarbFactorMode.entries.forEach { mode ->
                            val label = when (mode) {
                                CarbFactorMode.GRAMS_PER_IE -> stringResource(R.string.factor_mode_g_per_ie)
                                CarbFactorMode.IE_PER_10G -> stringResource(R.string.factor_mode_ie_per_10g)
                                CarbFactorMode.IE_PER_BE -> stringResource(R.string.factor_mode_ie_per_be)
                            }
                            FilterChip(
                                selected = carbFactorMode == mode,
                                onClick = { switchCarbFactorMode(mode) },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = carbRatio,
                            onValueChange = { carbRatio = it },
                            label = { Text(carbRatioLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = isf,
                            onValueChange = { isf = it },
                            label = {
                                Text(
                                    stringResource(R.string.settings_isf, unitLabel),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = diaHours,
                            onValueChange = { diaHours = it },
                            label = {
                                Text(
                                    stringResource(R.string.settings_dia),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = defaultBasal,
                            onValueChange = { defaultBasal = it },
                            label = {
                                Text(
                                    stringResource(R.string.settings_default_basal, ieUnit),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

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
                                    val fillValue = carbRatio.ifBlank {
                                        CarbFactorFormatter.formatInput(settings.carbRatio, carbFactorMode)
                                    }
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

            // 3. Zielwerte
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
                }
            }

            // 4. Benachrichtigungen & Korrektur-Meldung
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
                        text = stringResource(R.string.settings_notifications_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_persistent_notif),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stringResource(R.string.settings_persistent_notif_sub),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = persistentNotificationEnabled,
                            onCheckedChange = { enabled ->
                                persistentNotificationEnabled = enabled
                                if (enabled) ensureNotificationPermission()
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_correction_alert),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stringResource(R.string.settings_correction_alert_sub),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = correctionAlertEnabled,
                            onCheckedChange = { enabled ->
                                correctionAlertEnabled = enabled
                                if (enabled) ensureNotificationPermission()
                            }
                        )
                    }

                    AnimatedVisibility(visible = correctionAlertEnabled) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = correctionMinUnits,
                                onValueChange = { correctionMinUnits = it },
                                label = {
                                    Text(
                                        stringResource(R.string.settings_correction_min, ieUnit),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = correctionWaitMinutes,
                                onValueChange = { correctionWaitMinutes = it.filter { ch -> ch.isDigit() } },
                                label = {
                                    Text(
                                        stringResource(R.string.settings_correction_wait),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 5. Sensor-Anbindung (Juggluco / xDrip abschaltbar) & Testdaten
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
                            Text(stringResource(R.string.btn_load_demo), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = onClearDemoData, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.btn_clear_demo), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // 6. Backup & Daten-Export
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
                        text = stringResource(R.string.settings_backup_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { exportSettingsLauncher.launch("easyBOLUS_settings_$dateStamp.json") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                stringResource(R.string.btn_export_settings),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { importSettingsLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                stringResource(R.string.btn_import_settings),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onExportFullCsv,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_export_full_csv))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { exportFullJsonLauncher.launch("easyBOLUS_backup_$dateStamp.json") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                stringResource(R.string.btn_export_full_json),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { importFullJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                stringResource(R.string.btn_import_full_json),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
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
                    onSaveSettings(buildCurrentSettings())
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

private fun formatCleanDecimal(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}
