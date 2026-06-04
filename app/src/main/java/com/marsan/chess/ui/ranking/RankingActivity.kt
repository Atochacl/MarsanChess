package com.marsan.chess.ui.ranking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class RankingActivity : AppCompatActivity() {
    companion object {
        fun start(context: Context) {}
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
