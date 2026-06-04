package com.marsan.chess.util

import android.content.Context
import androidx.core.content.edit
import com.marsan.chess.data.model.PlayerPieceColor

class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var playerName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_NAME, value) }

    var lastDifficulty: Int
        get() = prefs.getInt(KEY_DIFFICULTY, 3)
        set(value) = prefs.edit { putInt(KEY_DIFFICULTY, value) }

    var playerPieceColor: PlayerPieceColor
        get() = PlayerPieceColor.valueOf(
            prefs.getString(KEY_PIECE_COLOR, PlayerPieceColor.WHITE.name) ?: PlayerPieceColor.WHITE.name)
        set(value) = prefs.edit { putString(KEY_PIECE_COLOR, value.name) }

    var tutorialCompleted: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_TUTORIAL_DONE, value) }

    var tutorialSuggested: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_SUGGESTED, false)
        set(value) = prefs.edit { putBoolean(KEY_TUTORIAL_SUGGESTED, value) }

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit { putBoolean(KEY_FIRST_LAUNCH, value) }

    // true = ya se le pidió el nombre al menos una vez
    var nameAsked: Boolean
        get() = prefs.getBoolean(KEY_NAME_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_NAME_ASKED, value) }

    companion object {
        private const val PREFS_NAME             = "marsan_chess_prefs"
        private const val KEY_NAME               = "player_name"
        private const val KEY_DIFFICULTY         = "last_difficulty"
        private const val KEY_TUTORIAL_DONE      = "tutorial_completed"
        private const val KEY_TUTORIAL_SUGGESTED = "tutorial_suggested"
        private const val KEY_FIRST_LAUNCH       = "first_launch"
        private const val KEY_NAME_ASKED         = "name_asked"
        private const val KEY_PIECE_COLOR        = "player_piece_color"

        @Volatile private var INSTANCE: PreferencesManager? = null
        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
