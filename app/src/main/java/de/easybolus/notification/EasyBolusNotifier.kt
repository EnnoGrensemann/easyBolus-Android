package de.easybolus.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import de.easybolus.MainActivity
import de.easybolus.R
import de.easybolus.data.local.EasyBolusDatabase
import de.easybolus.data.local.TrendArrow
import de.easybolus.data.settings.SettingsRepository
import de.easybolus.domain.CorrectionAlertEvaluator
import de.easybolus.domain.CorrectionAlertState
import de.easybolus.domain.GlucoseFormatter
import de.easybolus.domain.IobCobCalculator
import de.easybolus.widget.EasyBolusWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

object EasyBolusNotifier {

    private const val CHANNEL_PERSISTENT = "easybolus_persistent_glucose"
    private const val CHANNEL_CORRECTION = "easybolus_correction_alert"
    private const val NOTIF_ID_PERSISTENT = 1001
    private const val NOTIF_ID_CORRECTION = 1002
    private const val PREFS_NAME = "easybolus_alert_state"
    private const val KEY_CONDITION_SINCE = "condition_since_ms"
    private const val KEY_LAST_ALERT = "last_alert_ms"

    private const val COLOR_IN_RANGE = 0xFF00A884.toInt()
    private const val COLOR_HIGH = 0xFFFFB300.toInt()
    private const val COLOR_LOW = 0xFFFF1744.toInt()
    private const val COLOR_MUTED = 0xFF9AA0A6.toInt()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val persistentChannel = NotificationChannel(
            CHANNEL_PERSISTENT,
            context.getString(R.string.notif_channel_persistent),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        val correctionChannel = NotificationChannel(
            CHANNEL_CORRECTION,
            context.getString(R.string.notif_channel_correction),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
        }
        nm.createNotificationChannel(persistentChannel)
        nm.createNotificationChannel(correctionChannel)
    }

    fun resetCorrectionTimerOnBolus(context: Context) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_CONDITION_SINCE, 0L)
            .putLong(KEY_LAST_ALERT, now)
            .apply()
    }

    fun refreshAllAsync(context: Context, evaluateCorrection: Boolean = false) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            refreshAll(context, evaluateCorrection)
        }
    }

    suspend fun refreshAll(context: Context, evaluateCorrection: Boolean = false) {
        ensureChannels(context)
        EasyBolusWidgetProvider.updateWidgetsNow(context)

        val settings = SettingsRepository(context).getCurrentSettings()
        val dao = EasyBolusDatabase.getInstance(context).dao()
        val now = System.currentTimeMillis()
        val latest = dao.getLatestGlucose()
        val recentReadings = dao.getGlucoseSince(now - 2L * 3600_000L)
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

        val nm = NotificationManagerCompat.from(context)
        val hasPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        // 1. Dauerhafte Benachrichtigung (wie GlucoDataHandler)
        if (!settings.persistentNotificationEnabled || !hasPerm) {
            nm.cancel(NOTIF_ID_PERSISTENT)
        } else {
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
            val bgColor = when {
                latest == null || isStaleCgm -> COLOR_MUTED
                latest.mgDl < settings.targetRangeMin -> COLOR_LOW
                latest.mgDl > settings.targetRangeMax -> COLOR_HIGH
                else -> COLOR_IN_RANGE
            }
            val deltaMgDl = latest?.let { cur ->
                val prev = recentReadings.lastOrNull { it.timestampMillis < cur.timestampMillis }
                if (prev != null && (cur.timestampMillis - prev.timestampMillis) <= 30 * 60_000L) {
                    cur.mgDl - prev.mgDl
                } else null
            }
            val deltaText = if (!isStaleCgm && deltaMgDl != null) {
                "Δ ${GlucoseFormatter.formatSignedDelta(deltaMgDl, unit, locale)}"
            } else {
                ""
            }
            val iobCobLine = String.format(locale, "IOB %.2f %s · COB %.1f g", iob, ieUnit, cob)

            val views = RemoteViews(context.packageName, R.layout.notification_glucose).apply {
                setTextViewText(R.id.notif_bg_value, bgText)
                setTextColor(R.id.notif_bg_value, bgColor)
                setTextViewText(R.id.notif_trend, trendText)
                setTextColor(R.id.notif_trend, bgColor)
                setTextViewText(R.id.notif_delta, deltaText)
                setTextViewText(R.id.notif_iob_cob, iobCobLine)
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                1,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val smallIcon = IconCompat.createWithBitmap(renderStatusBarValueBitmap(bgText))

            val notification = NotificationCompat.Builder(context, CHANNEL_PERSISTENT)
                .setSmallIcon(smallIcon)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(views)
                .setCustomBigContentView(views)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pi)
                .setWhen(latest?.timestampMillis ?: now)
                .setShowWhen(true)
                .build()

            try {
                nm.notify(NOTIF_ID_PERSISTENT, notification)
            } catch (_: SecurityException) {
            }
        }

        // 2. Korrektur-Benachrichtigung prüfen (wenn Mindest-Korrektur + Wartezeit erreicht)
        if (evaluateCorrection && latest != null && settings.correctionAlertEnabled && hasPerm) {
            val minsAgo = ((now - latest.timestampMillis) / 60_000L).coerceAtLeast(0L)
            if (minsAgo <= 15L) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val prevState = CorrectionAlertState(
                    conditionSinceMillis = prefs.getLong(KEY_CONDITION_SINCE, 0L),
                    lastAlertMillis = prefs.getLong(KEY_LAST_ALERT, 0L)
                )
                val decision = CorrectionAlertEvaluator.evaluate(
                    currentBgMgDl = latest.mgDl,
                    activeIob = iob,
                    settings = settings,
                    previousState = prevState,
                    nowMillis = now
                )
                prefs.edit()
                    .putLong(KEY_CONDITION_SINCE, decision.nextState.conditionSinceMillis)
                    .putLong(KEY_LAST_ALERT, decision.nextState.lastAlertMillis)
                    .apply()

                if (decision.shouldNotify) {
                    val locale = Locale.getDefault()
                    val unit = settings.glucoseUnit
                    val ieUnit = context.getString(R.string.unit_ie)
                    val bgStr = "${GlucoseFormatter.formatValue(latest.mgDl, unit, locale)} ${GlucoseFormatter.unitLabel(unit)}"
                    val recStr = String.format(locale, "%.1f %s", decision.recommendedCorrectionUnits, ieUnit)
                    val iobStr = String.format(locale, "%.2f %s", iob, ieUnit)

                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pi = PendingIntent.getActivity(
                        context,
                        2,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val alertNotif = NotificationCompat.Builder(context, CHANNEL_CORRECTION)
                        .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                        .setContentTitle(context.getString(R.string.notif_correction_title, recStr))
                        .setContentText(context.getString(R.string.notif_correction_body, bgStr, iobStr))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pi)
                        .build()

                    try {
                        nm.notify(NOTIF_ID_CORRECTION, alertNotif)
                    } catch (_: SecurityException) {
                    }
                }
            }
        }
    }

    private fun renderStatusBarValueBitmap(text: String): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when {
                text.length <= 2 -> 64f
                text.length == 3 -> 52f
                else -> 42f
            }
        }
        val fm = paint.fontMetrics
        val baseline = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, size / 2f, baseline, paint)
        return bmp
    }
}
