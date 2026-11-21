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
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.SizeF
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updateWidgetsNow(context, appWidgetManager, intArrayOf(appWidgetId))
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
            val deltaPart = if (!isStaleCgm && deltaMgDl != null) {
                "Δ ${GlucoseFormatter.formatSignedDelta(deltaMgDl, unit, locale)}"
            } else {
                null
            }

            val timeStrFull = when (minsAgo) {
                null -> if (settings.jugglucoEnabled) {
                    context.getString(R.string.waiting_for_juggluco)
                } else {
                    context.getString(R.string.no_manual_reading)
                }
                0L -> context.getString(R.string.just_now)
                in 1L..119L -> context.getString(R.string.minutes_ago, minsAgo)
                else -> context.getString(R.string.hours_ago, minsAgo / 60L)
            }
            val timeStrCompact = when (minsAgo) {
                null -> "--"
                0L -> context.getString(R.string.just_now)
                in 1L..119L -> "${minsAgo}m"
                else -> "${minsAgo / 60L}h"
            }

            val subtitleFull = if (deltaPart != null) "$deltaPart · $timeStrFull" else timeStrFull
            val subtitleCompact = if (deltaPart != null) "$deltaPart · $timeStrCompact" else timeStrCompact

            val iobStr = String.format(locale, "%.2f %s", iob, ieUnit)
            val cobStr = String.format(locale, "%.1f g", cob)

            val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val bgAlpha = ((100 - settings.widgetTransparencyPercent.coerceIn(0, 100)) * 255 / 100)
                .coerceIn(0, 255)

            fun buildRemoteViews(layoutRes: Int, subtitle: String, chart: Bitmap): RemoteViews {
                return RemoteViews(context.packageName, layoutRes).apply {
                    setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                    setInt(R.id.widget_bg_image, "setImageAlpha", bgAlpha)
                    setTextViewText(R.id.widget_bg_value, bgText)
                    setTextViewText(R.id.widget_trend, trendText)
                    setTextColor(R.id.widget_status_dot, dotColor)
                    setTextViewText(R.id.widget_subtitle, subtitle)
                    setTextViewText(R.id.widget_iob, iobStr)
                    setTextViewText(R.id.widget_cob, cobStr)
                    setImageViewBitmap(R.id.widget_chart_image, chart)
                }
            }

            for (appWidgetId in widgetIds) {
                val options = manager.getAppWidgetOptions(appWidgetId)
                val widgetW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 340)
                    .takeIf { it > 0 } ?: 340
                val widgetH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 92)
                    .takeIf { it > 0 } ?: 92

                val chartWidthDp = ((widgetW - 31f) * (1.25f / 2.30f)).coerceIn(115f, 320f)
                val bmpWidth = 520

                val ultraHeightDp = (if (widgetH in 40..71) widgetH else 56) - 8f
                val compactHeightDp = (if (widgetH in 72..134) widgetH else 92) - 10f
                val regularHeightDp = (if (widgetH >= 135) widgetH else 175) - 16f

                val ultraBmpH = (bmpWidth * (ultraHeightDp / chartWidthDp)).roundToInt().coerceIn(135, 220)
                val compactBmpH = (bmpWidth * (compactHeightDp / chartWidthDp)).roundToInt().coerceIn(210, 320)
                val regularBmpH = (bmpWidth * (regularHeightDp / chartWidthDp)).roundToInt().coerceIn(330, 500)

                val ultraChart = renderMiniChartBitmap(
                    readings = readings,
                    settings = settings,
                    nowMillis = now,
                    isDark = isNight,
                    width = bmpWidth,
                    height = ultraBmpH
                )
                val compactChart = renderMiniChartBitmap(
                    readings = readings,
                    settings = settings,
                    nowMillis = now,
                    isDark = isNight,
                    width = bmpWidth,
                    height = compactBmpH
                )
                val regularChart = renderMiniChartBitmap(
                    readings = readings,
                    settings = settings,
                    nowMillis = now,
                    isDark = isNight,
                    width = bmpWidth,
                    height = regularBmpH
                )

                val ultraCompactViews = buildRemoteViews(
                    R.layout.widget_easybolus_ultra_compact,
                    subtitleCompact,
                    ultraChart
                )
                val compactViews = buildRemoteViews(
                    R.layout.widget_easybolus_compact,
                    subtitleFull,
                    compactChart
                )
                val regularViews = buildRemoteViews(
                    R.layout.widget_easybolus,
                    subtitleFull,
                    regularChart
                )

                val finalViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    RemoteViews(
                        mapOf(
                            SizeF(180f, 40f) to ultraCompactViews,
                            SizeF(180f, 72f) to compactViews,
                            SizeF(180f, 135f) to regularViews
                        )
                    )
                } else {
                    when {
                        widgetH in 1..71 -> ultraCompactViews
                        widgetH in 72..134 -> compactViews
                        else -> regularViews
                    }
                }
                manager.updateAppWidget(appWidgetId, finalViews)
            }
        }

        private fun renderMiniChartBitmap(
            readings: List<GlucoseReadingEntity>,
            settings: UserSettings,
            nowMillis: Long,
            isDark: Boolean,
            width: Int,
            height: Int
        ): Bitmap {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Proportionale, unverzerrte Ränder & Schrift (fitCenter)
            val leftPad = 6f
            val rightPad = 6f
            val topPad = 10f
            val labelSize = 24f
            val bottomPad = 38f
            val chartW = width - leftPad - rightPad
            val chartH = (height - topPad - bottomPad).coerceAtLeast(40f)
            val axisY = topPad + chartH

            val spanMs = 5L * 3600_000L
            val startMs = nowMillis - spanMs
            val visible = readings.filter { it.timestampMillis in startMs..nowMillis }

            val unit = settings.glucoseUnit
            val maxReadingMgDl = visible.maxOfOrNull { it.mgDl } ?: settings.targetRangeMax
            val rawMaxMgDl = max(maxReadingMgDl, settings.targetRangeMax).toFloat()

            val bgMinDisplay = 0f
            val bgMaxDisplay: Float = if (unit == GlucoseUnit.MMOL_L) {
                val maxMmol = GlucoseFormatter.mgDlToMmol(rawMaxMgDl.toDouble()).toFloat()
                max(15f, ceil((maxMmol + 1f) / 3f) * 3f)
            } else {
                max(250f, ceil((rawMaxMgDl + 15f) / 50f) * 50f)
            }

            fun toDisplay(mgDl: Float): Float = when (unit) {
                GlucoseUnit.MG_DL -> mgDl
                GlucoseUnit.MMOL_L -> GlucoseFormatter.mgDlToMmol(mgDl.toDouble()).toFloat()
            }

            fun xFor(t: Long): Float = leftPad + ((t - startMs).toFloat() / spanMs) * chartW
            fun yForDisplay(v: Float): Float =
                topPad + chartH - ((v - bgMinDisplay) / (bgMaxDisplay - bgMinDisplay)).coerceIn(0f, 1f) * chartH
            fun yForMgDl(v: Float): Float = yForDisplay(toDisplay(v))

            val axisColor = if (isDark) 0x66FFFFFF else 0x33000000
            val textColor = if (isDark) 0xCCFFFFFF.toInt() else 0x991F1F1F.toInt()
            val bandColor = if (isDark) 0x3300A884 else 0x3000A884
            val targetColor = if (isDark) 0xBBD0E8F2.toInt() else 0xAA0F4C5C.toInt()

            // Zielbereich-Band über die volle Breite
            val bandPaint = Paint().apply { color = bandColor }
            canvas.drawRect(
                leftPad,
                yForMgDl(settings.targetRangeMax.toFloat()),
                leftPad + chartW,
                yForMgDl(settings.targetRangeMin.toFloat()),
                bandPaint
            )

            // Gestrichelte Zielwert-Linie über die volle Breite
            val targetPaint = Paint().apply {
                color = targetColor
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
                isAntiAlias = true
                pathEffect = DashPathEffect(floatArrayOf(12f, 9f), 0f)
            }
            val targetY = yForMgDl(settings.targetBg.toFloat())
            canvas.drawLine(leftPad, targetY, leftPad + chartW, targetY, targetPaint)

            // Klare, scharfe X-Achse + dezente Stunden-Ticks
            val axisPaint = Paint().apply {
                color = axisColor
                strokeWidth = 2f
                isAntiAlias = true
            }
            canvas.drawLine(leftPad, axisY, leftPad + chartW, axisY, axisPaint)

            val xLabelPaint = Paint().apply {
                color = textColor
                textSize = labelSize
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
                isSubpixelText = true
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
                if (x >= leftPad + 34f && x <= leftPad + chartW - 34f) {
                    canvas.drawLine(x, axisY, x, axisY + 5f, axisPaint)
                    canvas.drawText(hourFmt.format(Date(cal.timeInMillis)), x, height - 6f, xLabelPaint)
                }
                cal.add(Calendar.HOUR_OF_DAY, 1)
            }

            // Glukose-Verlauf (Linie + farbige Punkte)
            if (visible.size >= 2) {
                val linePath = Path()
                visible.forEachIndexed { idx, r ->
                    val x = xFor(r.timestampMillis)
                    val y = yForMgDl(r.mgDl.toFloat())
                    if (idx == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }
                val linePaint = Paint().apply {
                    color = COLOR_IN_RANGE
                    strokeWidth = 5f
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
                canvas.drawCircle(xFor(r.timestampMillis), yForMgDl(r.mgDl.toFloat()), 4.5f, dotPaint)
            }

            return bmp
        }
    }
}
