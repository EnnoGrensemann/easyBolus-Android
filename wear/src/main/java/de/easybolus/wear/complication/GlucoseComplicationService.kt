package de.easybolus.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.easybolus.wear.ComplicationIconRenderer
import de.easybolus.wear.R
import de.easybolus.wear.WearGlucoseSnapshot
import de.easybolus.wear.WearGlucoseStore
import de.easybolus.wear.WearMainActivity
import java.util.Locale

/**
 * Watchface-Complication „Blutzucker & Trend“ für Wear OS (z. B. Samsung Galaxy Watch 6).
 * Unterstützt SHORT_TEXT, LONG_TEXT, SMALL_IMAGE (farbiges BZ+Trend-Icon wie GlucoDataHandler)
 * und RANGED_VALUE (Fortschrittsbogen).
 */
class GlucoseComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val sample = WearGlucoseSnapshot(
            hasData = true,
            mgDl = 114,
            trendSymbol = "→",
            trendName = "FLAT",
            hasDelta = true,
            deltaMgDl = 2,
            readingTimestampMillis = System.currentTimeMillis(),
            iob = 2.1,
            cob = 35.0
        )
        return buildComplicationData(type, sample, System.currentTimeMillis(), tapAction = null)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snapshot = WearGlucoseStore.load(applicationContext)
        val now = System.currentTimeMillis()
        val tapAction = createLaunchPendingIntent()
        return buildComplicationData(request.complicationType, snapshot, now, tapAction)
    }

    private fun buildComplicationData(
        type: ComplicationType,
        snapshot: WearGlucoseSnapshot,
        nowMillis: Long,
        tapAction: PendingIntent?
    ): ComplicationData? {
        val locale = Locale.getDefault()
        val ieUnit = getString(R.string.unit_ie)
        val bgText = snapshot.formattedBg(nowMillis, locale)
        val trend = snapshot.activeTrendSymbol(nowMillis)
        val bgWithTrend = snapshot.formattedBgWithTrend(nowMillis, locale)
        val delta = snapshot.formattedDelta(nowMillis, locale)
        val contentDesc = PlainComplicationText.Builder(
            "${getString(R.string.complication_glucose_cd)}: $bgWithTrend"
        ).build()

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                val builder = ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(bgWithTrend).build(),
                    contentDescription = contentDesc
                )
                if (!delta.isNullOrEmpty()) {
                    builder.setTitle(PlainComplicationText.Builder(delta).build())
                }
                if (tapAction != null) builder.setTapAction(tapAction)
                builder.build()
            }

            ComplicationType.LONG_TEXT -> {
                val mainLine = if (!delta.isNullOrEmpty()) "$bgWithTrend  $delta" else bgWithTrend
                val subLine = String.format(locale, "IOB %.1f %s · COB %.0f g", snapshot.iob, ieUnit, snapshot.cob)
                val builder = LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(mainLine).build(),
                    contentDescription = contentDesc
                ).setTitle(PlainComplicationText.Builder(subLine).build())
                if (tapAction != null) builder.setTapAction(tapAction)
                builder.build()
            }

            ComplicationType.SMALL_IMAGE -> {
                val color = snapshot.statusColorArgb(nowMillis)
                val activeIcon = ComplicationIconRenderer.createGlucoseIcon(
                    bgText = bgText,
                    trendSymbol = trend,
                    colorArgb = color,
                    ambient = false
                )
                val ambientIcon = ComplicationIconRenderer.createGlucoseIcon(
                    bgText = bgText,
                    trendSymbol = trend,
                    colorArgb = color,
                    ambient = true
                )
                val smallImage = SmallImage.Builder(activeIcon, SmallImageType.ICON)
                    .setAmbientImage(ambientIcon)
                    .build()
                val builder = SmallImageComplicationData.Builder(
                    smallImage = smallImage,
                    contentDescription = contentDesc
                )
                if (tapAction != null) builder.setTapAction(tapAction)
                builder.build()
            }

            ComplicationType.RANGED_VALUE -> {
                val isStale = snapshot.isStale(nowMillis)
                val clampedBg = if (isStale) 40f else snapshot.mgDl.toFloat().coerceIn(40f, 300f)
                val builder = RangedValueComplicationData.Builder(
                    value = clampedBg,
                    min = 40f,
                    max = 300f,
                    contentDescription = contentDesc
                ).setText(PlainComplicationText.Builder(bgWithTrend).build())
                if (!delta.isNullOrEmpty()) {
                    builder.setTitle(PlainComplicationText.Builder(delta).build())
                }
                if (tapAction != null) builder.setTapAction(tapAction)
                builder.build()
            }

            else -> null
        }
    }

    private fun createLaunchPendingIntent(): PendingIntent {
        val intent = Intent(applicationContext, WearMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            10,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
