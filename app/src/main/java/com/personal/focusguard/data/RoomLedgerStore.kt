package com.personal.focusguard.data

import android.util.Log
import com.personal.focusguard.data.db.HistoryDao
import com.personal.focusguard.data.db.SessionEntity
import com.personal.focusguard.domain.tracking.LedgerStore
import com.personal.focusguard.domain.tracking.SessionRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Implementación Room de [LedgerStore]. Las escrituras se encolan en un canal consumido por una
 * única corrutina, así se aplican en el mismo orden en que ocurrieron sin bloquear el hilo
 * principal del servicio de accesibilidad.
 */
class RoomLedgerStore(private val dao: HistoryDao, scope: CoroutineScope) : LedgerStore {

    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (op in queue) {
                try {
                    op()
                } catch (t: Throwable) {
                    Log.e("FocusGuard", "Error al guardar historial", t)
                }
            }
        }
    }

    override suspend fun loadSince(since: Long): Pair<List<SessionRecord>, List<Pair<String, Long>>> {
        val sessions = dao.sessionsSince(since).map { SessionRecord(it.id, it.packageName, it.startMs, it.endMs) }
        val opens = dao.opensSince(since).map { it.packageName to it.timestampMs }
        return sessions to opens
    }

    override fun saveSession(session: SessionRecord) {
        queue.trySend { dao.upsertSession(SessionEntity(session.id, session.packageName, session.start, session.end)) }
    }

    override fun saveOpen(packageName: String, timestamp: Long) {
        queue.trySend { dao.insertOpen(packageName, timestamp) }
    }

    override fun pruneBefore(timestamp: Long) {
        queue.trySend {
            dao.pruneSessions(timestamp)
            dao.pruneOpens(timestamp)
        }
    }

    override suspend fun clearAll() {
        // Se encola para que no lo pisen escrituras pendientes anteriores.
        val done = CompletableDeferred<Unit>()
        queue.trySend {
            dao.clearSessions()
            dao.clearOpens()
            done.complete(Unit)
        }
        done.await()
    }
}
