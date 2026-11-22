package de.easybolus.ui.reports

import android.graphics.Paint
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.easybolus.R
import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.settings.GlucoseUnit
import de.easybolus.data.settings.UserSettings
import de.easybolus.domain.AgpBucket
import de.easybolus.domain.GlucoseFormatter
import de.easybolus.domain.ReportCalculator
import de.easybolus.domain.ReportPeriod
import de.easybolus.domain.ReportSummary
import de.easybolus.ui.theme.BgHighOrange
import de.easybolus.ui.theme.BgInRangeGreen
import de.easybolus.ui.theme.BgLowRed
import de.easybolus.ui.theme.BgVeryHighOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ReportsScreen(
    readings: List<GlucoseReadingEntity>,
    entries: List<DiaryEntryEntity>,
    settings: UserSettings,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val dateRangeFmt = remember(locale) { SimpleDateFormat("dd. MMM yyyy", locale) }
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.DAYS_14) }

    val summary = remember(readings, entries, selectedPeriod, settings) {
        ReportCalculator.calculate(
            readings = readings,
            entries = entries,
            period = selectedPeriod,
            settings = settings
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Schlichte transparente Kopfzeile (Google Material You Stil)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (onBack != null) 8.dp else 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 4.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reports_back_cd),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reports_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${dateRangeFmt.format(Date(summary.fromMillis))} – ${dateRangeFmt.format(Date(summary.toMillis))}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Zeitraum-Pills: 7 / 14 / 30 / 90 Tage
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportPeriod.entries.forEach { period ->
                val selected = period == selectedPeriod
                val label = when (period) {
                    ReportPeriod.DAYS_7 -> stringResource(R.string.reports_period_7d)
                    ReportPeriod.DAYS_14 -> stringResource(R.string.reports_period_14d)
                    ReportPeriod.DAYS_30 -> stringResource(R.string.reports_period_30d)
                    ReportPeriod.DAYS_90 -> stringResource(R.string.reports_period_90d)
                }
                Surface(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .clickable { selectedPeriod = period }
                ) {
                    Text(
                        text = label,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.reports_active_days, summary.activeDays, selectedPeriod.days),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )

            // 1. Zeit im Zielbereich (5-stufig wie Libre 3)
            FiveTierTirCard(summary = summary, settings = settings)

            // 2. Tagesmuster (AGP 5–95%, 25–75%, Median)
            AgpPatternCard(summary = summary, settings = settings)

            // 3. Kennzahlen-Übersicht
            OverviewMetricsCard(summary = summary, settings = settings)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FiveTierTirCard(
    summary: ReportSummary,
    settings: UserSettings
) {
    val locale = Locale.getDefault()
    val unit = settings.glucoseUnit
    val unitLabel = GlucoseFormatter.unitLabel(unit)

    val vhText = GlucoseFormatter.formatValue(ReportCalculator.VERY_HIGH_THRESHOLD, unit, locale)
    val maxText = GlucoseFormatter.formatValue(settings.targetRangeMax, unit, locale)
    val minText = GlucoseFormatter.formatValue(settings.targetRangeMin, unit, locale)
    val vlText = GlucoseFormatter.formatValue(ReportCalculator.VERY_LOW_THRESHOLD, unit, locale)

    val lowOrangeRed = Color(0xFFFF6D00)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.reports_tir_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = unitLabel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TirTierRow(
                rangeLabel = stringResource(R.string.reports_tier_very_high, vhText),
                percent = summary.veryHighPct,
                barColor = BgVeryHighOrange
            )
            TirTierRow(
                rangeLabel = stringResource(R.string.reports_tier_range, maxText, vhText),
                percent = summary.highPct,
                barColor = BgHighOrange
            )
            TirTierRow(
                rangeLabel = stringResource(R.string.reports_tier_range, minText, maxText),
                percent = summary.inRangePct,
                barColor = BgInRangeGreen
            )
            TirTierRow(
                rangeLabel = stringResource(R.string.reports_tier_range, vlText, minText),
                percent = summary.lowPct,
                barColor = lowOrangeRed
            )
            TirTierRow(
                rangeLabel = stringResource(R.string.reports_tier_very_low, vlText),
                percent = summary.veryLowPct,
                barColor = BgLowRed
            )

            Text(
                text = stringResource(R.string.reports_tir_target_range, minText, maxText, unitLabel),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun TirTierRow(
    rangeLabel: String,
    percent: Double,
    barColor: Color
) {
    val pctInt = percent.roundToInt()
    val fraction = (percent / 100.0).toFloat().coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = rangeLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum"),
            textAlign = TextAlign.End,
            modifier = Modifier.width(74.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.03f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(barColor)
                )
            }
        }
        Text(
            text = "$pctInt%",
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp)
        )
    }
}

