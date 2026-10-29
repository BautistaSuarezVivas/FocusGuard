package com.personal.focusguard.domain.tracking

import com.personal.focusguard.domain.limits.Interval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Intervalo en primer plano de una app controlada. [end] se actualiza con cada latido. */
data class SessionRecord(val id: String, val packageName: String, val start: Long, val end: Long)

/** Persistencia de los registros mínimos (Room en la app, memoria en los tests). */
interface LedgerStore {
    suspend fun loadSince(since: Long): Pair<List<SessionRecord>, List<Pair<String, Long>>>
    /** Escrituras asíncronas pero en orden. */
    fun saveSession(session: SessionRecord)
    fun saveOpen(packageName: String, timestamp: Long)
    fun pruneBefore(timestamp: Long)
    suspend fun clearAll()
}

/**
 * Copia en memoria del historial reciente (sesiones y aperturas) respaldada por [LedgerStore].
 * Es la única fuente de verdad del proceso: la usan el servicio de accesibilidad para decidir
 * bloqueos en el acto y la interfaz para mostrar el estado.
 */
class UsageLedger(private val store: LedgerStore) {

    private val lock = Any()
    private val loadMutex = Mutex()
    private val sessions = HashMap<String, MutableList<SessionRecord>>()
    private val opens = HashMap<String, MutableList<Long>>()

    @Volatile
    var isLoaded: Boolean = false
        private set

    private val _version = MutableStateFlow(0L)
    /** Cambia cada vez que se modifica el historial; útil para refrescar la interfaz. */
    val version: StateFlow<Long> = _version

    suspend fun ensureLoaded(now: Long) {
        if (isLoaded) return
        loadMutex.withLock {
            if (isLoaded) return
            val (s, o) = store.loadSince(now - RETENTION_MS)
            synchronized(lock) {
                // Mezcla con lo que se haya registrado en memoria mientras se cargaba.
                s.forEach { rec ->
                    val list = sessions.getOrPut(rec.packageName) { mutableListOf() }
                    if (list.none { it.id == rec.id }) list.add(rec)
                }
                o.forEach { (pkg, ts) -> opens.getOrPut(pkg) { mutableListOf() }.add(ts) }
                opens.values.forEach { it.sort() }
            }
            isLoaded = true
            bump()
        }
    }

    fun recordOpen(packageName: String, timestamp: Long) {
        synchronized(lock) { opens.getOrPut(packageName) { mutableListOf() }.add(timestamp) }
        store.saveOpen(packageName, timestamp)
        bump()
    }

    /** Inserta o reemplaza una sesión (mismo id). */
    fun upsertSession(session: SessionRecord, persist: Boolean = true) {
        synchronized(lock) {
            val list = sessions.getOrPut(session.packageName) { mutableListOf() }
            val idx = list.indexOfFirst { it.id == session.id }
            if (idx >= 0) list[idx] = session else list.add(session)
        }
        if (persist) store.saveSession(session)
        bump()
    }

    fun sessionIntervals(packageName: String): List<Interval> = synchronized(lock) {
        sessions[packageName]?.map { Interval(it.start, it.end) } ?: emptyList()
    }

    fun opensOf(packageName: String): List<Long> = synchronized(lock) {
        opens[packageName]?.toList() ?: emptyList()
    }

    fun prune(now: Long) {
        val cutoff = now - RETENTION_MS
        synchronized(lock) {
            sessions.values.forEach { list -> list.removeAll { it.end < cutoff } }
            opens.values.forEach { list -> list.removeAll { it < cutoff } }
        }
        store.pruneBefore(cutoff)
    }

    suspend fun clear() {
        synchronized(lock) {
            sessions.clear()
            opens.clear()
        }
        store.clearAll()
        bump()
    }

    private fun bump() {
        _version.update { it + 1 }
    }

    companion object {
        /** Cubre el período más largo (semanal) con margen. */
        const val RETENTION_MS = 15L * 24 * 60 * 60 * 1000
    }
}
