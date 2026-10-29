package com.personal.focusguard

import com.personal.focusguard.util.Format
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FormatTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test
    fun momentRoundsUpToTheNextMinute() {
        // Una apertura que se libera a las 05:02:21 no puede anunciarse como "05:02".
        assertEquals("05:03", Format.moment(at("2026-10-21T05:02:21"), at("2026-10-21T05:01:00"), zone))
    }

    @Test
    fun exactMinuteIsNotRounded() {
        assertEquals("mañana 04:00", Format.moment(at("2026-10-22T04:00"), at("2026-10-21T12:00"), zone))
    }
}
