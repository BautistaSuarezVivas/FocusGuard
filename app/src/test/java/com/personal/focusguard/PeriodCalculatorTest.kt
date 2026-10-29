package com.personal.focusguard

import com.personal.focusguard.domain.period.PeriodCalculator
import com.personal.focusguard.domain.period.PeriodSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class PeriodCalculatorTest {

    private val ba = ZoneId.of("America/Argentina/Buenos_Aires")
    private val ny = ZoneId.of("America/New_York")
    private val daily4 = PeriodSpec.FixedBlocks(1440, 4 * 60)

    @Test
    fun dailyResetAtCustomHour_afterReset() {
        val r = PeriodCalculator.currentRange(daily4, at("2026-10-21T10:00"), ba)
        assertEquals(at("2026-10-21T04:00"), r.start)
        assertEquals(at("2026-10-22T04:00"), r.end)
    }

    @Test
    fun dailyResetAtCustomHour_beforeResetBelongsToPreviousDay() {
        val r = PeriodCalculator.currentRange(daily4, at("2026-10-21T03:59"), ba)
        assertEquals(at("2026-10-20T04:00"), r.start)
        assertEquals(at("2026-10-21T04:00"), r.end)
    }

    @Test
    fun dailyResetExactlyAtResetStartsNewPeriod() {
        val r = PeriodCalculator.currentRange(daily4, at("2026-10-21T04:00"), ba)
        assertEquals(at("2026-10-21T04:00"), r.start)
    }

    @Test
    fun fixedHourlyBlocks() {
        val spec = PeriodSpec.FixedBlocks(60, 0)
        val r = PeriodCalculator.currentRange(spec, at("2026-10-21T10:37"), ba)
        assertEquals(at("2026-10-21T10:00"), r.start)
        assertEquals(at("2026-10-21T11:00"), r.end)
    }

    @Test
    fun fixedBlocksOf45Minutes() {
        val spec = PeriodSpec.FixedBlocks(45, 0)
        val r = PeriodCalculator.currentRange(spec, at("2026-10-21T01:50"), ba)
        assertEquals(at("2026-10-21T01:30"), r.start)
        assertEquals(at("2026-10-21T02:15"), r.end)
    }

    @Test
    fun fixedBlocksThatDoNotDivideTheDayAreTruncatedAtNextAnchor() {
        // Bloques de 7 h desde las 04:00: 04-11, 11-18, 18-01, 01-04 (recortado).
        val spec = PeriodSpec.FixedBlocks(7 * 60, 4 * 60)
        val r = PeriodCalculator.currentRange(spec, at("2026-10-22T02:00"), ba)
        assertEquals(at("2026-10-22T01:00"), r.start)
        assertEquals(at("2026-10-22T04:00"), r.end)
    }

    @Test
    fun weeklyStartsMondayAtResetHour() {
        val spec = PeriodSpec.Weekly(4 * 60)
        // 2026-10-21 es miércoles.
        val r = PeriodCalculator.currentRange(spec, at("2026-10-21T12:00"), ba)
        assertEquals(at("2026-10-19T04:00"), r.start)
        assertEquals(at("2026-10-26T04:00"), r.end)
        // Lunes antes de las 04:00 pertenece a la semana anterior.
        val r2 = PeriodCalculator.currentRange(spec, at("2026-10-19T03:00"), ba)
        assertEquals(at("2026-10-12T04:00"), r2.start)
    }

    @Test
    fun rollingHasNoFixedReset() {
        assertNull(PeriodCalculator.nextReset(PeriodSpec.Rolling(60), at("2026-10-21T10:00"), ba))
    }

    @Test
    fun dstSpringForwardDayIs23Hours() {
        // En Nueva York, el 2026-03-08 a las 02:00 se adelanta a las 03:00.
        val spec = PeriodSpec.FixedBlocks(1440, 0)
        val r = PeriodCalculator.currentRange(spec, at("2026-03-08T12:00", ny), ny)
        assertEquals(23 * 60 * MIN, r.end - r.start)
    }

    @Test
    fun dstResetInsideGapIsShiftedForward() {
        // Reinicio a las 02:30, hora inexistente ese día: se usa 03:30.
        val spec = PeriodSpec.FixedBlocks(1440, 150)
        val r = PeriodCalculator.currentRange(spec, at("2026-03-08T12:00", ny), ny)
        assertEquals(at("2026-03-08T03:30", ny), r.start)
        assertEquals(at("2026-03-09T02:30", ny), r.end)
    }

    @Test
    fun timeZoneChangeRecomputesPeriodForNewLocalTime() {
        val instant = at("2026-10-21T04:30", ba) // 04:30 en Buenos Aires = 03:30 en Nueva York (EDT)
        val inBa = PeriodCalculator.currentRange(daily4, instant, ba)
        val inNy = PeriodCalculator.currentRange(daily4, instant, ny)
        assertEquals(at("2026-10-21T04:00", ba), inBa.start)
        assertEquals(at("2026-10-20T04:00", ny), inNy.start)
    }

    @Test
    fun dateChangeMovesToTheCorrespondingPeriod() {
        // Saltar el reloj un día hacia adelante simplemente cae en el período de ese día.
        val r1 = PeriodCalculator.currentRange(daily4, at("2026-10-21T10:00"), ba)
        val r2 = PeriodCalculator.currentRange(daily4, at("2026-10-22T10:00"), ba)
        assertEquals(r1.end, r2.start)
    }
}
