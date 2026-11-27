package de.easybolus.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import de.easybolus.R
import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.local.TrendArrow
import de.easybolus.data.settings.UserSettings
import de.easybolus.domain.GlucoseFormatter
import de.easybolus.domain.IobCobCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class CarDashboardState(
    val latestGlucose: GlucoseReadingEntity? = null,
    val deltaMgDl: Int? = null,
    val minutesAgo: Long? = null,
    val readings: List<GlucoseReadingEntity> = emptyList(),
    val entries: List<DiaryEntryEntity> = emptyList(),
    val currentIob: Double = 0.0,
    val currentCob: Double = 0.0,
    val settings: UserSettings = UserSettings()
)

object CarDashboardRenderer {

    private const val BG_DARK = 0xFF121316.toInt()
    private const val TEXT_PRIMARY = 0xFFF3F4F6.toInt()
    private const val TEXT_SECONDARY = 0xFF9CA3AF.toInt()
    private const val GRID_COLOR = 0xFF282A30.toInt()

    private const val COLOR_IN_RANGE = 0xFF10B981.toInt()
    private const val COLOR_HIGH = 0xFFF59E0B.toInt()
    private const val COLOR_LOW = 0xFFEF4444.toInt()
    private const val TARGET_BAND_FILL = 0x2910B981
    private const val TARGET_LINE_COLOR = 0x6610B981

    private const val PILL_IOB_BG = 0xFF2B6CB0.toInt()
    private const val PILL_COB_BG = 0xFF3E7B4F.toInt()
    private const val IOB_CURVE = 0xFF60A5FA.toInt()
    private const val IOB_AREA_TOP = 0x553B82F6
    private const val IOB_AREA_BOT = 0x053B82F6
    private const val COB_CURVE = 0xFFA3B84C.toInt()

