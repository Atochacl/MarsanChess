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

                send("ucinewgame")
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
     * Analiza una posición con UNA SOLA búsqueda que devuelve a la vez:
     *   - la puntuación (normalizada a perspectiva de blancas)
     *   - la mejor jugada
     *   - si hay mate forzado y en cuántas jugadas
     *
     * Reemplaza el patrón antiguo de llamar evaluatePosition() +
     * getBestMove() por separado, que hacía DOS búsquedas completas
     * por posición (el doble de lento).
     *
     * @param depth profundidad de búsqueda. Sugerencia:
     *   - 10 para pasada rápida de clasificación
     *   - 14–16 para re-analizar jugadas críticas
     */
    suspend fun analyzePosition(fen: String, depth: Int = 12): EngineAnalysis =
        withContext(Dispatchers.IO) {
            if (!isReady) return@withContext EngineAnalysis(0, null, null, 0)
            try {
                send("position fen $fen")
                send("go depth $depth")

                var lastCp    = 0
                var lastMate: Int? = null
                var lastDepth = 0
                var bestMove: String? = null

                // Tiempo máximo de seguridad proporcional a la profundidad
                val deadline = System.currentTimeMillis() + 3000L + depth * 1000L

                loop@ while (System.currentTimeMillis() < deadline) {
                    if (reader?.ready() == true) {
                        val line = reader?.readLine() ?: break@loop
                        when {
                            line.startsWith("info") && line.contains(" score ") -> {
                                parseToken(line, "depth")?.let { lastDepth = it }
                                val cp = parseToken(line, "score cp")
                                if (cp != null) { lastCp = cp; lastMate = null }
                                val mate = parseToken(line, "score mate")
                                if (mate != null) { lastMate = mate }
                            }
                            line.startsWith("bestmove") -> {
                                bestMove = line.trim().split(" ").getOrNull(1)
                                    ?.takeIf { it != "(none)" }
                                break@loop
                            }
                        }
                    } else {
                        Thread.sleep(5)
                    }
                }

                // ── Normalización de perspectiva ─────────────────────
                // UCI reporta la puntuación desde el punto de vista del
                // JUGADOR QUE MUEVE. Si mueven las negras, la invertimos
                // para que el resultado sea siempre "blancas positivo".
                val blackToMove = fen.split(" ").getOrNull(1) == "b"
                val normCp   = if (blackToMove) -lastCp else lastCp
                val normMate = lastMate?.let { if (blackToMove) -it else it }

                // Los mates también se reflejan en scoreCp con un valor
                // enorme, para que las comparaciones numéricas funcionen.
                val finalCp = when {
                    normMate != null && normMate > 0 ->  30000 - normMate
                    normMate != null && normMate < 0 -> -30000 - normMate
                    else -> normCp
                }

                EngineAnalysis(finalCp, normMate, bestMove, lastDepth)

            } catch (e: Exception) {
                Log.e(TAG, "analyzePosition error: ${e.message}")
                EngineAnalysis(0, null, null, 0)
            }
        }

    /**
     * COMPATIBILIDAD: mantiene la firma antigua mientras migramos
     * GameReplayActivity al nuevo analyzePosition(). Internamente ya
     * usa la búsqueda unificada y la perspectiva corregida.
     */
    @Deprecated("Usar analyzePosition(), que además devuelve la mejor jugada")
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
