package de.easybolus.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.easybolus.wear.R
import de.easybolus.wear.WearGlucoseSnapshot
import de.easybolus.wear.WearGlucoseStore
import de.easybolus.wear.WearMainActivity
import java.util.Locale

/**
 * Optionale zweite Watchface-Complication für aktives Insulin (IOB) & Kohlenhydrate (COB).
 */
class IobCobComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val sample = WearGlucoseSnapshot(hasData = true, iob = 2.4, cob = 42.0)
        return buildData(type, sample, tapAction = null)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snapshot = WearGlucoseStore.load(applicationContext)
        return buildData(request.complicationType, snapshot, createLaunchPendingIntent())
    }

    private fun buildData(
        type: ComplicationType,
        snapshot: WearGlucoseSnapshot,
        tapAction: PendingIntent?
    ): ComplicationData? {
        val locale = Locale.getDefault()
        val ieUnit = getString(R.string.unit_ie)
        val iobShort = String.format(locale, "%.1f %s", snapshot.iob, ieUnit)
        val cobShort = String.format(locale, "%.0f g", snapshot.cob)
        val contentDesc = PlainComplicationText.Builder(getString(R.string.complication_iob_cob_cd)).build()

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                val builder = ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(iobShort).build(),
                    contentDescription = contentDesc
                ).setTitle(
                    PlainComplicationText.Builder(if (snapshot.cob > 0.0) cobShort else "IOB").build()
                )
                if (tapAction != null) builder.setTapAction(tapAction)
                builder.build()
            }

            ComplicationType.LONG_TEXT -> {
                val line = String.format(locale, "IOB %.2f %s · COB %.0f g", snapshot.iob, ieUnit, snapshot.cob)
                val builder = LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(line).build(),
                    contentDescription = contentDesc
                )
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
            11,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
