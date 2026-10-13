package com.marsan.chess.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter

/**
 * Resultado de análisis de una posición.
 *
 * IMPORTANTE: scoreCp y mateIn están SIEMPRE normalizados a la
 * perspectiva de las BLANCAS, sin importar de quién sea el turno.
 *   scoreCp > 0  → ventaja blanca
 *   scoreCp < 0  → ventaja negra
 *   mateIn = 3   → blancas dan mate en 3
 *   mateIn = -2  → negras dan mate en 2
 */
data class EngineAnalysis(
    val scoreCp: Int,
    val mateIn: Int?,
    val bestMove: String?,
    val depth: Int
)

/**
 * Integración de Stockfish via protocolo UCI.
 *
 * ESTRATEGIA SELinux-safe:
 * Android 10+ bloquea la ejecución de binarios desde filesDir por política
 * SELinux (execute_no_trans denied). La única carpeta permitida para ejecutar
 * binarios es nativeLibraryDir (marcada como lib_file por SELinux).
 *
 * El binario debe llamarse "libstockfish.so" y colocarse en:
 *   app/src/main/jniLibs/arm64-v8a/libstockfish.so
 * (solo arm64-v8a: la app está limitada a dispositivos de 64 bits
 *  mediante abiFilters en build.gradle)
 *
 * También requiere en app/build.gradle:
 *   android { packagingOptions { jniLibs { useLegacyPackaging = true } } }
 */
class StockfishEngine(private val context: Context) {

    private var process: Process? = null
    private var writer: PrintWriter? = null
    private var reader: java.io.BufferedReader? = null
    private var isReady = false

    // ── Niveles de dificultad ─────────────────────────────────────────
    // HALLAZGO (verificado contra Stockfish real): UCI_Elo tiene un
    // MÍNIMO de 1320. Los antiguos valores 200/600/1000 se recortaban
    // silenciosamente a 1320, así que los niveles 1-3 jugaban con la
    // MISMA fuerza (~jugador de club) — imposible para principiantes.
    //
    // Solución: usar "Skill Level" (0-20), que sí llega a nivel
    // principiante. Escalera validada con matches motor-vs-motor:
    // cada nivel le gana con claridad al anterior.
    private val SKILL_BY_LEVEL = mapOf(1 to 0, 2 to 3, 3 to 8, 4 to 14)
    private val TIME_BY_LEVEL  = mapOf(1 to 100, 2 to 300, 3 to 800, 4 to 1500, 5 to 3000)

    // ── Inicializar ───────────────────────────────────────────────────

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        try {
            val binary = findExecutable() ?: return@withContext false

            Log.d(TAG, "Starting Stockfish from: ${binary.absolutePath}")

            val pb = ProcessBuilder(binary.absolutePath).apply {
                redirectErrorStream(true)
                directory(context.filesDir)   // directorio de trabajo escribible
            }
            process = pb.start()
            writer  = PrintWriter(process!!.outputStream.bufferedWriter())
            reader  = process!!.inputStream.bufferedReader()

            send("uci")
            waitFor("uciok", 5000) ?: run { Log.e(TAG, "uciok timeout"); return@withContext false }

            // Rendimiento: usamos varios hilos del teléfono y una tabla
            // hash generosa. Esto acelera tanto el juego como el análisis.
            val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)
            send("setoption name Threads value $threads")
            send("setoption name Hash value 64")

            send("isready")
            waitFor("readyok", 5000) ?: run { Log.e(TAG, "readyok timeout"); return@withContext false }

