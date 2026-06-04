package com.marsan.chess.ui.game

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.marsan.chess.data.model.*
import com.marsan.chess.data.repository.GameRepository
import com.marsan.chess.databinding.ActivityGameBinding
import com.marsan.chess.engine.*
import com.marsan.chess.util.PreferencesManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private lateinit var controller: GameController
    private lateinit var repository: GameRepository
    private lateinit var prefs: PreferencesManager

    private lateinit var playerName: String
    private lateinit var difficulty: Difficulty
    private var resultShown = false
    private var playerPieceColor: PieceColor = PieceColor.WHITE
    private lateinit var currentPalette: BoardPalette

    // Centésimas
    private val clockHandler      = Handler(Looper.getMainLooper())
    private var playerCentesimals = 0L
    private var cpuCentesimals    = 0L
    private var isPlayerTurnClock = true
    private var clockRunning      = false
    private var previousBoard: Board?   = null
    private var previousLastMove: Move? = null

    private val clockRunnable = object : Runnable {
        override fun run() {
            if (!clockRunning) return
            if (isPlayerTurnClock) { playerCentesimals++; binding.tvClockPlayer.text = formatTime(playerCentesimals) }
            else                   { cpuCentesimals++;    binding.tvClockCpu.text    = formatTime(cpuCentesimals) }
            clockHandler.postDelayed(this, 10)
        }
    }

    companion object {
        const val EXTRA_PLAYER_NAME = "player_name"
        const val EXTRA_DIFFICULTY  = "difficulty"

        fun start(context: Context, playerName: String, difficulty: Difficulty) {
            context.startActivity(Intent(context, GameActivity::class.java).apply {
                putExtra(EXTRA_PLAYER_NAME, playerName)
                putExtra(EXTRA_DIFFICULTY,  difficulty.level)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding    = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs      = PreferencesManager.getInstance(this)
        playerName = intent.getStringExtra(EXTRA_PLAYER_NAME) ?: prefs.playerName.ifEmpty { "Jugador" }
        difficulty = Difficulty.fromLevel(intent.getIntExtra(EXTRA_DIFFICULTY, 3))
        repository = GameRepository.getInstance(this)

        playerPieceColor = when (prefs.playerPieceColor) {
            PlayerPieceColor.WHITE  -> PieceColor.WHITE
            PlayerPieceColor.BLACK  -> PieceColor.BLACK
            PlayerPieceColor.RANDOM -> if (kotlin.random.Random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
        }

        currentPalette = BoardPalettes.random()
        applyPaletteToUI(currentPalette)

        binding.boardView.palette     = currentPalette
        binding.boardView.playerColor = playerPieceColor

        setupController()
        setupViews()
        observeState()
        controller.startGame()
    }

    // Aplica colores de paleta a la UI fuera del tablero
    private fun applyPaletteToUI(palette: BoardPalette) {
        val light = palette.lightColor
        val dark  = palette.darkColor

        binding.rootLayout.setBackgroundColor(light)
        binding.zoneCpu.setBackgroundColor(light)
        binding.zonePlayer.setBackgroundColor(light)
        binding.cardClockCpu.setCardBackgroundColor(dark)
        binding.cardClockPlayer.setCardBackgroundColor(dark)

        // Texto de relojes en color claro sobre fondo oscuro
        binding.tvCpuName.setTextColor(light)
        binding.tvClockCpu.setTextColor(light)
        binding.tvPlayerName.setTextColor(light)
        binding.tvClockPlayer.setTextColor(light)

        // Botones con color de paleta
        val btnBg = android.graphics.drawable.GradientDrawable().apply {
            shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = (8 * resources.displayMetrics.density)
            setColor(light)
            setStroke((1.5f * resources.displayMetrics.density).toInt(), dark)
        }
        val btnBgActive = android.graphics.drawable.GradientDrawable().apply {
            shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = (8 * resources.displayMetrics.density)
            setColor(dark)
            setStroke((1.5f * resources.displayMetrics.density).toInt(), dark)
        }
        binding.btnUndo.background   = btnBg.constantState?.newDrawable()?.mutate()
        binding.btnResign.background = btnBg.constantState?.newDrawable()?.mutate()
        binding.btnUndo.setTextColor(dark)
        binding.btnResign.setTextColor(dark)
    }

    private fun setupController() {
        controller = GameController(
            playerColor = playerPieceColor,
            difficulty  = difficulty,
            rival       = null,
            scope       = lifecycleScope
        )
    }

    private fun setupViews() {
        binding.tvPlayerName.text = playerName
        binding.tvCpuName.text    = "CPU"
        binding.boardView.onSquareTapped = { row, col -> controller.onSquareTapped(row, col) }
        binding.btnResign.setOnClickListener { confirmResign() }
        binding.btnUndo.setOnClickListener   { controller.undoMove() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            controller.state.collectLatest { state ->

                // Animación
                val newMove = state.lastMove
                if (newMove != null && newMove != previousLastMove) {
                    binding.boardView.animBoard = state.board
                    binding.boardView.startMoveAnimation(newMove, previousBoard ?: state.board)
                    previousLastMove = newMove
                } else { binding.boardView.animBoard = null }
                previousBoard = state.board.copy()

                binding.boardView.board          = state.board
                binding.boardView.selectedSquare = state.selectedSquare
                binding.boardView.legalMoves     = state.legalMovesForSelected
                binding.boardView.lastMove       = state.lastMove
                binding.boardView.checkAttackers = state.checkAttackers
                binding.boardView.kingInCheck    = if (state.status == GameStatus.CHECK ||
                                                       state.status == GameStatus.CHECKMATE)
                    state.board.kingPos(state.currentTurn) else null

                // Piezas comidas FUERA del tablero
                renderCaptured(binding.layoutCapturedByCpu,    state.capturedByCpu)
                renderCaptured(binding.layoutCapturedByPlayer, state.capturedByPlayer)

                // Reloj
                val playerActive = state.currentTurn == playerPieceColor && !state.isThinking
                if (isPlayerTurnClock != playerActive) isPlayerTurnClock = playerActive
                if (!clockRunning && state.result == GameResult.ONGOING) {
                    clockRunning = true; clockHandler.post(clockRunnable)
                }
                updateClockColors(playerActive)

                binding.btnUndo.isEnabled = state.boardHistory.size >= 3 &&
                                            state.result == GameResult.ONGOING && !state.isThinking
                binding.layoutThinking.visibility = if (state.isThinking) View.VISIBLE else View.GONE

                if (state.result != GameResult.ONGOING && !resultShown) {
                    resultShown = true; stopClock(); saveAndShowResult(state)
                }
            }
        }
    }

    // Renderiza piezas comidas como ImageViews pequeños fuera del tablero
    private fun renderCaptured(container: LinearLayout, pieces: List<Piece>) {
        container.removeAllViews()
        val size = (22 * resources.displayMetrics.density).toInt()
        val margin = (2 * resources.displayMetrics.density).toInt()
        pieces.sortedByDescending { it.value }.forEach { piece ->
            val iv = ImageView(this).apply {
                val resName = if (piece.color == PieceColor.WHITE) {
                    "piece_w_${piece.type.name.lowercase()}"
                } else {
                    "piece_b_${piece.type.name.lowercase()}"
                }
                val resId = resources.getIdentifier(resName, "drawable", packageName)
                if (resId != 0) setImageResource(resId)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(0, 0, margin, 0)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            container.addView(iv)
        }
    }

    private fun stopClock() {
        clockRunning = false; clockHandler.removeCallbacks(clockRunnable)
    }

    private fun formatTime(cs: Long): String {
        val s = cs / 100; return "%02d:%02d.%02d".format(s / 60, s % 60, cs % 100)
    }

    private fun updateClockColors(playerActive: Boolean) {
        val dark  = currentPalette.darkColor
        val light = currentPalette.lightColor
        binding.cardClockPlayer.setCardBackgroundColor(if (playerActive) dark else light)
        binding.tvPlayerName.setTextColor(if (playerActive) light else dark)
        binding.tvClockPlayer.setTextColor(if (playerActive) light else dark)
        binding.cardClockCpu.setCardBackgroundColor(if (!playerActive) dark else light)
        binding.tvCpuName.setTextColor(if (!playerActive) light else dark)
        binding.tvClockCpu.setTextColor(if (!playerActive) light else dark)
    }

    private fun saveAndShowResult(state: GameState) {
        val points = if (state.result == GameResult.WIN)
            repository.calculatePoints(GameMode.NORMAL, difficulty, null) else 0
        lifecycleScope.launch {
            repository.saveGameResult(GameSession(
                playerName = playerName, mode = GameMode.NORMAL, difficulty = difficulty,
                result = state.result, playerTimeSeconds = playerCentesimals / 100,
                cpuTimeSeconds = cpuCentesimals / 100, pointsEarned = points))
            kotlinx.coroutines.delay(400)
            ResultActivity.start(this@GameActivity, state.result, points,
                playerCentesimals / 100, cpuCentesimals / 100,
                playerName, difficulty, GameMode.NORMAL, null)
            finish()
        }
    }

    private fun confirmResign() {
        AlertDialog.Builder(this)
            .setTitle("Abandonar")
            .setMessage("¿Seguro que quieres abandonar?")
            .setPositiveButton("Sí") { _, _ ->
                stopClock()
                AlertDialog.Builder(this).setTitle("Partida Terminada")
                    .setMessage("Has abandonado la partida.").setCancelable(false)
                    .setPositiveButton("Menú principal") { _, _ -> goToMainMenu() }
                    .setNegativeButton("Nueva partida") { _, _ ->
                        GameActivity.start(this, playerName, difficulty); finish()
                    }.show()
            }
            .setNegativeButton("Seguir jugando", null).show()
    }

    private fun goToMainMenu() {
        startActivity(Intent(this, com.marsan.chess.ui.main.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { confirmResign() }

    override fun onDestroy() { super.onDestroy(); stopClock() }
}
