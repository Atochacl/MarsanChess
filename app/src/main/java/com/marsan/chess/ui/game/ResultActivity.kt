package com.marsan.chess.ui.game

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.marsan.chess.data.model.*
import com.marsan.chess.databinding.ActivityResultBinding
import com.marsan.chess.util.toTimeString
import com.marsan.chess.util.slideInFromBottom

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    companion object {
        const val EXTRA_RESULT      = "result"
        const val EXTRA_POINTS      = "points"
        const val EXTRA_PLAYER_TIME = "player_time"
        const val EXTRA_CPU_TIME    = "cpu_time"
        const val EXTRA_PLAYER_NAME = "player_name"
        const val EXTRA_DIFFICULTY  = "difficulty"
        const val EXTRA_MODE        = "mode"
        const val EXTRA_RIVAL_ID    = "rival_id"

        fun start(
            context: Context,
            result: GameResult,
            points: Int,
            playerTime: Long,
            cpuTime: Long,
            playerName: String,
            difficulty: Difficulty,
            mode: GameMode,
            rivalId: Int? = null
        ) {
            context.startActivity(Intent(context, ResultActivity::class.java).apply {
                putExtra(EXTRA_RESULT,      result.name)
                putExtra(EXTRA_POINTS,      points)
                putExtra(EXTRA_PLAYER_TIME, playerTime)
                putExtra(EXTRA_CPU_TIME,    cpuTime)
                putExtra(EXTRA_PLAYER_NAME, playerName)
                putExtra(EXTRA_DIFFICULTY,  difficulty.level)
                putExtra(EXTRA_MODE,        mode.name)
                rivalId?.let { putExtra(EXTRA_RIVAL_ID, it) }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val result     = GameResult.valueOf(intent.getStringExtra(EXTRA_RESULT) ?: "DRAW")
        val points     = intent.getIntExtra(EXTRA_POINTS, 0)
        val playerTime = intent.getLongExtra(EXTRA_PLAYER_TIME, 0L)
        val cpuTime    = intent.getLongExtra(EXTRA_CPU_TIME,    0L)
        val playerName = intent.getStringExtra(EXTRA_PLAYER_NAME) ?: "Jugador"
        val difficulty = Difficulty.fromLevel(intent.getIntExtra(EXTRA_DIFFICULTY, 3))
        val mode       = GameMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "NORMAL")

        setupUI(result, points, playerTime, cpuTime)
        setupButtons(playerName, difficulty, mode)
        animateEntrance()
    }

    private fun setupUI(result: GameResult, points: Int, playerTime: Long, cpuTime: Long) {
        binding.tvResultIcon.text = when (result) {
            GameResult.WIN    -> "🏆"
            GameResult.LOSS   -> "♟"
            GameResult.RESIGN -> "🏳"
            else              -> "🤝"
        }
        binding.tvResultTitle.text = when (result) {
            GameResult.WIN    -> "¡Ganaste!"
            GameResult.LOSS   -> "Perdiste"
            GameResult.RESIGN -> "Partida Terminada"
            else              -> "Tablas"
        }
        binding.tvResultPoints.text = if (points > 0) "+$points puntos" else ""
        binding.tvPlayerTime.text   = playerTime.toTimeString()
        binding.tvCpuTime.text      = cpuTime.toTimeString()
    }

    private fun setupButtons(playerName: String, difficulty: Difficulty, mode: GameMode) {
        binding.btnPlayAgain.setOnClickListener {
            GameActivity.start(this, playerName, difficulty)
            finish()
        }
        binding.btnMainMenu.setOnClickListener {
            val intent = Intent(this, com.marsan.chess.ui.main.MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun animateEntrance() {
        binding.tvResultIcon.slideInFromBottom()
        binding.tvResultTitle.postDelayed({ binding.tvResultTitle.slideInFromBottom() }, 80)
        binding.cardTimes.postDelayed({ binding.cardTimes.slideInFromBottom() }, 160)
        binding.btnPlayAgain.postDelayed({ binding.btnPlayAgain.slideInFromBottom() }, 220)
        binding.btnMainMenu.postDelayed({ binding.btnMainMenu.slideInFromBottom() }, 260)
    }
}
