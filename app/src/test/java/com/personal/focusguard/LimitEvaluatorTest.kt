package com.personal.focusguard

import com.personal.focusguard.domain.limits.Interval
import com.personal.focusguard.domain.limits.IntervalMath
import com.personal.focusguard.domain.limits.LimitEvaluator
import com.personal.focusguard.domain.model.OpenRule
import com.personal.focusguard.domain.model.OpenWindowType
import com.personal.focusguard.domain.model.UsageRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class LimitEvaluatorTest {

    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test
    fun rollingWindow_countsOnlyLastXMinutes() {
        val rule = OpenRule(enabled = true, maxOpens = 3, windowType = OpenWindowType.ROLLING, windowMinutes = 60)
        val now = at("2026-10-21T12:00")
        val opens = listOf(at("2026-10-21T10:50"), at("2026-10-21T11:10"), at("2026-10-21T11:40"))
        val s = LimitEvaluator.opens(rule, opens, now, zone)
        assertEquals(2, s.used) // la de las 10:50 ya salió de la ventana
        assertEquals(1, s.remaining)
        assertFalse(s.exhausted)
        assertNull(s.nextAvailableAt)
    }

    @Test
    fun rollingWindow_nextOpeningWhenOldestLeavesWindow() {
        val rule = OpenRule(enabled = true, maxOpens = 3, windowType = OpenWindowType.ROLLING, windowMinutes = 60)
        val now = at("2026-10-21T12:00")
        val opens = listOf(at("2026-10-21T11:05"), at("2026-10-21T11:20"), at("2026-10-21T11:50"))
        val s = LimitEvaluator.opens(rule, opens, now, zone)
        assertTrue(s.exhausted)
        assertEquals(at("2026-10-21T12:05"), s.nextAvailableAt)
    }

    @Test
    fun rollingWindow_customFourIn45() {
        val rule = OpenRule(enabled = true, maxOpens = 4, windowType = OpenWindowType.ROLLING, windowMinutes = 45)
        val now = at("2026-10-21T12:00")
        val opens = listOf(at("2026-10-21T11:14"), at("2026-10-21T11:16"), at("2026-10-21T11:30"), at("2026-10-21T11:59"))
        val s = LimitEvaluator.opens(rule, opens, now, zone)
        assertEquals(3, s.used) // 11:14 está fuera (ventana desde 11:15)
        assertFalse(s.exhausted)
    }

    @Test
    fun fixedBlocks_countPerBlockAndResetAtBlockEnd() {
        val rule = OpenRule(enabled = true, maxOpens = 3, windowType = OpenWindowType.FIXED_BLOCKS, windowMinutes = 60, anchorMinuteOfDay = 0)
        val opens = listOf(at("2026-10-21T09:55"), at("2026-10-21T10:01"), at("2026-10-21T10:20"), at("2026-10-21T10:40"))
        val s = LimitEvaluator.opens(rule, opens, at("2026-10-21T10:45"), zone)
        assertEquals(3, s.used)
        assertTrue(s.exhausted)
        assertEquals(at("2026-10-21T11:00"), s.nextAvailableAt)
        val next = LimitEvaluator.opens(rule, opens, at("2026-10-21T11:00"), zone)
        assertEquals(0, next.used)
    }

    @Test
    fun opensAfterClockRollbackStillCountInRollingWindow() {
        val rule = OpenRule(enabled = true, maxOpens = 2, windowType = OpenWindowType.ROLLING, windowMinutes = 60)
        // Aperturas registradas a las 12:30 y 12:40; luego el reloj se atrasa a las 12:00.
        val opens = listOf(at("2026-10-21T12:30"), at("2026-10-21T12:40"))
        val s = LimitEvaluator.opens(rule, opens, at("2026-10-21T12:00"), zone)
        assertTrue(s.exhausted)
    }

    @Test
    fun usage_sumsOnlyCurrentPeriod() {
        val rule = UsageRule(enabled = true, limitMinutes = 30, resetMinuteOfDay = 4 * 60)
        val sessions = listOf(
            Interval(at("2026-10-21T03:50"), at("2026-10-21T04:10")), // 10 min antes y 10 después del reinicio
            Interval(at("2026-10-21T09:00"), at("2026-10-21T09:15")),
        )
        val s = LimitEvaluator.usage(rule, 0, sessions, at("2026-10-21T12:00"), zone)
        assertEquals(25 * MIN, s.usedMs)
        assertEquals(5 * MIN, s.remainingMs)
        assertEquals(at("2026-10-22T04:00"), s.resetsAt)
    }

    @Test
    fun usage_globalDefaultResetUsedWhenRuleHasNone() {
        val rule = UsageRule(enabled = true, limitMinutes = 30, resetMinuteOfDay = null)
        val s = LimitEvaluator.usage(rule, 6 * 60, emptyList(), at("2026-10-21T05:00"), zone)
        assertEquals(at("2026-10-20T06:00"), s.period.start)
    }

    @Test
    fun usage_overlappingIntervalsAreNotDoubleCounted() {
        val a = Interval(at("2026-10-21T10:00"), at("2026-10-21T10:20"))
        val b = Interval(at("2026-10-21T10:10"), at("2026-10-21T10:30"))
        assertEquals(30 * MIN, IntervalMath.unionDuration(listOf(a, b, a), 0, Long.MAX_VALUE))
    }

    @Test
    fun usage_exhaustedWhenLimitReached() {
        val rule = UsageRule(enabled = true, limitMinutes = 30, resetMinuteOfDay = 4 * 60)
        val sessions = listOf(Interval(at("2026-10-21T10:00"), at("2026-10-21T10:30")))
        val s = LimitEvaluator.usage(rule, 0, sessions, at("2026-10-21T11:00"), zone)
        assertTrue(s.exhausted)
        assertEquals(0, s.remainingMs)
    }
}
