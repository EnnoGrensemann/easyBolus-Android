package de.easybolus.wear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon

/**
 * Rendert gestochen scharfe Watchface-Complication-Icons (SMALL_IMAGE) im Stil von GlucoDataHandler:
 * großer Blutzuckerwert + klarer Trendpfeil in Statusfarbe (und rein weiß für den Always-On-Ambient-Modus).
 */
object ComplicationIconRenderer {

    fun createGlucoseIcon(
        bgText: String,
        trendSymbol: String,
        colorArgb: Int,
        ambient: Boolean = false
    ): Icon {
        val size = 144
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val drawColor = if (ambient) Color.WHITE else colorArgb

        val hasArrow = trendSymbol.isNotEmpty()

        // Oberer Hauptwert (Blutzucker)
        val bgPaint = Paint().apply {
            color = drawColor
            isAntiAlias = true
            isSubpixelText = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = when {
                bgText.length <= 2 -> if (hasArrow) 68f else 78f
                bgText.length == 3 -> if (hasArrow) 58f else 66f
                else -> if (hasArrow) 48f else 54f
            }
        }
        val bgBounds = Rect()
        bgPaint.getTextBounds(bgText, 0, bgText.length, bgBounds)
        val maxW = size - 10f
        if (bgBounds.width() > maxW && bgBounds.width() > 0) {
            bgPaint.textSize = bgPaint.textSize * (maxW / bgBounds.width())
            bgPaint.getTextBounds(bgText, 0, bgText.length, bgBounds)
        }

        if (hasArrow) {
            // Wert im oberen Bereich, klarer Trendpfeil zentriert darunter
            val bgCenterY = size * 0.38f
            canvas.drawText(bgText, size / 2f, bgCenterY - bgBounds.exactCenterY(), bgPaint)

            val arrowPaint = Paint().apply {
                color = drawColor
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 48f
            }
            val arrowBounds = Rect()
            arrowPaint.getTextBounds(trendSymbol, 0, trendSymbol.length, arrowBounds)
            val arrowCenterY = size * 0.77f
            canvas.drawText(trendSymbol, size / 2f, arrowCenterY - arrowBounds.exactCenterY(), arrowPaint)
        } else {
            val bgCenterY = size * 0.50f
            canvas.drawText(bgText, size / 2f, bgCenterY - bgBounds.exactCenterY(), bgPaint)
        }

        return Icon.createWithBitmap(bmp)
    }
}
