package de.easybolus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import de.easybolus.MainActivity
import de.easybolus.R
import de.easybolus.data.local.EasyBolusDatabase
import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.local.TrendArrow
import de.easybolus.data.settings.GlucoseUnit
import de.easybolus.data.settings.SettingsRepository
import de.easybolus.data.settings.UserSettings
import de.easybolus.domain.GlucoseFormatter
import de.easybolus.domain.IobCobCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class EasyBolusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updateWidgetsNow(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val COLOR_IN_RANGE = 0xFF00A884.toInt()
        private const val COLOR_HIGH = 0xFFFFB300.toInt()
        private const val COLOR_LOW = 0xFFFF1744.toInt()
        private const val COLOR_MUTED = 0xFF9AA0A6.toInt()

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EasyBolusWidgetProvider::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                updateWidgetsNow(context, manager, ids)
            }
        }

        suspend fun updateWidgetsNow(
            context: Context,
            manager: AppWidgetManager = AppWidgetManager.getInstance(context),
            widgetIds: IntArray = manager.getAppWidgetIds(ComponentName(context, EasyBolusWidgetProvider::class.java))
        ) {
            if (widgetIds.isEmpty()) return

            val dao = EasyBolusDatabase.getInstance(context).dao()
            val settings = SettingsRepository(context).getCurrentSettings()
            val now = System.currentTimeMillis()
            val latest = dao.getLatestGlucose()
            val readings = dao.getGlucoseSince(now - 6L * 3600_000L)
            val entries = dao.getAllDiaryEntries()

            val iob = IobCobCalculator.calculateExponentialIob(
                entries = entries,
                atTimeMillis = now,
                diaHours = settings.diaHours,
                peakMinutes = settings.insulinType.peakMinutes
            )
            val cob = IobCobCalculator.calculateLinearCob(
                entries = entries,
                atTimeMillis = now,
                absorptionHours = settings.carbAbsorptionHours
            )

            val locale = Locale.getDefault()
            val unit = settings.glucoseUnit
            val unitLabel = GlucoseFormatter.unitLabel(unit)
            val ieUnit = context.getString(R.string.unit_ie)

            val minsAgo = latest?.let { ((now - it.timestampMillis) / 60_000L).coerceAtLeast(0L) }
            val isStaleCgm = settings.jugglucoEnabled && (minsAgo ?: 0L) > 15L

            val bgText = when {
                latest == null || isStaleCgm -> "---"
                else -> GlucoseFormatter.formatValue(latest.mgDl, unit, locale)
            }
            val trendText = when {
                latest == null || isStaleCgm || latest.trendArrow == TrendArrow.UNKNOWN -> ""
                else -> latest.trendArrow.symbol
            }
            val dotColor = when {
                latest == null || isStaleCgm -> COLOR_MUTED
                latest.mgDl < settings.targetRangeMin -> COLOR_LOW
                latest.mgDl > settings.targetRangeMax -> COLOR_HIGH
                else -> COLOR_IN_RANGE
            }

            val deltaMgDl = latest?.let { cur ->
                val prev = readings.lastOrNull { it.timestampMillis < cur.timestampMillis }
                if (prev != null && (cur.timestampMillis - prev.timestampMillis) <= 30 * 60_000L) {
                    cur.mgDl - prev.mgDl
                } else null
            }
            val timeStr = when (minsAgo) {
                null -> if (settings.jugglucoEnabled) {
                    context.getString(R.string.waiting_for_juggluco)
                } else {
                    context.getString(R.string.no_manual_reading)
                }
                0L -> context.getString(R.string.just_now)
                in 1L..119L -> context.getString(R.string.minutes_ago, minsAgo)
                else -> context.getString(R.string.hours_ago, minsAgo / 60L)
            }
            val subtitle = if (!isStaleCgm && deltaMgDl != null) {
                "${GlucoseFormatter.formatSignedDelta(deltaMgDl, unit, locale)} $unitLabel · $timeStr"
            } else {
                timeStr
            }

            val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val chartBitmap = renderMiniChartBitmap(
                readings = readings,
                settings = settings,
                nowMillis = now,
                isDark = isNight
            )

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            for (appWidgetId in widgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_easybolus).apply {
                    setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                    setTextViewText(R.id.widget_bg_value, bgText)
                    setTextViewText(R.id.widget_trend, trendText)
                    setTextViewText(R.id.widget_unit, unitLabel)
                    setTextColor(R.id.widget_status_dot, dotColor)
                    setTextViewText(R.id.widget_subtitle, subtitle)
                    setTextViewText(R.id.widget_iob, String.format(locale, "%.2f %s", iob, ieUnit))
                    setTextViewText(R.id.widget_cob, String.format(locale, "%.1f g", cob))
                    setImageViewBitmap(R.id.widget_chart_image, chartBitmap)
                }
                manager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun renderMiniChartBitmap(
            readings: List<GlucoseReadingEntity>,
            settings: UserSettings,
            nowMillis: Long,
            isDark: Boolean
        ): Bitmap {
            val width = 420
            val height = 220
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val leftPad = 48f
            val rightPad = 12f
            val topPad = 14f
            val bottomPad = 30f
            val chartW = width - leftPad - rightPad
            val chartH = height - topPad - bottomPad

            val spanMs = 5L * 3600_000L
            val startMs = nowMillis - spanMs
            val visible = readings.filter { it.timestampMillis in startMs..nowMillis }

            val unit = settings.glucoseUnit
            val maxReadingMgDl = visible.maxOfOrNull { it.mgDl } ?: settings.targetRangeMax
            val rawMaxMgDl = max(maxReadingMgDl, settings.targetRangeMax).toFloat()

            val bgMinDisplay = 0f
            val bgMaxDisplay: Float
            val bgStep: Int
            if (unit == GlucoseUnit.MMOL_L) {
                val maxMmol = GlucoseFormatter.mgDlToMmol(rawMaxMgDl.toDouble()).toFloat()
                bgMaxDisplay = max(15f, ceil((maxMmol + 1f) / 3f) * 3f)
                bgStep = 3
            } else {
                bgMaxDisplay = max(250f, ceil((rawMaxMgDl + 15f) / 50f) * 50f)
                bgStep = 50
            }

            fun toDisplay(mgDl: Float): Float = when (unit) {
                GlucoseUnit.MG_DL -> mgDl
                GlucoseUnit.MMOL_L -> GlucoseFormatter.mgDlToMmol(mgDl.toDouble()).toFloat()
            }

            fun xFor(t: Long): Float = leftPad + ((t - startMs).toFloat() / spanMs) * chartW
            fun yForDisplay(v: Float): Float = topPad + chartH - ((v - bgMinDisplay) / (bgMaxDisplay - bgMinDisplay)).coerceIn(0f, 1f) * chartH
            fun yForMgDl(v: Float): Float = yForDisplay(toDisplay(v))

            val axisColor = if (isDark) 0xFF5F6368.toInt() else 0xFFDADCE0.toInt()
            val textColor = if (isDark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
            val bandColor = if (isDark) 0x3300A884 else 0x3000A884
            val targetColor = if (isDark) 0xBBD0E8F2.toInt() else 0xAA0F4C5C.toInt()

            // Zielbereich-Band
            val bandPaint = Paint().apply { color = bandColor }
            canvas.drawRect(
                leftPad,
                yForMgDl(settings.targetRangeMax.toFloat()),
                leftPad + chartW,
                yForMgDl(settings.targetRangeMin.toFloat()),
                bandPaint
            )

            // Gestrichelte Zielwert-Linie
            val targetPaint = Paint().apply {
                color = targetColor
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
                isAntiAlias = true
                pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
            }
            val targetY = yForMgDl(settings.targetBg.toFloat())
            canvas.drawLine(leftPad, targetY, leftPad + chartW, targetY, targetPaint)

            // Achsen & Labels
            val axisPaint = Paint().apply {
                color = axisColor
                strokeWidth = 2f
                isAntiAlias = true
            }
            canvas.drawLine(leftPad, topPad, leftPad, topPad + chartH, axisPaint)
            canvas.drawLine(leftPad, topPad + chartH, leftPad + chartW, topPad + chartH, axisPaint)

            val yLabelPaint = Paint().apply {
                color = textColor
                textSize = 18f
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            for (tick in bgStep..bgMaxDisplay.roundToInt() step bgStep) {
                val y = yForDisplay(tick.toFloat())
                canvas.drawText(tick.toString(), leftPad - 8f, y + 6f, yLabelPaint)
            }

            // Volle Stunden auf X-Achse
            val xLabelPaint = Paint().apply {
                color = textColor
                textSize = 17f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val hourFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val cal = Calendar.getInstance().apply {
                timeInMillis = startMs
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis < startMs) add(Calendar.HOUR_OF_DAY, 1)
            }
            while (cal.timeInMillis <= nowMillis) {
                val x = xFor(cal.timeInMillis)
                if (x >= leftPad + 22f && x <= leftPad + chartW - 22f) {
                    canvas.drawText(hourFmt.format(Date(cal.timeInMillis)), x, height - 6f, xLabelPaint)
                }
                cal.add(Calendar.HOUR_OF_DAY, 1)
            }

            // Glukose-Verlauf (feine Linie + farbige Punkte)
            if (visible.size >= 2) {
                val linePath = Path()
                visible.forEachIndexed { idx, r ->
                    val x = xFor(r.timestampMillis)
                    val y = yForMgDl(r.mgDl.toFloat())
                    if (idx == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }
                val linePaint = Paint().apply {
                    color = COLOR_IN_RANGE
                    strokeWidth = 4f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(linePath, linePaint)
            }

            val dotPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            visible.forEach { r ->
                dotPaint.color = when {
                    r.mgDl < settings.targetRangeMin -> COLOR_LOW
                    r.mgDl > settings.targetRangeMax -> COLOR_HIGH
                    else -> COLOR_IN_RANGE
                }
                canvas.drawCircle(xFor(r.timestampMillis), yForMgDl(r.mgDl.toFloat()), 3.5f, dotPaint)
            }

            return bmp
        }
    }
}
