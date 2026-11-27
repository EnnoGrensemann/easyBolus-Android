package de.easybolus.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.roundToInt

private enum class SettingsCategory {
    APPEARANCE,
    INSULIN_PEN,
    TARGETS,
    NOTIFICATIONS,
    SENSOR,
    BACKUP
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    hasJugglucoData: Boolean = false,
    onBack: () -> Unit,
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
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    var activeCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    BackHandler {
        if (activeCategory != null) {
            activeCategory = null
        } else {
            onBack()
        }
    }

    val versionName = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.1.0" }
    }

    var glucoseUnit by remember(settings) { mutableStateOf(settings.glucoseUnit) }
    var themeMode by remember(settings) { mutableStateOf(settings.themeMode) }
    var widgetTransparency by remember(settings) { mutableIntStateOf(settings.widgetTransparencyPercent) }
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
    var defaultSplitPart1Percent by remember(settings) {
        mutableIntStateOf(settings.defaultSplitPart1Percent)
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
            correctionAlertWaitMinutes = parsedCorrWait,
            widgetTransparencyPercent = widgetTransparency,
            defaultSplitPart1Percent = defaultSplitPart1Percent.coerceIn(10, 90)
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

    val topTitle = when (activeCategory) {
        null -> stringResource(R.string.nav_settings)
        SettingsCategory.APPEARANCE -> stringResource(R.string.settings_appearance)
        SettingsCategory.INSULIN_PEN -> stringResource(R.string.settings_factors_title)
        SettingsCategory.TARGETS -> stringResource(R.string.settings_target_pen_title)
        SettingsCategory.NOTIFICATIONS -> stringResource(R.string.settings_notifications_title)
        SettingsCategory.SENSOR -> stringResource(R.string.settings_sensor_title)
        SettingsCategory.BACKUP -> stringResource(R.string.settings_backup_title)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Schlichte Android-/Pixel-Kopfzeile mit Zurück-Pfeil
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (activeCategory != null) activeCategory = null else onBack()
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reports_back_cd),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = topTitle,
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (activeCategory == null) {
            // === KATEGORIEN-ÜBERSICHT (Android-Einstellungen Stil) ===
            val themeLabel = when (settings.themeMode) {
                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                ThemeMode.DARK -> stringResource(R.string.theme_dark)
            }
            val savedUnitLabel = GlucoseFormatter.unitLabel(settings.glucoseUnit)
            val savedFactorModeLabel = when (settings.carbFactorMode) {
                CarbFactorMode.GRAMS_PER_IE -> stringResource(R.string.factor_mode_g_per_ie)
                CarbFactorMode.IE_PER_10G -> stringResource(R.string.factor_mode_ie_per_10g)
                CarbFactorMode.IE_PER_BE -> stringResource(R.string.factor_mode_ie_per_be)
            }
            val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
            val savedFactorDisplay = CarbFactorFormatter.formatDisplay(
                settings.carbRatioForHour(currentHour),
                settings.carbFactorMode,
                locale
            )
            val savedIsfDisplay = GlucoseFormatter.formatFactorInput(settings.isf, settings.glucoseUnit)
            val savedPenDisplay = String.format(locale, "%.1f", settings.penStepSize)
            val savedTargetDisplay = GlucoseFormatter.formatValue(settings.targetBg, settings.glucoseUnit, locale)
            val savedMinDisplay = GlucoseFormatter.formatValue(settings.targetRangeMin, settings.glucoseUnit, locale)
            val savedMaxDisplay = GlucoseFormatter.formatValue(settings.targetRangeMax, settings.glucoseUnit, locale)

            val notifSummary = listOf(
                stringResource(
                    if (settings.persistentNotificationEnabled) R.string.settings_summary_notif_status_on
                    else R.string.settings_summary_notif_status_off
                ),
                stringResource(
                    if (settings.correctionAlertEnabled) R.string.settings_summary_notif_corr_on
                    else R.string.settings_summary_notif_corr_off
                )
            ).joinToString(" · ")

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsCategoryRow(
                    icon = Icons.Default.Palette,
                    iconBg = if (isDark) Color(0xFF2E3A59) else Color(0xFFDFE5F8),
                    iconTint = if (isDark) Color(0xFFB3C5FF) else Color(0xFF344E8F),
                    title = stringResource(R.string.settings_appearance),
                    subtitle = stringResource(
                        R.string.settings_summary_appearance,
                        themeLabel,
                        savedUnitLabel,
                        settings.widgetTransparencyPercent
                    ),
                    onClick = { activeCategory = SettingsCategory.APPEARANCE }
                )

                SettingsCategoryRow(
                    icon = Icons.Default.Vaccines,
                    iconBg = if (isDark) Color(0xFF213C47) else Color(0xFFD7EAF2),
                    iconTint = if (isDark) Color(0xFF8FD0E8) else Color(0xFF285F75),
                    title = stringResource(R.string.settings_factors_title),
                    subtitle = stringResource(
                        R.string.settings_summary_insulin,
                        savedFactorDisplay,
                        savedFactorModeLabel,
                        savedIsfDisplay,
                        savedPenDisplay,
                        ieUnit
                    ),
                    onClick = { activeCategory = SettingsCategory.INSULIN_PEN }
                )

                SettingsCategoryRow(
                    icon = Icons.Default.TrackChanges,
                    iconBg = if (isDark) Color(0xFF1B3F34) else Color(0xFFD4F0E5),
                    iconTint = if (isDark) Color(0xFF7AD9B8) else Color(0xFF0E6B4F),
                    title = stringResource(R.string.settings_target_pen_title),
                    subtitle = stringResource(
                        R.string.settings_summary_targets,
                        savedTargetDisplay,
                        savedMinDisplay,
                        savedMaxDisplay,
                        savedUnitLabel
                    ),
                    onClick = { activeCategory = SettingsCategory.TARGETS }
                )

                SettingsCategoryRow(
                    icon = Icons.Default.Notifications,
                    iconBg = if (isDark) Color(0xFF473A1D) else Color(0xFFFBEFD3),
                    iconTint = if (isDark) Color(0xFFF5CA72) else Color(0xFF8A5A00),
                    title = stringResource(R.string.settings_notifications_title),
                    subtitle = notifSummary,
                    onClick = { activeCategory = SettingsCategory.NOTIFICATIONS }
                )

                SettingsCategoryRow(
                    icon = Icons.Default.Sensors,
                    iconBg = if (isDark) Color(0xFF38304D) else Color(0xFFE8E2F8),
                    iconTint = if (isDark) Color(0xFFC8B8F5) else Color(0xFF533E8E),
                    title = stringResource(R.string.settings_sensor_title),
                    subtitle = stringResource(
                        if (settings.jugglucoEnabled) R.string.settings_summary_sensor_on
                        else R.string.settings_summary_sensor_off
                    ),
                    onClick = { activeCategory = SettingsCategory.SENSOR }
                )

                SettingsCategoryRow(
                    icon = Icons.Default.CloudUpload,
                    iconBg = if (isDark) Color(0xFF263D2B) else Color(0xFFDCEADE),
                    iconTint = if (isDark) Color(0xFF9AC9A6) else Color(0xFF356340),
                    title = stringResource(R.string.settings_backup_title),
                    subtitle = stringResource(R.string.settings_summary_backup),
                    onClick = { activeCategory = SettingsCategory.BACKUP }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "easyBOLUS v$versionName",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }
        } else {
            // === DETAIL-UNTERSEITE DER GEWÄHLTEN KATEGORIE ===
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (activeCategory) {
                    SettingsCategory.APPEARANCE -> {
                        SettingsDetailCard {
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

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

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

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                            Text(
                                text = stringResource(R.string.settings_widget_transparency, widgetTransparency),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Slider(
                                value = widgetTransparency.toFloat(),
                                onValueChange = { raw ->
                                    widgetTransparency = ((raw / 5f).roundToInt() * 5).coerceIn(0, 100)
                                },
                                valueRange = 0f..100f,
                                steps = 19,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    SettingsCategory.INSULIN_PEN -> {
                        val activeFactorModeLabel = when (carbFactorMode) {
                            CarbFactorMode.GRAMS_PER_IE -> stringResource(R.string.factor_mode_g_per_ie)
                            CarbFactorMode.IE_PER_10G -> stringResource(R.string.factor_mode_ie_per_10g)
                            CarbFactorMode.IE_PER_BE -> stringResource(R.string.factor_mode_ie_per_be)
                        }

                        // Karte 1: Berechnungsfaktor (Faktorvariante + Tagesfaktor ODER Stündliche Faktoren)
                        SettingsDetailCard {
                            Text(
                                text = stringResource(R.string.settings_card_factors),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = stringResource(R.string.settings_hourly_carb_ratio),
                                        fontWeight = FontWeight.Medium,
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

                            if (!useHourlyCarbRatio) {
                                OutlinedTextField(
                                    value = carbRatio,
                                    onValueChange = { carbRatio = it },
                                    label = { Text(carbRatioLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
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
                                            else R.string.settings_hourly_expand,
                                            activeFactorModeLabel
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

                                AnimatedVisibility(visible = hourlyExpanded) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val firstHourValue = hourlyCarbRatios[0].ifBlank {
                                            CarbFactorFormatter.formatInput(settings.carbRatio, carbFactorMode)
                                        }
                                        TextButton(
                                            onClick = {
                                                for (h in 0 until 24) {
                                                    hourlyCarbRatios[h] = firstHourValue
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                stringResource(
                                                    R.string.settings_hourly_copy_base,
                                                    "$firstHourValue $activeFactorModeLabel"
                                                )
                                            )
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

                        // Karte 2: Korrektur, DIA, Basal, Insulin-Wirkprofil & Pen-Schrittweite
                        SettingsDetailCard {
                            Text(
                                text = stringResource(R.string.settings_card_insulin_pen),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            }

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

                            Text(
                                text = stringResource(
                                    R.string.settings_split_ratio,
                                    defaultSplitPart1Percent,
                                    100 - defaultSplitPart1Percent
                                ),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Slider(
                                value = defaultSplitPart1Percent.toFloat(),
                                onValueChange = { raw ->
                                    defaultSplitPart1Percent = ((raw / 5f).roundToInt() * 5).coerceIn(20, 80)
                                },
                                valueRange = 20f..80f,
                                steps = 11
                            )
                        }
                    }

                    SettingsCategory.TARGETS -> {
                        SettingsDetailCard {
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

                    SettingsCategory.NOTIFICATIONS -> {
                        SettingsDetailCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = stringResource(R.string.settings_persistent_notif),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
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
                                        fontSize = 15.sp
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

                    SettingsCategory.SENSOR -> {
                        SettingsDetailCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = stringResource(R.string.settings_juggluco_toggle),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
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

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = onInsertDemoData, modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.btn_load_demo),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(onClick = onClearDemoData, modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.btn_clear_demo),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    SettingsCategory.BACKUP -> {
                        val chooserTitle = stringResource(R.string.backup_share_chooser_title)
                        SettingsDetailCard {
                            Text(
                                text = stringResource(R.string.backup_full_card_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.backup_full_card_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { exportFullJsonLauncher.launch("easyBOLUS_backup_$dateStamp.json") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.btn_backup_save_file))
                            }
                            androidx.compose.material3.FilledTonalButton(
                                onClick = {
                                    onRequestFullBackupJson { json ->
                                        runCatching {
                                            val exportDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
                                            val file = java.io.File(exportDir, "easyBOLUS_backup_$dateStamp.json")
                                            file.writeText(json, Charsets.UTF_8)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "application/json"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                putExtra(android.content.Intent.EXTRA_SUBJECT, "easyBOLUS_backup_$dateStamp.json")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(
                                                android.content.Intent.createChooser(sendIntent, chooserTitle)
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.btn_backup_send_drive))
                            }
                            OutlinedButton(
                                onClick = { importFullJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.btn_backup_restore_file))
                            }
                        }

                        SettingsDetailCard {
                            Text(
                                text = stringResource(R.string.backup_other_card_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        }
                    }

                    null -> Unit
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            if (activeCategory != SettingsCategory.BACKUP) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Button(
                        onClick = {
                            onSaveSettings(buildCurrentSettings())
                            Toast.makeText(context, savedToastText, Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(48.dp)
                    ) {
                        Text(stringResource(R.string.btn_save_settings), fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SettingsDetailCard(
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
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
