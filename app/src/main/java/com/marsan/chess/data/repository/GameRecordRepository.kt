package com.marsan.chess.data.repository

import android.content.Context
import com.marsan.chess.data.db.GameRecordEntity
import com.marsan.chess.data.db.MarsanChessDatabase
import com.marsan.chess.engine.Move
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/**
 * Gestiona la grabación de partidas.
 *
 * Diseño simplificado y thread-safe:
 * - beginSession()  → guarda metadatos de la partida en memoria (NO suspendida, instantánea)
 * - addMove()       → acumula movimientos en memoria (NO suspendida, instantánea)
 * - finishRecording() → persiste todo en DB de una sola vez (suspendida, llamar en IO)
 *
 * Esto evita el race condition donde addMove() era llamado antes de que
 * startRecording() terminara de insertar en la DB.
 */
class GameRecordRepository private constructor(context: Context) {

    private val dao = MarsanChessDatabase.getInstance(context).gameRecordDao()

    // ── Estado en memoria de la sesión actual ─────────────────────────
    @Volatile private var sessionActive    = false
    @Volatile private var sessionPlayerName = ""
    @Volatile private var sessionMode      = "NORMAL"
    @Volatile private var sessionDifficulty = 3
    @Volatile private var sessionPlayerColor = "WHITE"
    @Volatile private var sessionPaletteIdx  = 0
    @Volatile private var sessionStartTime   = 0L
    private val sessionMoves = mutableListOf<String>()  // sincronizado manualmente

    // ── Observar todas las grabaciones ────────────────────────────────

    fun observeAll(): Flow<List<GameRecordEntity>> = dao.getAll()

    suspend fun getById(id: Int): GameRecordEntity? = dao.getById(id)

    // ── Iniciar sesión (NO suspendida — instantánea, sin DB) ──────────

    fun beginSession(
        playerName:  String,
        mode:        String,
        difficulty:  Int,
        playerColor: String,
        paletteIdx:  Int
    ) {
        synchronized(sessionMoves) {
            sessionMoves.clear()
            sessionPlayerName  = playerName
            sessionMode        = mode
            sessionDifficulty  = difficulty
            sessionPlayerColor = playerColor
            sessionPaletteIdx  = paletteIdx
            sessionStartTime   = System.currentTimeMillis()
            sessionActive      = true
        }
        android.util.Log.d(TAG, "Session started: mode=$mode player=$playerName")
    }

    // ── Añadir movimiento (NO suspendida — solo memoria) ─────────────

    fun addMove(move: Move) {
        if (!sessionActive) return
        // UciMoves.toUci: la versión anterior guardaba la coronación a
        // Caballo como "k" (primera letra de KNIGHT) en vez de "n".
        val uci   = com.marsan.chess.engine.UciMoves.toUci(move)
        synchronized(sessionMoves) { sessionMoves.add(uci) }
        android.util.Log.d(TAG, "Move added: $uci (total: ${sessionMoves.size})")
    }

    // ── Finalizar y persistir en DB ───────────────────────────────────

    suspend fun finishRecording(result: String) {
        if (!sessionActive) {
            android.util.Log.w(TAG, "finishRecording called but no active session")
            return
        }

        val endTime = System.currentTimeMillis()
        val moves: List<String>
        val meta: Triple<String, String, Int>

        synchronized(sessionMoves) {
            moves = sessionMoves.toList()
            meta  = Triple(sessionPlayerName, sessionMode, sessionDifficulty)
            sessionActive = false
        }

        val movesJson = buildJsonArray(moves)
        android.util.Log.d(TAG, "Saving ${moves.size} moves: $movesJson")

        val entity = GameRecordEntity(
            startTimestamp  = sessionStartTime,
            endTimestamp    = endTime,
            playerName      = sessionPlayerName,
            mode            = sessionMode,
            difficulty      = sessionDifficulty,
            result          = result,
            playerColor     = sessionPlayerColor,
            movesJson       = movesJson,
            totalMoves      = moves.size,
            durationSeconds = (endTime - sessionStartTime) / 1000,
            paletteIdx      = sessionPaletteIdx
        )
        dao.insert(entity)
        android.util.Log.d(TAG, "Recording saved: result=$result moves=${moves.size}")
    }

    // ── Cancelar sesión ───────────────────────────────────────────────

    fun cancelSession() {
        synchronized(sessionMoves) {
            sessionActive = false
            sessionMoves.clear()
        }
    }

    // ── Eliminar ──────────────────────────────────────────────────────

    suspend fun deleteAll()         = dao.deleteAll()
    suspend fun deleteById(id: Int) = dao.deleteById(id)
    suspend fun count(): Int        = dao.count()

    // ── Helpers ───────────────────────────────────────────────────────

    private fun buildJsonArray(moves: List<String>): String {
        val arr = JSONArray()
        moves.forEach { arr.put(it) }
        return arr.toString()
    }

    companion object {
        private const val TAG = "GameRecordRepo"

        @Volatile private var INSTANCE: GameRecordRepository? = null

        fun getInstance(context: Context): GameRecordRepository =
            INSTANCE ?: synchronized(this) {
                GameRecordRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
