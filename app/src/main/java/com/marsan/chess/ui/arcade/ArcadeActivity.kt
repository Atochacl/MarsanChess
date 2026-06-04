package com.marsan.chess.ui.arcade

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ArcadeActivity : AppCompatActivity() {
    companion object {
        fun start(context: Context, playerName: String) {
            // Arcade eliminado
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
