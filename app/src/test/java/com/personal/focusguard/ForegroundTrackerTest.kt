package com.personal.focusguard

import com.personal.focusguard.domain.model.AppRules
import com.personal.focusguard.domain.model.OpenRule
import com.personal.focusguard.domain.model.OpenWindowType
import com.personal.focusguard.domain.model.UsageRule
import com.personal.focusguard.domain.tracking.BlockReason
import com.personal.focusguard.domain.tracking.EntryDecision
import com.personal.focusguard.domain.tracking.ForegroundTracker
import com.personal.focusguard.domain.tracking.UsageLedger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForegroundTrackerTest {

    private val ig = "com.instagram.android"
    private val wa = "com.whatsapp"
    private val launcher = "com.android.launcher"

    private lateinit var clock: FakeClock
    private lateinit var store: InMemoryLedgerStore
    private lateinit var ledger: UsageLedger
    private var rules = mapOf<String, AppRules>()
    private lateinit var tracker: ForegroundTracker
    private var ids = 0

    @Before
    fun setUp() {
        clock = FakeClock(at("2026-10-21T10:00"))
        store = InMemoryLedgerStore()
        rules = mapOf(
            ig to AppRules(
                ig, "Instagram",
                usage = UsageRule(enabled = true, limitMinutes = 30, resetMinuteOfDay = 4 * 60),
                opens = OpenRule(enabled = true, maxOpens = 3, windowType = OpenWindowType.ROLLING, windowMinutes = 60),
            ),
            wa to AppRules(
                wa, "WhatsApp",
                usage = UsageRule(enabled = true, limitMinutes = 60, resetMinuteOfDay = 4 * 60),
                opens = OpenRule(enabled = true, maxOpens = 10, windowType = OpenWindowType.ROLLING, windowMinutes = 30),
            ),
        )
        newTracker()
    }

    /** Simula un arranque del servicio (o del teléfono) con lo que haya en el almacenamiento. */
    private fun newTracker() {
        ledger = UsageLedger(store)
        runBlocking { ledger.ensureLoaded(clock.wall) }
        tracker = ForegroundTracker(ledger, { rules }, { 4 * 60 }, clock, { "s${ids++}" })
        // La primera observación tras arrancar se trata como continuación; arrancamos en el launcher.
        tracker.onForeground(launcher)
    }

    private fun open(pkg: String, stayMinutes: Long = 1): EntryDecision {
        val d = tracker.onForeground(pkg)
        clock.advanceMinutes(stayMinutes)
        tracker.onForeground(launcher)
        clock.advanceMinutes(1)
        return d
    }

    private fun usedMs(pkg: String) = tracker.statusOf(pkg)!!.usage!!.usedMs
    private fun opensUsed(pkg: String) = tracker.statusOf(pkg)!!.opens!!.used

    @Test
    fun countsOneOpenPerEntryFromLauncher() {
        open(ig)
        open(ig)
        assertEquals(2, opensUsed(ig))
    }

    @Test
    fun repeatedEventsInSameAppDoNotCountAsNewOpens() {
        tracker.onForeground(ig)
        repeat(20) {
            clock.advance(500)
            assertEquals(EntryDecision.SameApp, tracker.onForeground(ig))
        }
        assertEquals(1, opensUsed(ig))
    }

    @Test
    fun quickReturnWithinGraceIsNotANewOpen() {
        tracker.onForeground(ig)
        clock.advance(10_000)
        tracker.onForeground(launcher)
        clock.advance(2_000)
        tracker.onForeground(ig)
        assertEquals(1, opensUsed(ig))
    }

    @Test
    fun blocksWhenOpensExhaustedAndBlockedAttemptsDoNotConsume() {
        repeat(3) { open(ig) }
        val d = tracker.onForeground(ig)
        assertTrue(d is EntryDecision.Blocked && d.reason is BlockReason.OpenLimit)
        tracker.onForeground(launcher)
        clock.advanceMinutes(1)
        tracker.onForeground(ig) // otro intento bloqueado
        tracker.onForeground(launcher)
        assertEquals(3, opensUsed(ig))
        assertEquals(3, store.opens.count { it.first == ig })
    }

    @Test
    fun rollingWindowReleasesOpenAfterWindowPasses() {
        repeat(3) { open(ig) } // 10:00, 10:02, 10:04
        clock.wall = at("2026-10-21T11:01"); clock.elapsed += 60 * MIN
        val d = tracker.onForeground(ig)
        assertTrue(d is EntryDecision.Allowed)
    }

    @Test
    fun foregroundTimeOnlyCountsWhileAppIsInFront() {
        tracker.onForeground(ig)
        clock.advanceMinutes(5)
        tracker.onForeground(wa)
        clock.advanceMinutes(20)
        tracker.onForeground(ig)
        clock.advanceMinutes(3)
        assertEquals(8 * MIN, usedMs(ig))
        assertEquals(20 * MIN, usedMs(wa))
    }

    @Test
    fun appsAreIndependent() {
        repeat(3) { open(ig) }
        val d = tracker.onForeground(wa)
        assertTrue(d is EntryDecision.Allowed)
        assertEquals(3, opensUsed(ig))
        assertEquals(1, opensUsed(wa))
    }

    @Test
    fun usageLimitBlocksWhileInsideApp() {
        val d = tracker.onForeground(ig) as EntryDecision.Allowed
        assertEquals(30 * MIN, d.usageRemainingMs)
        clock.advanceMinutes(29)
        assertTrue(tracker.checkCurrent() is EntryDecision.Allowed)
        clock.advanceMinutes(1)
        val c = tracker.checkCurrent()
        assertTrue(c is EntryDecision.Blocked && c.reason is BlockReason.UsageLimit)
        // Volver a entrar sigue bloqueado y no consume aperturas.
        tracker.onForeground(launcher)
        clock.advanceMinutes(1)
        val again = tracker.onForeground(ig)
        assertTrue(again is EntryDecision.Blocked)
        assertEquals(1, opensUsed(ig))
    }

    @Test
    fun usageResetsAtConfiguredHour() {
        tracker.onForeground(ig)
        clock.advanceMinutes(30)
        assertTrue(tracker.checkCurrent() is EntryDecision.Blocked)
        tracker.onForeground(launcher)
        clock.wall = at("2026-10-22T04:00"); clock.elapsed += 18 * 60 * MIN
        assertTrue(tracker.onForeground(ig) is EntryDecision.Allowed)
    }

    @Test
    fun screenOffStopsCountingAndUnlockIntoSameAppIsNotNewOpen() {
        tracker.onForeground(ig)
        clock.advanceMinutes(2)
        tracker.onScreenOff()
        clock.advanceMinutes(30)
        tracker.onForeground(ig)
        clock.advanceMinutes(1)
        assertEquals(3 * MIN, usedMs(ig))
        assertEquals(1, opensUsed(ig))
    }

    @Test
    fun clockChangeDoesNotCreateNegativeOrDuplicatedTime() {
        tracker.onForeground(ig)
        clock.advanceMinutes(5)
        // El usuario atrasa el reloj 1 hora (el reloj monotónico sigue igual).
        clock.changeSystemTime(clock.wall - 60 * MIN)
        tracker.onTimeChanged()
        clock.advanceMinutes(5)
        tracker.onForeground(launcher)
        assertEquals(10 * MIN, store.sessions.values.filter { it.packageName == ig }.sumOf { it.end - it.start })
        assertTrue(store.sessions.values.all { it.end >= it.start })
    }

    @Test
    fun stateIsRecoveredAfterRestart() {
        tracker.onForeground(ig)
        clock.advanceMinutes(10)
        tracker.checkCurrent() // latido: persiste el fin de la sesión
        open(ig) // sigue en Instagram (no es apertura nueva), 1 min más y sale al launcher
        // "Reinicio": nuevo ledger y tracker leyendo del almacenamiento.
        clock.advanceMinutes(5)
        newTracker()
        assertEquals(11 * MIN, usedMs(ig))
        assertEquals(1, opensUsed(ig))
    }

    @Test
    fun sessionInterruptedByCrashKeepsTimeUntilLastHeartbeat() {
        tracker.onForeground(ig)
        clock.advanceMinutes(7)
        tracker.checkCurrent()
        clock.advanceMinutes(3) // el proceso muere sin cerrar la sesión
        newTracker()
        assertEquals(7 * MIN, usedMs(ig))
    }

    @Test
    fun unmonitoredAppsAreNotRecorded() {
        tracker.onForeground("com.other.app")
        clock.advanceMinutes(10)
        tracker.onForeground(launcher)
        assertTrue(store.sessions.isEmpty())
        assertTrue(store.opens.isEmpty())
    }

    @Test
    fun firstObservationAfterServiceStartIsNotCountedAsOpen() {
        val t = ForegroundTracker(ledger, { rules }, { 4 * 60 }, clock)
        t.onForeground(ig)
        assertEquals(0, t.statusOf(ig)!!.opens!!.used)
    }
}
