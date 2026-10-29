package com.personal.focusguard

import com.personal.focusguard.domain.time.Clock
import com.personal.focusguard.domain.tracking.LedgerStore
import com.personal.focusguard.domain.tracking.SessionRecord
import java.time.LocalDateTime
import java.time.ZoneId

/** Reloj controlable: el de pared y el monotónico avanzan juntos salvo que se cambie la hora. */
class FakeClock(var wall: Long, var elapsed: Long = 1_000_000L, var zoneId: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")) : Clock {
    override fun wallMillis() = wall
    override fun elapsedMillis() = elapsed
    override fun zone(): ZoneId = zoneId

    fun advance(ms: Long) {
        wall += ms
        elapsed += ms
    }

    fun advanceMinutes(m: Long) = advance(m * 60_000)

    /** Cambio de hora del sistema: solo cambia el reloj de pared. */
    fun changeSystemTime(newWall: Long) {
        wall = newWall
    }
}

/** Persistencia en memoria que sobrevive a "reinicios" (se reutiliza entre ledgers). */
class InMemoryLedgerStore : LedgerStore {
    val sessions = LinkedHashMap<String, SessionRecord>()
    val opens = mutableListOf<Pair<String, Long>>()

    override suspend fun loadSince(since: Long) =
        sessions.values.filter { it.end >= since }.toList() to opens.filter { it.second >= since }.toList()

    override fun saveSession(session: SessionRecord) {
        sessions[session.id] = session
    }

    override fun saveOpen(packageName: String, timestamp: Long) {
        opens += packageName to timestamp
    }

    override fun pruneBefore(timestamp: Long) {
        sessions.values.removeAll { it.end < timestamp }
        opens.removeAll { it.second < timestamp }
    }

    override suspend fun clearAll() {
        sessions.clear()
        opens.clear()
    }
}

fun at(text: String, zone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")): Long =
    LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

const val MIN = 60_000L
