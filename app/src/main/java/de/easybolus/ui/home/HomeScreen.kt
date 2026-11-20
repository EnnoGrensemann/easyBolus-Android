package de.easybolus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.easybolus.R
import de.easybolus.data.local.TrendArrow
import de.easybolus.data.settings.GlucoseUnit
import de.easybolus.domain.GlucoseFormatter
import de.easybolus.ui.HomeUiState
import de.easybolus.ui.PeriodStats
import de.easybolus.ui.StatsPeriod
import de.easybolus.ui.theme.BgHighOrange
import de.easybolus.ui.theme.BgInRangeGreen
import de.easybolus.ui.theme.BgLowRed
import de.easybolus.ui.theme.CobPillBg
import de.easybolus.ui.theme.IobPillBg
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSaveManualGlucose: (mgDl: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val locale = Locale.getDefault()
    val unit = uiState.settings.glucoseUnit
    val unitText = GlucoseFormatter.unitLabel(unit)
    val ieUnit = stringResource(R.string.unit_ie)

    var selectedPeriod by remember { mutableStateOf(StatsPeriod.TODAY) }
    var showManualBgDialog by remember { mutableStateOf(false) }
    val currentStats = uiState.statsByPeriod[selectedPeriod] ?: PeriodStats()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        // 1. Live-Werte-Karte direkt oben (kein farbiger Header)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    val isStaleCgm = uiState.settings.jugglucoEnabled && (uiState.minutesAgo ?: 0L) > 15L
                    val latestBg = uiState.latestGlucose?.mgDl
                    val bgText = when {
                        latestBg == null || isStaleCgm -> "---"
                        else -> GlucoseFormatter.formatValue(latestBg, unit, locale)
                    }
                    val trend = uiState.latestGlucose?.trendArrow
                    val arrowText = if (isStaleCgm || trend == null || trend == TrendArrow.UNKNOWN) "" else trend.symbol
                    val statusDotColor = when {
                        latestBg == null || isStaleCgm -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        latestBg < uiState.settings.targetRangeMin -> BgLowRed
                        latestBg > uiState.settings.targetRangeMax -> BgHighOrange
                        else -> BgInRangeGreen
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bgText,
                            color = if (isStaleCgm) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = TextStyle(
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = (-2).sp,
                                fontFeatureSettings = "tnum"
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (arrowText.isNotEmpty()) {
                                Text(
                                    text = arrowText,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Light
                                )
                            }
                            Text(
                                text = unitText,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    val timeLabel = when (val mins = uiState.minutesAgo) {
                        null -> if (uiState.settings.jugglucoEnabled) {
                            stringResource(R.string.waiting_for_juggluco)
                        } else {
                            stringResource(R.string.no_manual_reading)
                        }
                        0L -> stringResource(R.string.just_now)
                        in 1L..119L -> stringResource(R.string.minutes_ago, mins)
                        else -> stringResource(R.string.hours_ago, mins / 60L)
                    }
                    val deltaText = if (!isStaleCgm && uiState.latestDeltaMgDl != null) {
                        "${GlucoseFormatter.formatSignedDelta(uiState.latestDeltaMgDl, unit, locale)} $unitText"
                    } else {
                        null
                    }
                    val subtitleText = if (deltaText != null) "$deltaText · $timeLabel" else timeLabel

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = subtitleText,
                            color = if (isStaleCgm) {
                                if (isDark) Color(0xFFFDE68A) else Color(0xFFD97706)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = if (isStaleCgm) FontWeight.Medium else FontWeight.Normal,
                                fontFeatureSettings = "tnum"
                            )
                        )
                        if (!uiState.settings.jugglucoEnabled) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clickable { showManualBgDialog = true }
                            ) {
                                Text(
                                    text = stringResource(R.string.manual_bg_short),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Kompakte IOB & COB Pill-Badges übereinander rechts
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    StatusPill(
                        icon = Icons.Default.Vaccines,
                        text = String.format(locale, "%.2f %s", uiState.currentIob, ieUnit),
                        backgroundColor = IobPillBg
                    )
                    StatusPill(
                        icon = Icons.Default.Grain,
                        text = String.format(locale, "%.1f g", uiState.currentCob),
                        backgroundColor = CobPillBg
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Kompakte Statistik-Leiste (Heute / 24h / 7T / 90T)
        StatsSummaryCard(
            stats = currentStats,
            unit = unit,
            selectedPeriod = selectedPeriod,
            onSelectPeriod = { selectedPeriod = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Dual-Axis Chart endet sauber mit Abstand oberhalb des Extended FAB
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 84.dp)
        ) {
            if (uiState.chartReadings.isEmpty() && uiState.chartEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.settings.jugglucoEnabled) R.string.chart_empty_juggluco
                            else R.string.chart_empty_manual
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                GlucoseDualAxisChart(
                    readings = uiState.chartReadings,
                    entries = uiState.chartEntries,
                    settings = uiState.settings,
                    windowHours = uiState.chartWindowHours,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (showManualBgDialog) {
        ManualGlucoseDialog(
            unit = unit,
            onDismiss = { showManualBgDialog = false },
            onSave = { mgDl ->
                onSaveManualGlucose(mgDl)
                showManualBgDialog = false
            }
        )
    }
}

@Composable
private fun ManualGlucoseDialog(
    unit: GlucoseUnit,
    onDismiss: () -> Unit,
    onSave: (mgDl: Int) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val unitLabel = GlucoseFormatter.unitLabel(unit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_bg_dialog_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.manual_bg_input_label, unitLabel)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (unit == GlucoseUnit.MG_DL) KeyboardType.Number else KeyboardType.Decimal
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mgDl = GlucoseFormatter.parseToMgDl(input, unit)
                    if (mgDl != null) onSave(mgDl)
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    )
}

@Composable
private fun StatsSummaryCard(
    stats: PeriodStats,
    unit: GlucoseUnit,
    selectedPeriod: StatsPeriod,
    onSelectPeriod: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val unitLabel = GlucoseFormatter.unitLabel(unit)
    val ieUnit = stringResource(R.string.unit_ie)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Kopfzeile: Titel + 4 Zeitraum-Buttons (Heute / 24h / 7T / 90T)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stats_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    StatsPeriod.entries.forEach { period ->
                        val active = period == selectedPeriod
                        val label = when (period) {
                            StatsPeriod.TODAY -> stringResource(R.string.period_today)
                            StatsPeriod.HOURS_24 -> stringResource(R.string.period_24h)
                            StatsPeriod.DAYS_7 -> stringResource(R.string.period_7d)
                            StatsPeriod.DAYS_90 -> stringResource(R.string.period_90d)
                        }
                        Surface(
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable { onSelectPeriod(period) }
                        ) {
                            Text(
                                text = label,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // TIR-Farbbalken mit klaren Trennlinien + Low / Ziel / High Prozentwerte
            val lowPct = stats.timeBelowPercent ?: 0.0
            val inPct = stats.timeInRangePercent ?: 0.0
            val highPct = stats.timeAbovePercent ?: 0.0
            val hasReadings = stats.readingCount > 0
            val segments = buildList {
                if (hasReadings) {
                    if (lowPct > 0.0) add(lowPct.toFloat().coerceAtLeast(0.01f) to BgLowRed)
                    if (inPct > 0.0) add(inPct.toFloat().coerceAtLeast(0.01f) to BgInRangeGreen)
                    if (highPct > 0.0) add(highPct.toFloat().coerceAtLeast(0.01f) to BgHighOrange)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    segments.forEachIndexed { index, (weightVal, segColor) ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(weightVal)
                                .fillMaxHeight()
                                .background(segColor)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TirLegendLabel(
                        dotColor = BgLowRed,
                        text = if (hasReadings) stringResource(R.string.stat_low, lowPct.roundToInt())
                        else stringResource(R.string.stat_low_empty),
                        bold = false
                    )
                    TirLegendLabel(
                        dotColor = BgInRangeGreen,
                        text = if (hasReadings) stringResource(R.string.stat_tir, inPct.roundToInt())
                        else stringResource(R.string.stat_tir_empty),
                        bold = true
                    )
                    TirLegendLabel(
                        dotColor = BgHighOrange,
                        text = if (hasReadings) stringResource(R.string.stat_high, highPct.roundToInt())
                        else stringResource(R.string.stat_high_empty),
                        bold = false
                    )
                }
            }

            // 4 kompakte Kennzahlen: Ø BZ, GMI, Bolus gesamt (mit Ø/Tag bei 7T/90T), KH gesamt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatMetricItem(
                    label = stringResource(R.string.stat_avg_bg),
                    value = stats.averageBg?.let { GlucoseFormatter.formatValue(it, unit, locale) } ?: "--",
                    subLabel = if (stats.averageBg != null) unitLabel else null,
                    modifier = Modifier.weight(1f)
                )
                StatMetricItem(
                    label = stringResource(R.string.stat_gmi),
                    value = stats.gmiPercent?.let { String.format(locale, "%.1f%%", it) } ?: "--",
                    subLabel = stringResource(R.string.stat_ehba1c),
                    modifier = Modifier.weight(1f)
                )
                StatMetricItem(
                    label = stringResource(R.string.stat_bolus),
                    value = String.format(locale, "%.1f %s", stats.totalBolusUnits, ieUnit),
                    subLabel = stats.avgDailyBolusUnits?.let {
                        stringResource(R.string.stat_per_day, String.format(locale, "%.1f", it))
                    },
                    modifier = Modifier.weight(1f)
                )
                StatMetricItem(
                    label = stringResource(R.string.stat_carbs),
                    value = "${stats.totalCarbsGrams.roundToInt()} g",
                    subLabel = null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatMetricItem(
    label: String,
    value: String,
    subLabel: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            )
        )
        if (subLabel != null) {
            Text(
                text = subLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = TextStyle(fontSize = 9.sp, fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
private fun TirLegendLabel(
    dotColor: Color,
    text: String,
    bold: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
                fontFeatureSettings = "tnum"
            )
        )
    }
}

@Composable
private fun StatusPill(
    icon: ImageVector,
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
            .width(114.dp)
            .height(30.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = Color.White,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                )
            )
        }
    }
}
