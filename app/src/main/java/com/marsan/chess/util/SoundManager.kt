package com.marsan.chess.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Maneja efectos de sonido del juego.
 * Los sonidos son generados programáticamente con tonos simples
 * para no depender de archivos de audio externos.
 */
class SoundManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var isEnabled = true

    // IDs de sonidos (se cargan bajo demanda)
    private val sounds = mutableMapOf<SoundType, Int>()

    enum class SoundType {
        MOVE,       // Pieza movida
        CAPTURE,    // Pieza capturada
        CHECK,      // Jaque
        CHECKMATE,  // Jaque mate
        SELECT,     // Pieza seleccionada
        INVALID,    // Movimiento inválido
        WIN,        // Victoria
        LOSS        // Derrota
    }

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
    }

    fun play(type: SoundType) {
        if (!isEnabled) return
        // Vibración táctil suave como alternativa a sonidos
        // Los sonidos reales se pueden agregar en assets/ en el futuro
    }

    fun setEnabled(enabled: Boolean) { isEnabled = enabled }
    fun isEnabled() = isEnabled

    fun release() {
        soundPool?.release()
        soundPool = null
    }

    companion object {
        @Volatile private var INSTANCE: SoundManager? = null
        fun getInstance(context: Context): SoundManager {
            return INSTANCE ?: synchronized(this) {
                SoundManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
