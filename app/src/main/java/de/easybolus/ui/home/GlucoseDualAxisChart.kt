package de.easybolus.ui.home

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.settings.UserSettings
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun GlucoseDualAxisChart(
    readings: List<GlucoseReadingEntity>,
    entries: List<DiaryEntryEntity>,
    settings: UserSettings,
    windowHours: Int = 6,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }
    var visibleHours by remember(windowHours) { mutableFloatStateOf(windowHours.toFloat()) }
    var panBackMillis by remember { mutableLongStateOf(0L) }

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val verticalGridColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val horizontalGridColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val axisBorderColor = if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newHours = (visibleHours / zoom).coerceIn(1f, 24f)
                    visibleHours = newHours

                    val maxPanMillis = ((24f - newHours).coerceAtLeast(0f) * 3600_000L).toLong()
                    if (pan.x != 0f && size.width > 0) {
                        val msPerPx = (newHours * 3600_000f) / size.width.toFloat()
                        val deltaMs = (pan.x * msPerPx).toLong()
                        panBackMillis = (panBackMillis + deltaMs).coerceIn(0L, maxPanMillis)
                    } else {
                        panBackMillis = panBackMillis.coerceIn(0L, maxPanMillis)
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

        // Dynamische linke Y-Achse (Blutzucker mg/dl) auf 50er-Schritte gerundet
        val minReading = visibleReadings.minOfOrNull { it.mgDl } ?: settings.targetRangeMin
        val maxReading = visibleReadings.maxOfOrNull { it.mgDl } ?: settings.targetRangeMax
        val rawMin = min(minReading, settings.targetRangeMin).toFloat()
        val rawMax = max(maxReading, settings.targetRangeMax).toFloat()
        val bgMin = (floor((rawMin - 15f) / 50f) * 50f).coerceAtLeast(0f)
        val bgMax = (ceil((rawMax + 15f) / 50f) * 50f).coerceAtLeast(bgMin + 100f)
        val bgSpan = (bgMax - bgMin).coerceAtLeast(50f)

        // IOB & COB vorab samplen für dynamische rechte Y-Achse
        val sampleCount = 72
        val iobSamples = FloatArray(sampleCount + 1)
        val cobScaledSamples = FloatArray(sampleCount + 1)
        var maxRightVal = 0f
        for (i in 0..sampleCount) {
            val t = startTime + (i.toFloat() / sampleCount * timeSpan).toLong()
            val iob = IobCobCalculator.calculateLinearIob(entries, t, settings.diaHours).toFloat()
            val cob = IobCobCalculator.calculateLinearCob(entries, t, settings.carbAbsorptionHours).toFloat()
            val cobScaled = cob / 40f
            iobSamples[i] = iob
            cobScaledSamples[i] = cobScaled
            maxRightVal = max(maxRightVal, max(iob, cobScaled))
        }

        val insMin = 0f
        val insMax = max(2f, ceil(maxRightVal * 1.15f))

        fun xForTime(t: Long): Float =
            leftPad + ((t - startTime).toFloat() / timeSpan) * chartW

        fun yForBg(bg: Float): Float =
            topPad + chartH - ((bg - bgMin) / bgSpan).coerceIn(0f, 1f) * chartH

        fun yForIns(ie: Float): Float =
            topPad + chartH - ((ie - insMin) / (insMax - insMin)).coerceIn(0f, 1f) * chartH

        // 1. Grünes Zielbereich-Band
        val bandTop = yForBg(settings.targetRangeMax.toFloat())
        val bandBottom = yForBg(settings.targetRangeMin.toFloat())
        drawRect(
            color = TargetBandGreen,
            topLeft = Offset(leftPad, bandTop),
            size = Size(chartW, (bandBottom - bandTop).coerceAtLeast(0f))
        )

        // 2. Vertikale Zeit-Gitterlinien & X-Achsen-Labels (dynamische Schrittweite beim Zoomen)
        val textPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 11.dp.toPx()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val tickSteps = 6
        for (i in 0..tickSteps) {
            val t = startTime + (i.toFloat() / tickSteps * timeSpan).toLong()
            val x = xForTime(t)
            drawLine(
                color = verticalGridColor,
                start = Offset(x, topPad),
                end = Offset(x, topPad + chartH),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                timeFormat.format(Date(t)),
                x,
                topPad + chartH + 18.dp.toPx(),
                textPaint
            )
        }

        // 3. Linke Y-Achse (Blutzucker mg/dl)
        val leftLabelPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.dp.toPx()
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val bgStartTick = bgMin.roundToInt()
        val bgEndTick = bgMax.roundToInt()
        for (bgTick in bgStartTick..bgEndTick step 50) {
            val y = yForBg(bgTick.toFloat())
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
            "Blutzucker (mg/dl)",
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
        val insMaxInt = insMax.roundToInt().coerceAtLeast(2)
        val insStep = when {
            insMaxInt > 10 -> 2
            else -> 1
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
            "Insulin (IE)",
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
            // 5. IOB-Fläche & Kurve (blau) + COB-Kurve (olivgrün)
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
                    colors = listOf(IobAreaBlue, Color.Transparent),
                    startY = topPad + chartH * 0.35f,
                    endY = baseY
                )
            )
            drawPath(
                path = iobPath,
                color = IobCurveBlue,
                style = Stroke(width = 1.8.dp.toPx())
            )
            drawPath(
                path = cobPath,
                color = CobCurveOlive,
                style = Stroke(width = 1.8.dp.toPx())
            )

            // 6. Glukose-Punkte (farbig je nach Zielbereich)
            visibleReadings.forEach { reading ->
                val x = xForTime(reading.timestampMillis)
                val y = yForBg(reading.mgDl.toFloat())
                val dotColor = when {
                    reading.mgDl < settings.targetRangeMin -> BgLowRed
                    reading.mgDl > settings.targetRangeMax -> BgHighOrange
                    else -> BgInRangeGreen
                }
                drawCircle(
                    color = dotColor,
                    radius = 3.dp.toPx(),
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
                val yRef = yForBg(bgRef)

                if (entry.type == DiaryEntryType.BOLUS && (entry.bolusUnits ?: 0.0) > 0.0) {
                    val triSize = 5.dp.toPx()
                    val triPath = Path().apply {
                        moveTo(x - triSize, yRef - 14.dp.toPx())
                        lineTo(x + triSize, yRef - 14.dp.toPx())
                        lineTo(x, yRef - 6.dp.toPx())
                        close()
                    }
                    drawPath(triPath, color = BolusMarkerBlue)
                    val unitsText = String.format(Locale.GERMANY, "%.1f", entry.bolusUnits)
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
}
