package com.personal.focusguard

import com.personal.focusguard.domain.morning.MorningDecision
import com.personal.focusguard.domain.morning.MorningRule
import com.personal.focusguard.domain.morning.MorningSchedule
import com.personal.focusguard.domain.morning.MorningStart
import com.personal.focusguard.domain.morning.MorningTracker
import com.personal.focusguard.domain.tracking.BlockReason
import com.personal.focusguard.domain.tracking.UsageLedger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class MorningModeTest {

    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val ig = "com.instagram.android"
    private val wa = "com.whatsapp"
    private val maps = "com.google.android.apps.maps"
    private val launcher = "com.android.launcher"

    private lateinit var clock: FakeClock
    private lateinit var store: InMemoryLedgerStore
    private lateinit var ledger: UsageLedger
    private var rule = MorningRule()
    private lateinit var tracker: MorningTracker

    @Before
    fun setUp() {
        // 2026-10-21 es miércoles.
        clock = FakeClock(at("2026-10-21T04:00"))
        store = InMemoryLedgerStore()
        rule = MorningRule(
            enabled = true,
            start = MorningStart.FIRST_UNLOCK,
            earliestMinute = 5 * 60,
            latestMinute = 12 * 60,
            durationMinutes = 120,
            screenLimitEnabled = true,
            screenLimitMinutes = 20,
            restricted = setOf(ig),
            privileged = setOf(maps),
        )
        newTracker()
    }

    private fun newTracker() {
        ledger = UsageLedger(store)
        runBlocking { ledger.ensureLoaded(clock.wall) }
        tracker = MorningTracker(ledger, { rule }, clock, { setOf(launcher) })
    }

    private fun goTo(text: String) {
        val target = at(text)
        clock.advance(target - clock.wall)
    }

    // --- Horario -------------------------------------------------------------------------------

    @Test
    fun firstUnlockAfterEarliestStartsTheWindow() {
        val unlocks = listOf(at("2026-10-21T03:10"), at("2026-10-21T07:15"), at("2026-10-21T07:40"))
        val w = MorningSchedule.windowFor(rule, LocalDate.of(2026, 10, 21), zone, unlocks)!!
        assertEquals(at("2026-10-21T07:15"), w.start) // el desbloqueo de las 03:10 se ignora
        assertEquals(at("2026-10-21T09:15"), w.end)
    }

    @Test
    fun noUnlockBeforeLatestMeansNoMorningMode() {
        val unlocks = listOf(at("2026-10-21T13:00"))
        assertNull(MorningSchedule.windowFor(rule, LocalDate.of(2026, 10, 21), zone, unlocks))
    }

    @Test
    fun fixedTimeWindowAndExcludedDays() {
        val fixed = rule.copy(start = MorningStart.FIXED_TIME, fixedMinute = 7 * 60, days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val w = MorningSchedule.activeWindow(fixed, at("2026-10-21T08:00"), zone, emptyList())!!
        assertEquals(at("2026-10-21T07:00"), w.start)
        // Jueves no está en los días elegidos.
        assertNull(MorningSchedule.activeWindow(fixed, at("2026-10-22T08:00"), zone, emptyList()))
    }

    @Test
    fun windowCrossingMidnightIsStillActive() {
        val late = rule.copy(start = MorningStart.FIXED_TIME, fixedMinute = 23 * 60, durationMinutes = 120)
        assertTrue(MorningSchedule.activeWindow(late, at("2026-10-22T00:30"), zone, emptyList()) != null)
    }

    // --- Seguimiento ---------------------------------------------------------------------------

    private fun wakeUpAt(text: String) {
        goTo(text)
        tracker.onUnlock()
    }

    @Test
    fun beforeWakingUpNothingIsRestricted() {
        assertEquals(MorningDecision.Inactive, tracker.onForeground(ig))
    }

    @Test
    fun restrictedAppIsBlockedDuringTheWindowAndFreeAfter() {
        wakeUpAt("2026-10-21T07:00")
        val d = tracker.onForeground(ig)
        assertTrue(d is MorningDecision.Blocked && d.reason is BlockReason.MorningRestricted)
        assertEquals(at("2026-10-21T09:00"), ((d as MorningDecision.Blocked).reason as BlockReason.MorningRestricted).endsAt)
        tracker.onForeground(launcher)
        goTo("2026-10-21T09:00")
        assertEquals(MorningDecision.Inactive, tracker.onForeground(ig))
    }

    @Test
    fun screenLimitBlocksNonPrivilegedAppsButNotPrivilegedOnes() {
        wakeUpAt("2026-10-21T07:00")
        val first = tracker.onForeground(wa) as MorningDecision.Allowed
        assertEquals(20 * MIN, first.remainingMs)
        clock.advanceMinutes(20)
        val d = tracker.check()
        assertTrue(d is MorningDecision.Blocked && d.reason is BlockReason.MorningScreenLimit)
        // Privilegiada y esencial: siempre accesibles.
        assertEquals(MorningDecision.Allowed(null), tracker.onForeground(maps))
        assertEquals(MorningDecision.Allowed(null), tracker.onForeground(launcher))
        assertTrue(tracker.onForeground(wa) is MorningDecision.Blocked)
    }

    @Test
    fun privilegedAndEssentialTimeDoesNotCount() {
        wakeUpAt("2026-10-21T07:00")
        tracker.onForeground(maps)
        clock.advanceMinutes(30)
        tracker.onForeground(launcher)
        clock.advanceMinutes(10)
        tracker.onForeground(wa)
        clock.advanceMinutes(5)
        assertEquals(5 * MIN, tracker.status()!!.usedMs)
    }

    @Test
    fun screenOffDoesNotCount() {
        wakeUpAt("2026-10-21T07:00")
        tracker.onForeground(wa)
        clock.advanceMinutes(3)
        tracker.onScreenOff()
        clock.advanceMinutes(30)
        tracker.onUnlock()
        tracker.onForeground(wa)
        clock.advanceMinutes(2)
        assertEquals(5 * MIN, tracker.status()!!.usedMs)
    }

    @Test
    fun remainingNeverExceedsTheEndOfTheWindow() {
        rule = rule.copy(screenLimitMinutes = 60)
        wakeUpAt("2026-10-21T07:00")
        goTo("2026-10-21T08:50")
        val d = tracker.onForeground(wa) as MorningDecision.Allowed
        assertEquals(10 * MIN, d.remainingMs)
    }

    @Test
    fun usageAndWakeUpSurviveARestart() {
        wakeUpAt("2026-10-21T07:00")
        tracker.onForeground(wa)
        clock.advanceMinutes(15)
        tracker.check() // latido: persiste
        newTracker() // "reinicio"
        clock.advanceMinutes(1)
        val d = tracker.onForeground(wa) as MorningDecision.Allowed
        assertEquals(5 * MIN, d.remainingMs)
        assertTrue(tracker.onForeground(ig) is MorningDecision.Blocked) // la ventana sigue activa
    }

    @Test
    fun disabledRuleDoesNothing() {
        rule = rule.copy(enabled = false)
        wakeUpAt("2026-10-21T07:00")
        assertEquals(MorningDecision.Inactive, tracker.onForeground(ig))
    }
}
