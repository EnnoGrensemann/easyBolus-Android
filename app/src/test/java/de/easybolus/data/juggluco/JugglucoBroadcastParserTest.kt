package de.easybolus.data.juggluco

import de.easybolus.data.local.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JugglucoBroadcastParserTest {

    @Test
    fun parsesGlucodataMinuteWithSecondsTimestamp() {
        val reading = JugglucoBroadcastParser.parse(
            JugglucoBroadcastParser.RawBroadcastExtras(
                action = JugglucoBroadcastParser.ACTION_GLUCODATA_MINUTE,
                glucoseMgDl = 121.0,
                ratePerMin = 1.8,
                timestampMillis = 1_700_000_000L
            )
        )
        assertNotNull(reading)
        assertEquals(121, reading!!.mgDl)
        assertEquals(TrendArrow.UP, reading.trendArrow)
        assertEquals(1_700_000_000_000L, reading.timestampMillis)
        assertEquals("Juggluco", reading.source)
    }

    @Test
    fun parsesXdripBgEstimateWithSlopeName() {
        val reading = JugglucoBroadcastParser.parse(
            JugglucoBroadcastParser.RawBroadcastExtras(
                action = JugglucoBroadcastParser.ACTION_XDRIP_BG_ESTIMATE,
                glucoseMgDl = 148.0,
                slopeName = "FortyFiveDown",
                timestampMillis = 1_700_000_000_000L
            )
        )
        assertNotNull(reading)
        assertEquals(148, reading!!.mgDl)
        assertEquals(TrendArrow.DOWN_RIGHT, reading.trendArrow)
        assertEquals("Juggluco (xDrip)", reading.source)
    }

    @Test
    fun ignoresInvalidGlucoseValues() {
        val reading = JugglucoBroadcastParser.parse(
            JugglucoBroadcastParser.RawBroadcastExtras(
                action = JugglucoBroadcastParser.ACTION_GLUCODATA_MINUTE,
                glucoseMgDl = -5.0
            )
        )
        assertNull(reading)
    }
}
