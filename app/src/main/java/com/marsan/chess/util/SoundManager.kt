package com.marsan.chess.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.marsan.chess.R

/**
 * Reproduce sonidos de ajedrez usando SoundPool (baja latencia).
 *
 * Sonidos usados (archivos en res/raw/):
 *   chess_move.ogg     → pieza se mueve a casilla vacía
 *   chess_capture.ogg  → pieza captura a otra
 *   chess_check.ogg    → jaque al rey
 *   chess_end.ogg      → fin de partida (jaque mate / tablas)
 *   chess_castle.ogg   → enroque
 *
 * Descarga gratuita recomendada: https://www.freesound.org
 * Busca "chess move wood" o similar, licencia CC0.
 * Alternativa: los sonidos de Lichess (Apache 2.0) en
 * https://github.com/lichess-org/lila/tree/master/public/sound/standard
 */
class SoundManager private constructor(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var idMove    = 0
    private var idCapture = 0
    private var idCheck   = 0
    private var idEnd     = 0
    private var idCastle  = 0
    private var loaded    = false

    private var enabled = true   // se puede silenciar desde preferencias

    init {
        try {
            idMove    = pool.load(context, R.raw.chess_move,    1)
            idCapture = pool.load(context, R.raw.chess_capture, 1)
            idCheck   = pool.load(context, R.raw.chess_check,   1)
            idEnd     = pool.load(context, R.raw.chess_end,     1)
            idCastle  = pool.load(context, R.raw.chess_castle,  1)
            pool.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) loaded = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sound files not found — audio disabled. ${e.message}")
        }
    }

    fun playMove()    = play(idMove)
    fun playCapture() = play(idCapture)
    fun playCheck()   = play(idCheck)
    fun playEnd()     = play(idEnd)
    fun playCastle()  = play(idCastle)

    fun setEnabled(on: Boolean) { enabled = on }

    private fun play(soundId: Int) {
        if (!enabled || !loaded || soundId == 0) return
        pool.play(soundId, 0.8f, 0.8f, 1, 0, 1.0f)
    }

    fun release() {
        pool.release()
        loaded = false
    }

    companion object {
        private const val TAG = "SoundManager"

        @Volatile private var INSTANCE: SoundManager? = null

        fun getInstance(context: Context): SoundManager =
            INSTANCE ?: synchronized(this) {
                SoundManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
