package com.marsan.chess.util

import android.content.Context
import android.content.SharedPreferences
import com.marsan.chess.data.model.PlayerPieceColor

class PreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var playerName: String
        get()      = prefs.getString(KEY_PLAYER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PLAYER_NAME, value).apply()

    var nameAsked: Boolean
        get()      = prefs.getBoolean(KEY_NAME_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_NAME_ASKED, value).apply()

    var playerPieceColor: PlayerPieceColor
        get() {
            val name = prefs.getString(KEY_PIECE_COLOR, PlayerPieceColor.WHITE.name)
                ?: PlayerPieceColor.WHITE.name
            return runCatching { PlayerPieceColor.valueOf(name) }.getOrDefault(PlayerPieceColor.WHITE)
        }
        set(value) = prefs.edit().putString(KEY_PIECE_COLOR, value.name).apply()

    var pieceStyle: Int
        get()      = prefs.getInt(KEY_PIECE_STYLE, 3)   // 3 = piezas estilo Lichess por defecto
        set(value) = prefs.edit().putInt(KEY_PIECE_STYLE, value).apply()

    var activePaletteIndex: Int
        get()      = prefs.getInt(KEY_PALETTE_IDX, 0)
        set(value) = prefs.edit().putInt(KEY_PALETTE_IDX, value).apply()

    var lastDifficulty: Int
        get()      = prefs.getInt(KEY_LAST_DIFFICULTY, 3)
        set(value) = prefs.edit().putInt(KEY_LAST_DIFFICULTY, value).apply()

    var selectedLanguage: String
        get()      = prefs.getString(KEY_LANGUAGE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** Si true, cada partida se graba con todos sus movimientos */
    var recordingEnabled: Boolean
        get()      = prefs.getBoolean(KEY_RECORDING, true)
        set(value) = prefs.edit().putBoolean(KEY_RECORDING, value).apply()

    companion object {
        private const val PREFS_NAME          = "marsan_chess_prefs"
        private const val KEY_PLAYER_NAME     = "player_name"
        private const val KEY_NAME_ASKED      = "name_asked"
        private const val KEY_PIECE_COLOR     = "piece_color"
        private const val KEY_PIECE_STYLE     = "piece_style"
        private const val KEY_PALETTE_IDX     = "palette_idx"
        private const val KEY_LAST_DIFFICULTY = "last_difficulty"
        private const val KEY_LANGUAGE        = "selected_language"
        private const val KEY_RECORDING       = "recording_enabled"

        @Volatile private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager =
            INSTANCE ?: synchronized(this) {
                PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
