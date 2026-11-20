package de.easybolus.domain

import de.easybolus.data.settings.GlucoseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class GlucoseFormatterTest {

    @Test
    fun formatsAndParsesMgDlAndMmolL() {
        assertEquals("180", GlucoseFormatter.formatValue(180, GlucoseUnit.MG_DL, Locale.US))
        assertEquals("10.0", GlucoseFormatter.formatValue(180, GlucoseUnit.MMOL_L, Locale.US))
        assertEquals("5,6", GlucoseFormatter.formatValue(100, GlucoseUnit.MMOL_L, Locale.GERMANY))

        assertEquals(180, GlucoseFormatter.parseToMgDl("180", GlucoseUnit.MG_DL))
        assertEquals(180, GlucoseFormatter.parseToMgDl("10,0", GlucoseUnit.MMOL_L))
        assertEquals(101, GlucoseFormatter.parseToMgDl("5.6", GlucoseUnit.MMOL_L))
        assertNull(GlucoseFormatter.parseToMgDl("", GlucoseUnit.MMOL_L))
        assertNull(GlucoseFormatter.parseToMgDl("-2", GlucoseUnit.MG_DL))
    }
}