    private const val CHART_HOURS = 4L

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        dpi: Int,
        visibleArea: Rect?,
        state: CarDashboardState,
        context: Context
    ) {
        canvas.drawColor(BG_DARK)
        if (width <= 0 || height <= 0) return

        val area = visibleArea?.takeIf { !it.isEmpty } ?: Rect(0, 0, width, height)
        val scale = if (dpi > 0) (dpi / 160f) else (min(width, height) / 480f).coerceIn(1.0f, 3.0f)
        fun dp(v: Float): Float = v * scale

        val padH = dp(24f)
        val padTop = dp(16f)
        val padBottom = dp(14f)

        val left = area.left + padH
        val right = area.right - padH
        val top = area.top + padTop
        val bottom = area.bottom - padBottom
        val contentW = (right - left).coerceAtLeast(100f)
        val contentH = (bottom - top).coerceAtLeast(100f)

        val locale = Locale.getDefault()
        val settings = state.settings
        val unit = settings.glucoseUnit
        val unitLabel = GlucoseFormatter.unitLabel(unit)
        val ieUnit = context.getString(R.string.unit_ie)

        val isStaleCgm = settings.jugglucoEnabled && (state.minutesAgo ?: 0L) > 15L
        val latestBg = state.latestGlucose?.mgDl
        val bgText = if (latestBg == null || isStaleCgm) "---" else GlucoseFormatter.formatValue(latestBg, unit, locale)
        val trend = state.latestGlucose?.trendArrow
        val arrowText = if (isStaleCgm || trend == null || trend == TrendArrow.UNKNOWN) "" else trend.symbol

        val statusColor = when {
            latestBg == null || isStaleCgm -> TEXT_SECONDARY
            latestBg < settings.targetRangeMin -> COLOR_LOW
            latestBg > settings.targetRangeMax -> COLOR_HIGH
            else -> COLOR_IN_RANGE
        }

        // --- Header-Bereich (oben ca. 44% der Höhe) ---
        val headerH = min(contentH * 0.46f, dp(170f))

        // Status-Punkt + "easyBOLUS"
        val dotRadius = dp(6f)
        val titleY = top + dp(14f)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColor }
        canvas.drawCircle(left + dotRadius, titleY - dp(4f), dotRadius, dotPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = dp(15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(context.getString(R.string.app_name), left + dotRadius * 2f + dp(8f), titleY, brandPaint)

        // Großer BZ-Wert + Trendpfeil + Einheit
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStaleCgm) TEXT_SECONDARY else TEXT_PRIMARY
            textSize = dp(62f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = -0.03f
        }
        val bgBaselineY = titleY + dp(62f)
        canvas.drawText(bgText, left, bgBaselineY, bgPaint)

        val bgWidth = bgPaint.measureText(bgText)
        if (arrowText.isNotEmpty()) {
            val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_PRIMARY
                textSize = dp(38f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(arrowText, left + bgWidth + dp(10f), bgBaselineY - dp(18f), arrowPaint)
        }
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = dp(14f)
        }
        canvas.drawText(unitLabel, left + bgWidth + dp(10f), bgBaselineY - dp(2f), unitPaint)

        // Untertitel: Delta & Alter
        val timeLabel = when (val mins = state.minutesAgo) {
            null -> if (settings.jugglucoEnabled) context.getString(R.string.waiting_for_juggluco) else context.getString(R.string.no_manual_reading)
            0L -> context.getString(R.string.just_now)
            in 1L..119L -> context.getString(R.string.minutes_ago, mins)
            else -> context.getString(R.string.hours_ago, mins / 60L)
        }
        val deltaText = if (!isStaleCgm && state.deltaMgDl != null) {
            "Δ ${GlucoseFormatter.formatSignedDelta(state.deltaMgDl, unit, locale)}"
        } else {
            null
        }
        val subtitle = if (deltaText != null) "$deltaText · $timeLabel" else timeLabel
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStaleCgm) COLOR_HIGH else TEXT_SECONDARY
            textSize = dp(15f)
        }
        canvas.drawText(subtitle, left, bgBaselineY + dp(22f), subPaint)

        // Rechts oben: IOB & COB Pill-Badges (unterhalb eines evtl. ActionStrips)
        val pillW = dp(148f)
        val pillH = dp(38f)
        val pillLeft = right - pillW
        val pillTop1 = top + dp(28f)
        val pillTop2 = pillTop1 + pillH + dp(10f)

        drawPill(
            canvas = canvas,
            rect = RectF(pillLeft, pillTop1, right, pillTop1 + pillH),
            bgColor = PILL_IOB_BG,
            text = "IOB  ${String.format(locale, "%.2f %s", state.currentIob, ieUnit)}",
            textSize = dp(15f),
            radius = dp(19f)
        )
        drawPill(
            canvas = canvas,
            rect = RectF(pillLeft, pillTop2, right, pillTop2 + pillH),
            bgColor = PILL_COB_BG,
            text = "COB  ${String.format(locale, "%.1f g", state.currentCob)}",
            textSize = dp(15f),
            radius = dp(19f)
        )

        // --- Unterer Bereich: 4h Glukose- & IOB/COB-Verlauf-Chart ---
        val chartTop = max(top + headerH + dp(8f), bgBaselineY + dp(34f))
        val chartBottom = bottom - dp(22f)
        val chartLeft = left
        val chartRight = right - dp(38f)
        val chartW = (chartRight - chartLeft).coerceAtLeast(50f)
        val chartH = (chartBottom - chartTop).coerceAtLeast(40f)

        val now = System.currentTimeMillis()
        val spanMs = CHART_HOURS * 3600_000L
        val startMs = now - spanMs
        val periodReadings = state.readings.filter { it.timestampMillis in startMs..now }.sortedBy { it.timestampMillis }

        val minBgMgDl = min(60f, (periodReadings.minOfOrNull { it.mgDl } ?: settings.targetRangeMin).toFloat() - 10f)
        val maxBgMgDl = max(220f, max(settings.targetRangeMax.toFloat() + 20f, (periodReadings.maxOfOrNull { it.mgDl } ?: 180).toFloat() + 15f))

        fun xForMs(ts: Long): Float = chartLeft + ((ts - startMs).toFloat() / spanMs.toFloat()).coerceIn(0f, 1f) * chartW
        fun yForMgDl(mgDl: Float): Float = chartBottom - ((mgDl - minBgMgDl) / (maxBgMgDl - minBgMgDl)).coerceIn(0f, 1f) * chartH

        // Zielbereichsband
        val yBandTop = yForMgDl(settings.targetRangeMax.toFloat())
        val yBandBot = yForMgDl(settings.targetRangeMin.toFloat())
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TARGET_BAND_FILL }
        canvas.drawRect(chartLeft, yBandTop, chartRight, yBandBot, bandPaint)

        // Gestrichelte Zielwert-Linie
        val targetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TARGET_LINE_COLOR
            strokeWidth = dp(1.2f)
            pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(5f)), 0f)
        }
        val yTarget = yForMgDl(settings.targetBg.toFloat())
        canvas.drawLine(chartLeft, yTarget, chartRight, yTarget, targetLinePaint)

        // Y-Achsen-Labels rechts (targetRangeMin & targetRangeMax)
        val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = dp(12f)
            textAlign = Paint.Align.LEFT
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GRID_COLOR
            strokeWidth = dp(1f)
        }
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, gridPaint)
        canvas.drawText(
            GlucoseFormatter.formatValue(settings.targetRangeMax, unit, locale),
            chartRight + dp(6f),
            yBandTop + dp(4f),
            axisLabelPaint
        )
        canvas.drawText(
            GlucoseFormatter.formatValue(settings.targetRangeMin, unit, locale),
            chartRight + dp(6f),
            yBandBot + dp(4f),
            axisLabelPaint
        )

        // X-Achse (stündliche Ticks)
        val timeFmt = SimpleDateFormat("HH:mm", locale)
        val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = dp(12f)
            textAlign = Paint.Align.CENTER
        }
        val cal = Calendar.getInstance().apply {
            timeInMillis = startMs
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, 1)
        }
        while (cal.timeInMillis <= now) {
            val x = xForMs(cal.timeInMillis)
            canvas.drawLine(x, chartTop, x, chartBottom, gridPaint)
            canvas.drawText(timeFmt.format(Date(cal.timeInMillis)), x, chartBottom + dp(16f), xLabelPaint)
            cal.add(Calendar.HOUR_OF_DAY, 1)
        }

        // IOB & COB Kurven am unteren Chart-Rand
        val steps = 48
        val iobVals = FloatArray(steps + 1)
        val cobVals = FloatArray(steps + 1)
        var maxIob = 2.0f
        var maxCob = 30.0f
        for (i in 0..steps) {
            val t = startMs + (i.toLong() * spanMs / steps)
            val iob = IobCobCalculator.calculateExponentialIob(state.entries, t, settings.diaHours, settings.insulinType.peakMinutes).toFloat()
            val cob = IobCobCalculator.calculateLinearCob(state.entries, t, settings.carbAbsorptionHours).toFloat()
            iobVals[i] = iob
            cobVals[i] = cob
            if (iob > maxIob) maxIob = iob
            if (cob > maxCob) maxCob = cob
        }
        val subCurveMaxH = chartH * 0.28f

        if (iobVals.any { it > 0.01f }) {
            val iobLinePath = Path()
            val iobFillPath = Path()
            for (i in 0..steps) {
                val x = chartLeft + (i.toFloat() / steps) * chartW
                val y = chartBottom - (iobVals[i] / maxIob).coerceIn(0f, 1f) * subCurveMaxH
                if (i == 0) {
                    iobLinePath.moveTo(x, y)
                    iobFillPath.moveTo(x, chartBottom)
                    iobFillPath.lineTo(x, y)
                } else {
                    iobLinePath.lineTo(x, y)
                    iobFillPath.lineTo(x, y)
                }
            }
            iobFillPath.lineTo(chartLeft + chartW, chartBottom)
            iobFillPath.close()

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, chartBottom - subCurveMaxH, 0f, chartBottom, IOB_AREA_TOP, IOB_AREA_BOT, Shader.TileMode.CLAMP)
            }
            canvas.drawPath(iobFillPath, fillPaint)

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = IOB_CURVE
                style = Paint.Style.STROKE
                strokeWidth = dp(1.8f)
            }
            canvas.drawPath(iobLinePath, strokePaint)
        }

        if (cobVals.any { it > 0.5f }) {
            val cobPath = Path()
            for (i in 0..steps) {
                val x = chartLeft + (i.toFloat() / steps) * chartW
                val y = chartBottom - (cobVals[i] / maxCob).coerceIn(0f, 1f) * subCurveMaxH
                if (i == 0) cobPath.moveTo(x, y) else cobPath.lineTo(x, y)
            }
            val cobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COB_CURVE
                style = Paint.Style.STROKE
                strokeWidth = dp(1.8f)
            }
            canvas.drawPath(cobPath, cobPaint)
        }

        // Glukose-Punkte (farbcodiert)
        val dotR = dp(3.0f)
        val ptPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (r in periodReadings) {
            ptPaint.color = when {
                r.mgDl < settings.targetRangeMin -> COLOR_LOW
                r.mgDl > settings.targetRangeMax -> COLOR_HIGH
                else -> COLOR_IN_RANGE
            }
            canvas.drawCircle(xForMs(r.timestampMillis), yForMgDl(r.mgDl.toFloat()), dotR, ptPaint)
        }
    }

    private fun drawPill(
        canvas: Canvas,
        rect: RectF,
        bgColor: Int,
        text: String,
        textSize: Float,
        radius: Float
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textY = rect.centerY() - (txtPaint.descent() + txtPaint.ascent()) / 2f
        canvas.drawText(text, rect.centerX(), textY, txtPaint)
    }
}