@Composable
private fun AgpPatternCard(
    summary: ReportSummary,
    settings: UserSettings
) {
    val unit = settings.glucoseUnit
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = if (isDark) Color(0xFF323338) else Color(0xFFE5E7EB)
    val medianColor = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val outerBandColor = Color(0xFF60A5FA).copy(alpha = if (isDark) 0.25f else 0.28f)
    val innerBandColor = Color(0xFF2563EB).copy(alpha = if (isDark) 0.45f else 0.42f)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.reports_agp_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.reports_agp_legend),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (summary.agpBuckets.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.reports_agp_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                AgpCanvas(
                    buckets = summary.agpBuckets,
                    settings = settings,
                    unit = unit,
                    labelColor = labelColor,
                    gridColor = gridColor,
                    outerBandColor = outerBandColor,
                    innerBandColor = innerBandColor,
                    medianColor = medianColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
        }
    }
}

@Composable
private fun AgpCanvas(
    buckets: List<AgpBucket>,
    settings: UserSettings,
    unit: GlucoseUnit,
    labelColor: Color,
    gridColor: Color,
    outerBandColor: Color,
    innerBandColor: Color,
    medianColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val leftPad = 36.dp.toPx()
        val rightPad = 10.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val chartW = (size.width - leftPad - rightPad).coerceAtLeast(10f)
        val chartH = (size.height - topPad - bottomPad).coerceAtLeast(10f)

        val maxP95 = buckets.maxOfOrNull { it.p95 } ?: settings.targetRangeMax.toFloat()
        val rawMaxMgDl = max(maxP95, settings.targetRangeMax.toFloat())
        val bgMaxDisplay: Float
        val bgStep: Int
        if (unit == GlucoseUnit.MMOL_L) {
            val maxMmol = GlucoseFormatter.mgDlToMmol(rawMaxMgDl.toDouble()).toFloat()
            bgMaxDisplay = max(18f, ceil((maxMmol + 1f) / 3f) * 3f)
            bgStep = 3
        } else {
            bgMaxDisplay = max(300f, ceil((rawMaxMgDl + 15f) / 50f) * 50f)
            bgStep = 50
        }

        fun toDisplay(mgDl: Float): Float = when (unit) {
            GlucoseUnit.MG_DL -> mgDl
            GlucoseUnit.MMOL_L -> GlucoseFormatter.mgDlToMmol(mgDl.toDouble()).toFloat()
        }

        fun xForMin(minute: Int): Float = leftPad + (minute.toFloat() / 1440f) * chartW
        fun yForDisplay(v: Float): Float = topPad + chartH - (v / bgMaxDisplay).coerceIn(0f, 1f) * chartH
        fun yForMgDl(mgDl: Float): Float = yForDisplay(toDisplay(mgDl))

        val yPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.dp.toPx()
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        for (tick in 0..bgMaxDisplay.roundToInt() step bgStep) {
            val y = yForDisplay(tick.toFloat())
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + chartW, y),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                tick.toString(),
                leftPad - 6.dp.toPx(),
                y + 3.5.dp.toPx(),
                yPaint
            )
        }

        // X-Achse: 00:00, 03:00, 06:00, 09:00, 12:00, 15:00, 18:00, 21:00, 24:00
        val xPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 9.5.dp.toPx()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        for (h in 0..24 step 4) {
            val x = xForMin(h * 60)
            drawLine(
                color = gridColor,
                start = Offset(x, topPad),
                end = Offset(x, topPad + chartH),
                strokeWidth = 1.dp.toPx()
            )
            val label = String.format(Locale.US, "%02d:00", h % 24)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x.coerceIn(leftPad + 14.dp.toPx(), leftPad + chartW - 14.dp.toPx()),
                topPad + chartH + 16.dp.toPx(),
                xPaint
            )
        }

        // 5% - 95% Perzentilband (hellblau)
        val outerPath = Path().apply {
            buckets.forEachIndexed { i, b ->
                val x = xForMin(b.minuteOfDay)
                val y = yForMgDl(b.p95)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            for (i in buckets.lastIndex downTo 0) {
                val b = buckets[i]
                lineTo(xForMin(b.minuteOfDay), yForMgDl(b.p5))
            }
            close()
        }
        drawPath(outerPath, color = outerBandColor)

        // 25% - 75% Perzentilband (mittelblau)
        val innerPath = Path().apply {
            buckets.forEachIndexed { i, b ->
                val x = xForMin(b.minuteOfDay)
                val y = yForMgDl(b.p75)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            for (i in buckets.lastIndex downTo 0) {
                val b = buckets[i]
                lineTo(xForMin(b.minuteOfDay), yForMgDl(b.p25))
            }
            close()
        }
        drawPath(innerPath, color = innerBandColor)

        // Zielbereich-Grenzlinien (grün wie Libre 3)
        val yMinTarget = yForMgDl(settings.targetRangeMin.toFloat())
        val yMaxTarget = yForMgDl(settings.targetRangeMax.toFloat())
        drawLine(
            color = BgInRangeGreen,
            start = Offset(leftPad, yMinTarget),
            end = Offset(leftPad + chartW, yMinTarget),
            strokeWidth = 1.4.dp.toPx()
        )
        drawLine(
            color = BgInRangeGreen,
            start = Offset(leftPad, yMaxTarget),
            end = Offset(leftPad + chartW, yMaxTarget),
            strokeWidth = 1.4.dp.toPx()
        )
        // Dezente Zielwert-Linie
        val yTarget = yForMgDl(settings.targetBg.toFloat())
        drawLine(
            color = BgInRangeGreen.copy(alpha = 0.6f),
            start = Offset(leftPad, yTarget),
            end = Offset(leftPad + chartW, yTarget),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )

        // Median-Linie (50%)
        val medianPath = Path().apply {
            buckets.forEachIndexed { i, b ->
                val x = xForMin(b.minuteOfDay)
                val y = yForMgDl(b.p50)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            path = medianPath,
            color = medianColor,
            style = Stroke(
                width = 2.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun OverviewMetricsCard(
    summary: ReportSummary,
    settings: UserSettings
) {
    val locale = Locale.getDefault()
    val unit = settings.glucoseUnit
    val unitLabel = GlucoseFormatter.unitLabel(unit)
    val ieUnit = stringResource(R.string.unit_ie)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.reports_summary_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                ReportMetricTile(
                    label = stringResource(R.string.stat_avg_bg),
                    value = summary.averageBg?.let { "${GlucoseFormatter.formatValue(it, unit, locale)} $unitLabel" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                ReportMetricTile(
                    label = stringResource(R.string.stat_gmi),
                    value = summary.gmiPercent?.let { String.format(locale, "%.1f%%", it) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                ReportMetricTile(
                    label = stringResource(R.string.reports_cv),
                    value = summary.cvPercent?.let { String.format(locale, "%.1f%%", it) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                ReportMetricTile(
                    label = stringResource(R.string.reports_bolus_day),
                    value = String.format(locale, "%.1f %s", summary.avgDailyBolus, ieUnit),
                    modifier = Modifier.weight(1f)
                )
                ReportMetricTile(
                    label = stringResource(R.string.reports_carbs_day),
                    value = "${summary.avgDailyCarbs.roundToInt()} g",
                    modifier = Modifier.weight(1f)
                )
                ReportMetricTile(
                    label = stringResource(R.string.reports_readings_count),
                    value = summary.totalReadings.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReportMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            textAlign = TextAlign.Center
        )
    }
}
