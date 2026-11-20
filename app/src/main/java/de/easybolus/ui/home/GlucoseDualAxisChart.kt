package de.easybolus.ui.home

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import de.easybolus.domain.IobCobCalculator
import de.easybolus.ui.theme.BgHighOrange
import de.easybolus.ui.theme.BgInRangeGreen
import de.easybolus.ui.theme.BgLowRed
import de.easybolus.ui.theme.BolusMarkerBlue
import de.easybolus.ui.theme.CarbsMarkerOrange
import de.easybolus.ui.theme.CobCurveOlive
import de.easybolus.ui.theme.IobAreaBlue
import de.easybolus.ui.theme.IobCurveBlue
import de.easybolus.ui.theme.TargetBandGreen
import de.easybolus.ui.theme.TargetBandGreenDark
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun GlucoseDualAxisChart(
    readings: List<GlucoseReadingEntity>,
    entries: List<DiaryEntryEntity>,
    settings: UserSettings,
    windowHours: Int = 6,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val timeFormat = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val dateFormat = remember(locale) { SimpleDateFormat("EE, dd.MM.", locale) }
    val dateTimeFormat = remember(locale) { SimpleDateFormat("dd.MM. HH:mm", locale) }

    val maxHistoryHours = 7f * 24f // 7x24h verschiebbar
    var visibleHours by remember(windowHours) { mutableFloatStateOf(windowHours.toFloat()) }
    var panBackMillis by remember { mutableLongStateOf(0L) }
    var selectedTimeMillis by remember { mutableStateOf<Long?>(null) }

    val unit = settings.glucoseUnit
    val unitLabel = GlucoseFormatter.unitLabel(unit)
    val ieUnit = stringResource(R.string.unit_ie)
    val bgAxisTitle = stringResource(R.string.chart_axis_bg, unitLabel)
    val insAxisTitle = stringResource(R.string.chart_axis_insulin, ieUnit)
    val targetLineLabel = stringResource(
        R.string.chart_target_label,
        GlucoseFormatter.formatValue(settings.targetBg, unit, locale)
    )
    val resetLabel = stringResource(R.string.chart_reset_now, windowHours)

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val verticalGridColor = if (isDark) Color(0xFF323338) else Color(0xFFE5E7EB)
    val horizontalGridColor = if (isDark) Color(0xFF2B2C30) else Color(0xFFF1F3F5)
    val axisBorderColor = if (isDark) Color(0xFF45474F) else Color(0xFFCBD5E1)
    val targetBandColor = if (isDark) TargetBandGreenDark else TargetBandGreen
    val targetLineColor = if (isDark) Color(0xFFE0F2FE).copy(alpha = 0.88f) else Color(0xFF0F4C5C).copy(alpha = 0.82f)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Maximal bis 24h herauszoombar, danach über 7 Tage (168h) verschiebbar
                        val newHours = (visibleHours / zoom).coerceIn(1f, 24f)
                        visibleHours = newHours

                        val maxPanMillis = ((maxHistoryHours - newHours).coerceAtLeast(0f) * 3600_000L).toLong()
                        if (pan.x != 0f && size.width > 0) {
                            val msPerPx = (newHours * 3600_000f) / size.width.toFloat()
                            val deltaMs = (pan.x * msPerPx).toLong()
                            panBackMillis = (panBackMillis + deltaMs).coerceIn(0L, maxPanMillis)
                        } else {
                            panBackMillis = panBackMillis.coerceIn(0L, maxPanMillis)
                        }
                    }
                }
                .pointerInput(visibleHours, panBackMillis) {
                    detectTapGestures { offset ->
                        val leftPadPx = 48.dp.toPx()
                        val rightPadPx = 42.dp.toPx()
                        val chartWPx = (size.width - leftPadPx - rightPadPx).coerceAtLeast(10f)
                        val relX = ((offset.x - leftPadPx) / chartWPx).coerceIn(0f, 1f)
                        val nowMs = System.currentTimeMillis()
                        val endMs = nowMs - panBackMillis
                        val spanMs = (visibleHours * 3600_000f).toLong().coerceAtLeast(3600_000L)
                        val tappedMs = endMs - spanMs + (relX * spanMs).toLong()
                        selectedTimeMillis = if (selectedTimeMillis != null && abs(selectedTimeMillis!! - tappedMs) < 10 * 60_000L) {
                            null
                        } else {
                            tappedMs
                        }
                    }
                }
        ) {
            val leftPad = 48.dp.toPx()
            val rightPad = 42.dp.toPx()
            val topPad = 16.dp.toPx()
            val bottomPad = 28.dp.toPx()

            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(10f)
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(10f)

            val now = System.currentTimeMillis()
            val endTime = now - panBackMillis
            val timeSpanMillis = (visibleHours * 3600_000f).toLong().coerceAtLeast(3600_000L)
            val startTime = endTime - timeSpanMillis
            val timeSpan = (endTime - startTime).toFloat().coerceAtLeast(1f)

            val visibleReadings = readings.filter { it.timestampMillis in startTime..endTime }

            // Linke Y-Achse (Blutzucker): mindestens 0–300 mg/dl bzw. 0–18 mmol/L
            val maxReadingMgDl = visibleReadings.maxOfOrNull { it.mgDl } ?: settings.targetRangeMax
            val rawMaxMgDl = max(maxReadingMgDl, settings.targetRangeMax).toFloat()
            val bgMinDisplay = 0f
            val bgMaxDisplay: Float
            val bgTickStepDisplay: Int
            if (unit == GlucoseUnit.MMOL_L) {
                val rawMaxMmol = GlucoseFormatter.mgDlToMmol(rawMaxMgDl.toDouble()).toFloat()
                bgMaxDisplay = max(18f, ceil((rawMaxMmol + 1f) / 3f) * 3f)
                bgTickStepDisplay = 3
            } else {
                bgMaxDisplay = max(300f, ceil((rawMaxMgDl + 15f) / 50f) * 50f)
                bgTickStepDisplay = 50
            }
            val bgSpanDisplay = (bgMaxDisplay - bgMinDisplay).coerceAtLeast(1f)

            fun mgDlToDisplay(mgDl: Float): Float = when (unit) {
                GlucoseUnit.MG_DL -> mgDl
                GlucoseUnit.MMOL_L -> GlucoseFormatter.mgDlToMmol(mgDl.toDouble()).toFloat()
            }

            // Exponentielle IOB-Kurve & COB vorab samplen für rechte Y-Achse (mindestens 0–10 IE)
            val sampleCount = 96
            val iobSamples = FloatArray(sampleCount + 1)
            val cobScaledSamples = FloatArray(sampleCount + 1)
            var maxRightVal = 0f
            for (i in 0..sampleCount) {
                val t = startTime + (i.toFloat() / sampleCount * timeSpan).toLong()
                val iob = IobCobCalculator.calculateExponentialIob(
                    entries = entries,
                    atTimeMillis = t,
                    diaHours = settings.diaHours,
                    peakMinutes = settings.insulinType.peakMinutes
                ).toFloat()
                val cob = IobCobCalculator.calculateLinearCob(entries, t, settings.carbAbsorptionHours).toFloat()
                val cobScaled = cob / 40f
                iobSamples[i] = iob
                cobScaledSamples[i] = cobScaled
                maxRightVal = max(maxRightVal, max(iob, cobScaled))
            }

            val insMin = 0f
            val insMax = max(10f, ceil(maxRightVal * 1.15f))

            fun xForTime(t: Long): Float =
                leftPad + ((t - startTime).toFloat() / timeSpan) * chartW

            fun yForBgDisplay(bgDisplay: Float): Float =
                topPad + chartH - ((bgDisplay - bgMinDisplay) / bgSpanDisplay).coerceIn(0f, 1f) * chartH

            fun yForBgMgDl(bgMgDl: Float): Float = yForBgDisplay(mgDlToDisplay(bgMgDl))

            fun yForIns(ie: Float): Float =
                topPad + chartH - ((ie - insMin) / (insMax - insMin)).coerceIn(0f, 1f) * chartH

            // 1. Zielbereich-Band + deutliche gestrichelte Zielwert-Linie
            val bandTop = yForBgMgDl(settings.targetRangeMax.toFloat())
            val bandBottom = yForBgMgDl(settings.targetRangeMin.toFloat())
            drawRect(
                color = targetBandColor,
                topLeft = Offset(leftPad, bandTop),
                size = Size(chartW, (bandBottom - bandTop).coerceAtLeast(0f))
            )
            val targetY = yForBgMgDl(settings.targetBg.toFloat())
            drawLine(
                color = targetLineColor,
                start = Offset(leftPad, targetY),
                end = Offset(leftPad + chartW, targetY),
                strokeWidth = 1.8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
            )
            val targetLabelPaint = Paint().apply {
                color = targetLineColor.toArgb()
                textSize = 9.5.dp.toPx()
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                targetLineLabel,
                leftPad + 6.dp.toPx(),
                targetY - 4.dp.toPx(),
                targetLabelPaint
            )

            // 2. Vertikale Zeit-Gitterlinien & X-Achsen-Labels auf vollen oder halben Stunden
            val textPaint = Paint().apply {
                color = labelColor.toArgb()
                textSize = 11.dp.toPx()
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val stepMinutes = when {
                visibleHours <= 2.5f -> 30
                visibleHours <= 7.5f -> 60
                visibleHours <= 14f -> 120
                else -> 180
            }
            val tickCal = Calendar.getInstance().apply {
                timeInMillis = startTime
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                val minutesOfDay = get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
                val rem = minutesOfDay % stepMinutes
                if (rem != 0) {
                    add(Calendar.MINUTE, stepMinutes - rem)
                }
                if (timeInMillis < startTime) {
                    add(Calendar.MINUTE, stepMinutes)
                }
            }
            val minEdgeGapPx = 14.dp.toPx()
            while (tickCal.timeInMillis <= endTime) {
                val t = tickCal.timeInMillis
                val x = xForTime(t)
                drawLine(
                    color = verticalGridColor,
                    start = Offset(x, topPad),
                    end = Offset(x, topPad + chartH),
                    strokeWidth = 1.dp.toPx()
                )
                if (x >= leftPad + minEdgeGapPx && x <= leftPad + chartW - minEdgeGapPx) {
                    drawContext.canvas.nativeCanvas.drawText(
                        timeFormat.format(Date(t)),
                        x,
                        topPad + chartH + 18.dp.toPx(),
                        textPaint
                    )
                }
                tickCal.add(Calendar.MINUTE, stepMinutes)
            }

            // 3. Linke Y-Achse (Blutzucker mg/dl oder mmol/L)
            val leftLabelPaint = Paint().apply {
                color = labelColor.toArgb()
                textSize = 10.dp.toPx()
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            val bgStartTick = bgMinDisplay.roundToInt()
            val bgEndTick = bgMaxDisplay.roundToInt()
            for (bgTick in bgStartTick..bgEndTick step bgTickStepDisplay) {
                val y = yForBgDisplay(bgTick.toFloat())
                drawLine(
                    color = horizontalGridColor,
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + chartW, y),
                    strokeWidth = 1.dp.toPx()
                )
                drawContext.canvas.nativeCanvas.drawText(
                    bgTick.toString(),
                    leftPad - 6.dp.toPx(),
                    y + 4.dp.toPx(),
                    leftLabelPaint
                )
            }

            // Linker Achsentitel (vertikal)
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, 12.dp.toPx(), topPad + chartH / 2f)
            val axisTitlePaint = Paint().apply {
                color = labelColor.toArgb()
                textSize = 11.dp.toPx()
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                bgAxisTitle,
                12.dp.toPx(),
                topPad + chartH / 2f,
                axisTitlePaint
            )
            drawContext.canvas.nativeCanvas.restore()

            // 4. Rechte Y-Achse (Insulin IE)
            val rightLabelPaint = Paint().apply {
                color = IobCurveBlue.toArgb()
                textSize = 10.dp.toPx()
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }
            val insMaxInt = insMax.roundToInt().coerceAtLeast(10)
            val insStep = when {
                insMaxInt > 20 -> 5
                else -> 2
            }
            for (ieTick in 0..insMaxInt step insStep) {
                val y = yForIns(ieTick.toFloat())
                drawContext.canvas.nativeCanvas.drawText(
                    ieTick.toString(),
                    leftPad + chartW + 6.dp.toPx(),
                    y + 4.dp.toPx(),
                    rightLabelPaint
                )
            }
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, size.width - 10.dp.toPx(), topPad + chartH / 2f)
            val rightTitlePaint = Paint().apply {
                color = IobCurveBlue.toArgb()
                textSize = 11.dp.toPx()
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                insAxisTitle,
                size.width - 10.dp.toPx(),
                topPad + chartH / 2f,
                rightTitlePaint
            )
            drawContext.canvas.nativeCanvas.restore()

            // Innerhalb des Chart-Rahmens clippen
            clipRect(
                left = leftPad,
                top = topPad,
                right = leftPad + chartW,
                bottom = topPad + chartH
            ) {
                // 5. Exponentielle IOB-Fläche & Kurve (blau) + COB-Kurve (olivgrün)
                val iobPath = Path()
                val iobFillPath = Path()
                val cobPath = Path()
                val baseY = yForIns(0f)

                for (i in 0..sampleCount) {
                    val t = startTime + (i.toFloat() / sampleCount * timeSpan).toLong()
                    val x = xForTime(t)
                    val yIob = yForIns(iobSamples[i].coerceAtMost(insMax))
                    val yCob = yForIns(cobScaledSamples[i].coerceAtMost(insMax))

                    if (i == 0) {
                        iobPath.moveTo(x, yIob)
                        iobFillPath.moveTo(x, baseY)
                        iobFillPath.lineTo(x, yIob)
                        cobPath.moveTo(x, yCob)
                    } else {
                        iobPath.lineTo(x, yIob)
                        iobFillPath.lineTo(x, yIob)
                        cobPath.lineTo(x, yCob)
                    }
                    if (i == sampleCount) {
                        iobFillPath.lineTo(x, baseY)
                        iobFillPath.close()
                    }
                }

                drawPath(
                    path = iobFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(IobAreaBlue.copy(alpha = 0.18f), Color.Transparent),
                        startY = topPad + chartH * 0.35f,
                        endY = baseY
                    )
                )
                drawPath(
                    path = iobPath,
                    color = IobCurveBlue.copy(alpha = 0.50f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawPath(
                    path = cobPath,
                    color = CobCurveOlive.copy(alpha = 0.50f),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // 6. Feine Glukose-Punkte (Rot-Grün-freundliche Farben je nach Zielbereich)
                visibleReadings.forEach { reading ->
                    val x = xForTime(reading.timestampMillis)
                    val y = yForBgMgDl(reading.mgDl.toFloat())
                    val dotColor = when {
                        reading.mgDl < settings.targetRangeMin -> BgLowRed
                        reading.mgDl > settings.targetRangeMax -> BgHighOrange
                        else -> BgInRangeGreen
                    }
                    drawCircle(
                        color = dotColor,
                        radius = 1.8.dp.toPx(),
                        center = Offset(x, y)
                    )
                }

                // 7. Bolus- & KH-Marker (Dreiecke)
                val bolusLabelPaint = Paint().apply {
                    color = BolusMarkerBlue.toArgb()
                    textSize = 10.dp.toPx()
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                entries.filter { it.timestampMillis in startTime..endTime }.forEach { entry ->
                    val x = xForTime(entry.timestampMillis)
                    val bgRef = (entry.bgAtTimeMgDl ?: settings.targetBg).toFloat()
                    val yRef = yForBgMgDl(bgRef)

                    if (entry.type == DiaryEntryType.BOLUS && (entry.bolusUnits ?: 0.0) > 0.0) {
                        val triSize = 5.dp.toPx()
                        val triPath = Path().apply {
                            moveTo(x - triSize, yRef - 14.dp.toPx())
                            lineTo(x + triSize, yRef - 14.dp.toPx())
                            lineTo(x, yRef - 6.dp.toPx())
                            close()
                        }
                        drawPath(triPath, color = BolusMarkerBlue)
                        val unitsText = String.format(locale, "%.1f", entry.bolusUnits)
                        drawContext.canvas.nativeCanvas.drawText(
                            unitsText,
                            x,
                            yRef - 17.dp.toPx(),
                            bolusLabelPaint
                        )
                    } else if (entry.type == DiaryEntryType.CARBS && (entry.carbsGrams ?: 0.0) > 0.0) {
                        val triSize = 5.dp.toPx()
                        val triPath = Path().apply {
                            moveTo(x, yRef + 6.dp.toPx())
                            lineTo(x - triSize, yRef + 14.dp.toPx())
                            lineTo(x + triSize, yRef + 14.dp.toPx())
                            close()
                        }
                        drawPath(triPath, color = CarbsMarkerOrange)
                    }
                }

                // 8. Crosshair bei Antippen
                selectedTimeMillis?.let { selMs ->
                    if (selMs in startTime..endTime) {
                        val cx = xForTime(selMs)
                        drawLine(
                            color = labelColor.copy(alpha = 0.7f),
                            start = Offset(cx, topPad),
                            end = Offset(cx, topPad + chartH),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                    }
                }
            }

            // Achsen-Rahmenlinien
            drawLine(
                color = axisBorderColor,
                start = Offset(leftPad, topPad),
                end = Offset(leftPad, topPad + chartH),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = axisBorderColor,
                start = Offset(leftPad, topPad + chartH),
                end = Offset(leftPad + chartW, topPad + chartH),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = axisBorderColor,
                start = Offset(leftPad + chartW, topPad),
                end = Offset(leftPad + chartW, topPad + chartH),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Kompakte IOB / COB Legende oben links (+ Datumsanzeige wenn in den letzten 7 Tagen zurückgescrollt)
        val isPanned = panBackMillis > 15 * 60_000L
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 54.dp, top = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(IobCurveBlue))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("IOB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(CobCurveOlive))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("COB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
                if (isPanned) {
                    val centerMs = System.currentTimeMillis() - panBackMillis - (visibleHours * 1800_000f).toLong()
                    Text(
                        text = dateFormat.format(Date(centerMs)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // "Jetzt"-Reset-Chip oben rechts, wenn gezoomt oder gescrollt wurde
        val isModified = abs(visibleHours - windowHours.toFloat()) > 0.15f || panBackMillis > 60_000L
        if (isModified) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 48.dp, top = 20.dp)
                    .clickable {
                        visibleHours = windowHours.toFloat()
                        panBackMillis = 0L
                        selectedTimeMillis = null
                    }
            ) {
                Text(
                    text = resetLabel,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Crosshair-Tooltip oben mittig
        selectedTimeMillis?.let { selMs ->
            val nearest = readings.minByOrNull { abs(it.timestampMillis - selMs) }
                ?.takeIf { abs(it.timestampMillis - selMs) <= 20 * 60_000L }
            val displayMs = nearest?.timestampMillis ?: selMs
            val iobAtTime = IobCobCalculator.calculateExponentialIob(
                entries = entries,
                atTimeMillis = displayMs,
                diaHours = settings.diaHours,
                peakMinutes = settings.insulinType.peakMinutes
            )
            val bgPart = nearest?.let { "${GlucoseFormatter.formatValue(it.mgDl, unit, locale)} $unitLabel · " } ?: ""
            val timeStr = if (isPanned) dateTimeFormat.format(Date(displayMs)) else timeFormat.format(Date(displayMs))
            val label = "$timeStr · ${bgPart}IOB ${String.format(locale, "%.1f %s", iobAtTime, ieUnit)}"

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 2.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .clickable { selectedTimeMillis = null }
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