            isReady = true
            Log.d(TAG, "Stockfish ready ✓ (threads=$threads, hash=64MB)")
            true

        } catch (e: Exception) {
            Log.e(TAG, "init failed: ${e.message}")
            false
        }
    }

    // ── Mejor movimiento (modo juego, con fuerza limitada) ────────────

    suspend fun getBestMove(fen: String, difficultyLevel: Int): String? =
        withContext(Dispatchers.IO) {
            if (!isReady) return@withContext null
            try {
                val timeMs = TIME_BY_LEVEL[difficultyLevel] ?: 800

                if (difficultyLevel < 5) {
                    // Skill Level introduce errores deliberados acordes al
                    // nivel; UCI_LimitStrength debe ir apagado para que no
                    // interfiera con el skill elegido.
                    send("setoption name UCI_LimitStrength value false")
                    send("setoption name Skill Level value ${SKILL_BY_LEVEL[difficultyLevel] ?: 8}")
                } else {
                    // Nivel 5: fuerza completa del motor
                    send("setoption name UCI_LimitStrength value false")
                    send("setoption name Skill Level value 20")
                }

                send("setoption name MultiPV value 1")
                send("ucinewgame")
                sync()
                send("position fen $fen")
                send("go movetime $timeMs")

                val result = waitFor("bestmove", (timeMs + 2000).toLong())
                    ?: return@withContext null

                val parts = result.trim().split(" ")
                val idx   = parts.indexOf("bestmove")
                if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1] else null

            } catch (e: Exception) {
                Log.e(TAG, "getBestMove error: ${e.message}")
                null
            }
        }

    // ── Análisis (modo replay) ────────────────────────────────────────

    /**
     * Prepara el motor para analizar UNA partida completa.
     * Se llama UNA sola vez al comenzar el análisis, no por cada jugada:
     * así la tabla hash se conserva entre posiciones consecutivas
     * (que se parecen mucho entre sí) y el análisis es mucho más rápido.
     */
    suspend fun startAnalysisSession() = withContext(Dispatchers.IO) {
        if (!isReady) return@withContext
        // El análisis SIEMPRE es a fuerza completa, sin importar el
        // nivel de dificultad con el que se jugó la partida.
        send("setoption name UCI_LimitStrength value false")
        send("setoption name Skill Level value 20")
        send("ucinewgame")
        send("isready")
        waitFor("readyok", 3000)
    }

    /**
     * Analiza una posición y devuelve hasta [multiPv] líneas (la 1 es la
     * mejor), cada una con su evaluación y su variante principal (pv).
     *
     * Con [searchMoves] la búsqueda se limita a esas jugadas: así se
     * evalúa la jugada que hizo el jugador desde la MISMA posición y con
     * la MISMA profundidad que la mejor jugada, que es la única forma de
     * comparar ambas sin ruido.
     *
     * Las puntuaciones salen normalizadas a perspectiva BLANCAS.
     */
    suspend fun analyzeLines(
        fen:         String,
        depth:       Int,
        multiPv:     Int = 1,
        searchMoves: List<String> = emptyList(),
        maxTimeMs:   Long = 4000L
    ): List<EngineLine> = withContext(Dispatchers.IO) {
        if (!isReady) return@withContext emptyList()
        try {
            // Descarta cualquier salida pendiente de una búsqueda anterior
            // (por ejemplo un "bestmove" que llegó tarde). Sin esto, la
            // siguiente búsqueda podía leer el resultado de la anterior.
            if (!sync()) return@withContext emptyList()

            send("setoption name MultiPV value ${multiPv.coerceIn(1, 5)}")
            send("position fen $fen")
            val goCmd = buildString {
                append("go depth $depth movetime $maxTimeMs")
                if (searchMoves.isNotEmpty()) append(" searchmoves ").append(searchMoves.joinToString(" "))
            }
            send(goCmd)

            val byPv = sortedMapOf<Int, EngineLine>()
            var gotBestMove = false
            val deadline = System.currentTimeMillis() + maxTimeMs + 3000L

            loop@ while (System.currentTimeMillis() < deadline) {
                if (reader?.ready() == true) {
                    val line = reader?.readLine() ?: break@loop
                    when {
                        line.startsWith("info") && line.contains(" score ") && line.contains(" pv ") -> {
                            // Los límites parciales de la ventana de búsqueda no son evaluaciones reales
                            if (line.contains(" lowerbound") || line.contains(" upperbound")) continue@loop
                            val idx  = parseToken(line, "multipv") ?: 1
                            val d    = parseToken(line, "depth") ?: 0
                            val cp   = parseToken(line, "score cp")
                            val mate = parseToken(line, "score mate")
                            val pv   = line.substringAfter(" pv ").trim().split(" ").filter { it.isNotBlank() }
                            byPv[idx] = EngineLine(cp ?: 0, mate, d, pv)
                        }
                        line.startsWith("bestmove") -> { gotBestMove = true; break@loop }
                    }
                } else {
                    Thread.sleep(5)
                }
            }
            if (!gotBestMove) {
                // El motor sigue pensando: lo detenemos y vaciamos la salida
                send("stop")
                waitFor("bestmove", 2000)
            }
            send("setoption name MultiPV value 1")

            val blackToMove = fen.split(" ").getOrNull(1) == "b"
            byPv.values.map { normalize(it, blackToMove) }
        } catch (e: Exception) {
            Log.e(TAG, "analyzeLines error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Una sola línea: evaluación + mejor jugada. Se mantiene por
     * compatibilidad; usa la misma búsqueda que analyzeLines.
     */
    suspend fun analyzePosition(fen: String, depth: Int = 12): EngineAnalysis {
        val line = analyzeLines(fen, depth, multiPv = 1).firstOrNull()
            ?: return EngineAnalysis(0, null, null, 0)
        return EngineAnalysis(line.scoreCp, line.mateIn, line.pv.firstOrNull(), line.depth)
    }

    /**
     * Normalización de perspectiva: UCI reporta la puntuación desde el
     * punto de vista del JUGADOR QUE MUEVE. Si mueven las negras se
     * invierte, para que el resultado sea siempre "blancas positivo".
     * Los mates también se reflejan en scoreCp con un valor enorme.
     */
    private fun normalize(raw: EngineLine, blackToMove: Boolean): EngineLine {
        val cp   = if (blackToMove) -raw.scoreCp else raw.scoreCp
        val mate = raw.mateIn?.let { if (blackToMove) -it else it }
        val finalCp = when {
            mate != null && mate > 0  ->  30000 - mate
            mate != null && mate < 0  -> -30000 - mate
            // "mate 0": el lado que mueve ya está mateado
            mate == 0                 -> if (blackToMove) 30000 else -30000
            else                      -> cp
        }
        return raw.copy(scoreCp = finalCp, mateIn = mate?.takeIf { it != 0 })
    }

    /** isready / readyok: deja el canal limpio antes de una búsqueda nueva. */
    private fun sync(): Boolean {
        send("isready")
        return waitFor("readyok", 3000) != null
    }

    /**
     * COMPATIBILIDAD: mantiene la firma antigua. Usa la búsqueda
     * unificada y la perspectiva corregida.
     */
    @Deprecated("Usar analyzeLines(), que además devuelve la variante")
    suspend fun evaluatePosition(fen: String): Int = analyzePosition(fen).scoreCp

    // ── Cleanup ───────────────────────────────────────────────────────

    fun quit() {
        try { send("quit"); process?.destroy() } catch (_: Exception) {}
        process = null; writer = null; reader = null; isReady = false
    }

    fun isAvailable() = isReady

    // ── Internos ──────────────────────────────────────────────────────

    private fun send(cmd: String) { writer?.println(cmd); writer?.flush() }

    /**
     * Extrae el número que sigue a una etiqueta en una línea UCI.
     * Ej: parseToken("info depth 12 score cp -35", "score cp") → -35
     */
    private fun parseToken(line: String, token: String): Int? {
        val idx = line.indexOf(" $token ")
        if (idx < 0) return null
        return line.substring(idx + token.length + 2)
            .trim().split(" ").firstOrNull()?.toIntOrNull()
    }

    private fun waitFor(token: String, timeoutMs: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (reader?.ready() == true) {
                val line = reader?.readLine() ?: return null
                if (line.contains(token)) return line
            } else Thread.sleep(10)
        }
        return null
    }

    /**
     * Busca libstockfish.so en nativeLibraryDir — la única ubicación
     * que Android/SELinux permite ejecutar en Android 10+.
     */
    private fun findExecutable(): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val binary = File(nativeDir, "libstockfish.so")

        if (!binary.exists()) {
            Log.e(TAG, "libstockfish.so NOT FOUND en $nativeDir. Check:")
            Log.e(TAG, "  1. Archivo en app/src/main/jniLibs/arm64-v8a/libstockfish.so")
            Log.e(TAG, "  2. build.gradle: packagingOptions { jniLibs { useLegacyPackaging = true } }")
            Log.e(TAG, "  3. build.gradle: ndk { abiFilters 'arm64-v8a' }")
            logAvailableLibraries(nativeDir)
            return null
        }

        if (!binary.canExecute()) {
            Log.e(TAG, "libstockfish.so existe pero NO es ejecutable")
            return null
        }

        return binary
    }

    private fun logAvailableLibraries(nativeDir: String) {
        val dir = File(nativeDir)
        if (!dir.exists()) { Log.d(TAG, "nativeLibraryDir no existe"); return }
        val files = dir.listFiles()
        if (files.isNullOrEmpty()) { Log.d(TAG, "nativeLibraryDir está VACÍO"); return }
        Log.d(TAG, "Archivos en nativeLibraryDir (${files.size}):")
        files.forEach { Log.d(TAG, "  ${it.name}  (${it.length()} bytes)") }
    }

    companion object {
        private const val TAG = "StockfishEngine"
    }
}
