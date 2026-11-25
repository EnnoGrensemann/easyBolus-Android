package de.easybolus.ui.reports

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.easybolus.R
import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.settings.GlucoseUnit
import de.easybolus.data.settings.UserSettings
import de.easybolus.domain.GlucoseFormatter
import de.easybolus.ui.theme.BgHighOrange
import de.easybolus.ui.theme.BgInRangeGreen
import de.easybolus.ui.theme.BgLowRed
import de.easybolus.ui.theme.BolusMarkerBlue
import de.easybolus.ui.theme.CarbsMarkerOrange
import de.easybolus.ui.theme.TargetBandGreen
import de.easybolus.ui.theme.TargetBandGreenDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Full-day (00:00 - 24:00) Libre-3-style chart for a single calendar day in Tagesberichte.
 */
@Composable
fun DailyReportChart(
    dayStartMillis: Long,
    readings: List<GlucoseReadingEntity>,
    entries: List<DiaryEntryEntity>,
    settings: UserSettings,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val timeFmt = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val unit = settings.glucoseUnit
    val unitLabel = GlucoseFormatter.unitLabel(unit)
    val ieUnit = stringResource(R.string.unit_ie)

    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = if (isDark) Color(0xFF323338) else Color(0xFFE5E7EB)
    val targetBandColor = if (isDark) TargetBandGreenDark else TargetBandGreen
    val curveColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)

    var selectedTimeMillis by remember(dayStartMillis) { mutableStateOf<Long?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dayStartMillis) {
                    detectTapGestures { offset ->
                        val leftPadPx = 36.dp.toPx()
                        val rightPadPx = 10.dp.toPx()
                        val chartWPx = (size.width - leftPadPx - rightPadPx).coerceAtLeast(10f)
                        val relX = ((offset.x - leftPadPx) / chartWPx).coerceIn(0f, 1f)
                        val tappedMs = dayStartMillis + (relX * 24L * 3600_000L).toLong()
                        selectedTimeMillis = if (selectedTimeMillis != null && abs(selectedTimeMillis!! - tappedMs) < 15 * 60_000L) {
                            null
                        } else {
                            tappedMs
                        }
                    }
                }
        ) {
            val leftPad = 36.dp.toPx()
            val rightPad = 10.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 24.dp.toPx()
            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(10f)
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(10f)

            val maxReadingMgDl = readings.maxOfOrNull { it.mgDl.toFloat() } ?: settings.targetRangeMax.toFloat()
            val rawMaxMgDl = max(maxReadingMgDl, settings.targetRangeMax.toFloat())
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

            fun xForMs(ts: Long): Float {
                val rel = ((ts - dayStartMillis).toFloat() / (24f * 3600_000f)).coerceIn(0f, 1f)
                return leftPad + rel * chartW
            }

            fun yForDisplay(v: Float): Float = topPad + chartH - (v / bgMaxDisplay).coerceIn(0f, 1f) * chartH
            fun yForMgDl(mgDl: Float): Float = yForDisplay(toDisplay(mgDl))

            // Zielbereichsband
            val yBandTop = yForMgDl(settings.targetRangeMax.toFloat())
            val yBandBottom = yForMgDl(settings.targetRangeMin.toFloat())
            drawRect(
                color = targetBandColor,
                topLeft = Offset(leftPad, yBandTop),
                size = Size(chartW, (yBandBottom - yBandTop).coerceAtLeast(1f))
            )

            // Y-Achse & horizontale Grid-Linien
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

            // X-Achse: 00:00, 04:00, 08:00, 12:00, 16:00, 20:00, 24:00
            val xPaint = Paint().apply {
                color = labelColor.toArgb()
                textSize = 9.5.dp.toPx()
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            for (h in 0..24 step 4) {
                val x = leftPad + (h / 24f) * chartW
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

            // Zielwert-Linie
            val yTarget = yForMgDl(settings.targetBg.toFloat())
            drawLine(
                color = BgInRangeGreen.copy(alpha = 0.65f),
                start = Offset(leftPad, yTarget),
                end = Offset(leftPad + chartW, yTarget),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )

            // Glukose-Linie farbcodiert nach Zielbereich (Segmentierung bei Lücken > 25 min)
            if (readings.size >= 2) {
                for (i in 1 until readings.size) {
                    val prev = readings[i - 1]
                    val cur = readings[i]
                    if ((cur.timestampMillis - prev.timestampMillis) <= 25 * 60_000L) {
                        val segColor = when {
                            cur.mgDl < settings.targetRangeMin || prev.mgDl < settings.targetRangeMin -> BgLowRed
                            cur.mgDl > settings.targetRangeMax || prev.mgDl > settings.targetRangeMax -> BgHighOrange
                            else -> BgInRangeGreen
                        }
                        drawLine(
                            color = segColor,
                            start = Offset(xForMs(prev.timestampMillis), yForMgDl(prev.mgDl.toFloat())),
                            end = Offset(xForMs(cur.timestampMillis), yForMgDl(cur.mgDl.toFloat())),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Farbcodierte Messpunkte (ausgedünnt, wenn sehr viele Punkte)
            val step = (readings.size / 140).coerceAtLeast(1)
            readings.forEachIndexed { idx, r ->
                if (idx % step == 0 || idx == readings.lastIndex) {
                    val dotColor = when {
                        r.mgDl < settings.targetRangeMin -> BgLowRed
                        r.mgDl > settings.targetRangeMax -> BgHighOrange
                        else -> BgInRangeGreen
                    }
                    drawCircle(
                        color = dotColor,
                        radius = 2.4.dp.toPx(),
                        center = Offset(xForMs(r.timestampMillis), yForMgDl(r.mgDl.toFloat()))
                    )
                }
            }

            // Bolus & KH Marker am unteren Rand / bei zugehörigem BZ
            val markerPaint = Paint().apply {
                textSize = 9.dp.toPx()
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            for (e in entries) {
                val x = xForMs(e.timestampMillis)
                if (e.type == DiaryEntryType.BOLUS && (e.bolusUnits ?: 0.0) > 0.0) {
                    val yBase = e.bgAtTimeMgDl?.let { yForMgDl(it.toFloat()) } ?: (topPad + chartH - 12.dp.toPx())
                    val triY = (yBase - 8.dp.toPx()).coerceAtLeast(topPad + 6.dp.toPx())
                    val tri = Path().apply {
                        moveTo(x, triY + 6.dp.toPx())
                        lineTo(x - 4.dp.toPx(), triY)
                        lineTo(x + 4.dp.toPx(), triY)
                        close()
                    }
                    drawPath(tri, color = BolusMarkerBlue)
                    markerPaint.color = BolusMarkerBlue.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format(locale, "%.1f", e.bolusUnits),
                        x,
                        (triY - 2.dp.toPx()).coerceAtLeast(topPad + 8.dp.toPx()),
                        markerPaint
                    )
                } else if (e.type == DiaryEntryType.CARBS && (e.carbsGrams ?: 0.0) > 0.0) {
                    val yBase = e.bgAtTimeMgDl?.let { yForMgDl(it.toFloat()) } ?: (topPad + chartH - 12.dp.toPx())
                    val triY = (yBase + 8.dp.toPx()).coerceAtMost(topPad + chartH - 6.dp.toPx())
                    val tri = Path().apply {
                        moveTo(x, triY - 6.dp.toPx())
                        lineTo(x - 4.dp.toPx(), triY)
                        lineTo(x + 4.dp.toPx(), triY)
                        close()
                    }
                    drawPath(tri, color = CarbsMarkerOrange)
                }
            }

            // Tap-Auswahl-Fadenkreuz
            selectedTimeMillis?.let { selMs ->
                val selX = xForMs(selMs)
                drawLine(
                    color = labelColor.copy(alpha = 0.6f),
                    start = Offset(selX, topPad),
                    end = Offset(selX, topPad + chartH),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
            }
        }

        // Tap-Tooltip
        selectedTimeMillis?.let { selMs ->
            val nearest = readings.minByOrNull { abs(it.timestampMillis - selMs) }
                ?.takeIf { abs(it.timestampMillis - selMs) <= 20 * 60_000L }
            val nearestBolus = entries.filter { it.type == DiaryEntryType.BOLUS }
                .minByOrNull { abs(it.timestampMillis - selMs) }
                ?.takeIf { abs(it.timestampMillis - selMs) <= 20 * 60_000L }
            if (nearest != null || nearestBolus != null) {
                val ts = nearest?.timestampMillis ?: nearestBolus!!.timestampMillis
                val bgPart = nearest?.let { "${GlucoseFormatter.formatValue(it.mgDl, unit, locale)} $unitLabel" }
                val bolusPart = nearestBolus?.bolusUnits?.let { String.format(locale, "%.1f %s", it, ieUnit) }
                val label = listOfNotNull(timeFmt.format(Date(ts)), bgPart, bolusPart).joinToString(" · ")
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
