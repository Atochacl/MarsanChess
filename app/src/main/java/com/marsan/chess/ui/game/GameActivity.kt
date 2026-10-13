package com.marsan.chess.ui.game

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.marsan.chess.R
import com.marsan.chess.data.model.*
import com.marsan.chess.data.repository.GameRepository
import com.marsan.chess.databinding.ActivityGameBinding
import com.marsan.chess.databinding.LayoutGameEndOverlayBinding
import com.marsan.chess.engine.*
import com.marsan.chess.engine.PieceColor
import com.marsan.chess.ui.BaseActivity
import com.marsan.chess.ui.main.AppPalette
import com.marsan.chess.ui.main.AppPalettes
import com.marsan.chess.util.PreferencesManager
import com.marsan.chess.util.SoundManager
import com.marsan.chess.data.repository.GameRecordRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GameActivity : BaseActivity() {

    private lateinit var binding: ActivityGameBinding
    private lateinit var controller: GameController
    private lateinit var repository: GameRepository
    private lateinit var prefs: PreferencesManager
    private lateinit var soundManager: SoundManager
    private lateinit var recordRepo: GameRecordRepository

    private lateinit var playerName: String
    private lateinit var difficulty: Difficulty
    private var timeLimitSeconds: Long       = 0L
    private var gameMode: GameMode           = GameMode.NORMAL
    private var resultSaved                  = false
    private var playerPieceColor: PieceColor = PieceColor.WHITE
    private lateinit var appPalette: AppPalette
    private lateinit var boardPalette: BoardPalette

    // Modo 2 jugadores
    private var twoPlayerMode = false
    private var player1Color: PieceColor = PieceColor.WHITE   // color del Jugador 1

    // Reloj cosmético para partidas SIN límite (centésimas)
    private val clockHandler      = Handler(Looper.getMainLooper())
    private var playerCentesimals = 0L
    private var cpuCentesimals    = 0L
    private var isPlayerTurnClock = true
    private var clockRunning      = false

    private var previousBoard: Board?   = null
    private var previousLastMove: Move? = null

    // Reloj cosmético (solo partidas sin límite de tiempo)
    private val clockRunnable = object : Runnable {
        override fun run() {
            if (!clockRunning) return
            if (isPlayerTurnClock) {
                playerCentesimals++
                binding.tvClockPlayer.text = formatCs(playerCentesimals)
            } else {
                cpuCentesimals++
                binding.tvClockCpu.text = formatCs(cpuCentesimals)
            }
            clockHandler.postDelayed(this, 10)
        }
    }

    companion object {
        private const val EXTRA_PLAYER_NAME    = "player_name"
        private const val EXTRA_DIFFICULTY     = "difficulty"
        private const val EXTRA_TIME_LIMIT_SEC = "time_limit_sec"
        private const val EXTRA_GAME_MODE      = "game_mode"
        private const val EXTRA_PALETTE_IDX    = "palette_idx"

        private const val EXTRA_TWO_PLAYER   = "two_player"
        private const val EXTRA_P1_COLOR      = "p1_color"

        fun start(
            context: Context,
            playerName: String,
            difficulty: Difficulty,
            timeLimitSeconds: Long = 0L,
            mode: GameMode = GameMode.NORMAL,
            paletteIdx: Int = 0
        ) {
            context.startActivity(Intent(context, GameActivity::class.java).apply {
                putExtra(EXTRA_PLAYER_NAME,    playerName)
                putExtra(EXTRA_DIFFICULTY,     difficulty.level)
                putExtra(EXTRA_TIME_LIMIT_SEC, timeLimitSeconds)
                putExtra(EXTRA_GAME_MODE,      mode.name)
                putExtra(EXTRA_PALETTE_IDX,    paletteIdx)
                putExtra(EXTRA_TWO_PLAYER,     false)
            })
        }

        fun startTwoPlayer(
            context: Context,
            paletteIdx: Int = 0,
            player1Color: PieceColor = PieceColor.WHITE
        ) {
            context.startActivity(Intent(context, GameActivity::class.java).apply {
                putExtra(EXTRA_PLAYER_NAME,    "")
                putExtra(EXTRA_DIFFICULTY,     3)
                putExtra(EXTRA_TIME_LIMIT_SEC, 0L)
                putExtra(EXTRA_GAME_MODE,      GameMode.NORMAL.name)
                putExtra(EXTRA_PALETTE_IDX,    paletteIdx)
                putExtra(EXTRA_TWO_PLAYER,     true)
                putExtra(EXTRA_P1_COLOR,       player1Color.name)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs            = PreferencesManager.getInstance(this)
        soundManager     = SoundManager.getInstance(this)
        recordRepo       = GameRecordRepository.getInstance(this)
        playerName       = intent.getStringExtra(EXTRA_PLAYER_NAME) ?: prefs.playerName.ifEmpty { "Jugador" }
        difficulty       = Difficulty.fromLevel(intent.getIntExtra(EXTRA_DIFFICULTY, 3))
        timeLimitSeconds = intent.getLongExtra(EXTRA_TIME_LIMIT_SEC, 0L)
        gameMode         = GameMode.valueOf(intent.getStringExtra(EXTRA_GAME_MODE) ?: "NORMAL")
        repository       = GameRepository.getInstance(this)

        twoPlayerMode = intent.getBooleanExtra(EXTRA_TWO_PLAYER, false)
        player1Color  = try {
            PieceColor.valueOf(intent.getStringExtra(EXTRA_P1_COLOR) ?: "WHITE")
        } catch (_: Exception) { PieceColor.WHITE }

        val paletteIdx = intent.getIntExtra(EXTRA_PALETTE_IDX, prefs.activePaletteIndex)
            .coerceIn(0, AppPalettes.all.size - 1)
        appPalette   = AppPalettes.all[paletteIdx]
        boardPalette = BoardPalette(
            lightColor = appPalette.boardLight,
            darkColor  = appPalette.boardDark
        )

        playerPieceColor = if (twoPlayerMode) {
            player1Color   // en modo 2P, playerColor = color del jugador 1 (blancas van primero)
        } else when (prefs.playerPieceColor) {
            PlayerPieceColor.WHITE  -> PieceColor.WHITE
            PlayerPieceColor.BLACK  -> PieceColor.BLACK
            PlayerPieceColor.RANDOM -> if (kotlin.random.Random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
        }

        applyPaletteToUI()
        binding.boardView.palette     = boardPalette
        binding.boardView.playerColor = playerPieceColor

        controller = GameController(
            playerColor      = playerPieceColor,
            difficulty       = difficulty,
            timeLimitSeconds = timeLimitSeconds,
            scope            = lifecycleScope,
            twoPlayerMode    = twoPlayerMode,
            context          = if (twoPlayerMode) null else this   // sin Stockfish en 2P
        )

        if (twoPlayerMode) {
            val p1Label = getString(R.string.two_players_player1) + " (" +
                if (player1Color == PieceColor.WHITE) getString(R.string.two_players_white)
                else getString(R.string.two_players_black) + ")"
            val p2Color = player1Color.opponent()
            val p2Label = getString(R.string.two_players_player2) + " (" +
                if (p2Color == PieceColor.WHITE) getString(R.string.two_players_white)
                else getString(R.string.two_players_black) + ")"
            binding.tvPlayerName.text = p1Label
            binding.tvCpuName.text    = p2Label
        } else {
            binding.tvPlayerName.text = playerName
            binding.tvCpuName.text    = "CPU"
        }
        binding.boardView.onSquareTapped = { row, col -> controller.onSquareTapped(row, col) }
        binding.btnResign.setOnClickListener { confirmResign() }
        binding.btnUndo.setOnClickListener   { controller.undoMove() }
        binding.btnUndo.text = getString(R.string.game_undo)

        binding.boardView.onPromotionSelected = { pieceType ->
            controller.choosePromotion(pieceType)
        }

        // Mostrar tiempo inicial con centésimas si hay límite
        if (timeLimitSeconds > 0L) {
            val limitStr = formatMsWithCs(timeLimitSeconds * 1000L)
            binding.tvClockPlayer.text = limitStr
            binding.tvClockCpu.text    = limitStr
        } else {
            binding.tvClockPlayer.text = "00:00.00"
            binding.tvClockCpu.text    = "00:00.00"
        }

        // Iniciar grabación si está habilitada — instantáneo, sin coroutine
        if (prefs.recordingEnabled) {
            recordRepo.beginSession(
                playerName  = if (twoPlayerMode) "2P" else playerName,
                mode        = if (twoPlayerMode) "TWO_PLAYER" else gameMode.name,
                difficulty  = difficulty.level,
                playerColor = playerPieceColor.name,
                paletteIdx  = prefs.activePaletteIndex
            )
        }

        observeState()
        controller.startGame()
    }

    // ── PALETA ────────────────────────────────────────────────────────

    private fun applyPaletteToUI() {
        val d    = resources.displayMetrics.density
        val dark = appPalette.boardDark

        binding.rootLayout.setBackgroundColor(appPalette.background)
        binding.zoneCpu.setBackgroundColor(appPalette.background)
        binding.zonePlayer.setBackgroundColor(appPalette.background)
        binding.cardClockCpu.setCardBackgroundColor(dark)
        binding.cardClockPlayer.setCardBackgroundColor(dark)
        binding.tvCpuName.setTextColor(appPalette.boardLight)
        binding.tvClockCpu.setTextColor(appPalette.boardLight)
        binding.tvPlayerName.setTextColor(appPalette.boardLight)
        binding.tvClockPlayer.setTextColor(appPalette.boardLight)

        val btnBg = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 10 * d
            setColor(appPalette.buttonBg)
            setStroke((1.5f * d).toInt(), appPalette.accent)
        }
        binding.btnUndo.background   = btnBg.constantState?.newDrawable()?.mutate()
        binding.btnResign.background = btnBg.constantState?.newDrawable()?.mutate()
        binding.btnUndo.setTextColor(appPalette.buttonText)
        binding.btnResign.setTextColor(appPalette.buttonText)
    }

    // ── OBSERVAR ESTADO ───────────────────────────────────────────────

    private fun observeState() {
        lifecycleScope.launch {
            controller.state.collectLatest { state ->

                val newMove = state.lastMove
                val isNewMove = newMove != null && newMove != previousLastMove
                // Tablero ANTERIOR a la jugada: se necesita para saber si
                // fue captura (después de mover, el destino siempre está
                // ocupado por la pieza que llegó).
                val boardBeforeMove = previousBoard

                if (isNewMove) {
                    binding.boardView.animBoard = state.board
                    binding.boardView.startMoveAnimation(newMove!!, previousBoard ?: state.board)

                    // ── Grabación — ANTES de actualizar previousLastMove ──
                    if (prefs.recordingEnabled) {
                        recordRepo.addMove(newMove)
                    }

                    previousLastMove = newMove
                } else { binding.boardView.animBoard = null }
                previousBoard = state.board.copy()

                // ── Sonidos ──────────────────────────────────────────
                if (isNewMove) {
                    when {
                        newMove.isCastling ->
                            soundManager.playCastle()
                        state.status == com.marsan.chess.engine.GameStatus.CHECK ||
                        state.status == com.marsan.chess.engine.GameStatus.CHECKMATE ->
                            soundManager.playCheck()
                        newMove.isEnPassant ||
                        boardBeforeMove?.get(newMove.toRow, newMove.toCol) != null ->
                            soundManager.playCapture()
                        else ->
                            soundManager.playMove()
                    }
                }
                if (state.result != com.marsan.chess.data.model.GameResult.ONGOING &&
                    !resultSaved) {
                    soundManager.playEnd()
                }

                binding.boardView.board          = state.board
                binding.boardView.selectedSquare = state.selectedSquare
                binding.boardView.legalMoves     = state.legalMovesForSelected
                binding.boardView.lastMove       = state.lastMove
                binding.boardView.checkAttackers = state.checkAttackers
                binding.boardView.kingInCheck    = if (state.status == GameStatus.CHECK ||
                    state.status == GameStatus.CHECKMATE)
                    state.board.kingPos(state.currentTurn) else null
                binding.boardView.pendingPromotion = state.pendingPromotion

                renderCaptured(binding.layoutCapturedByCpu,    state.capturedByCpu)
                renderCaptured(binding.layoutCapturedByPlayer, state.capturedByPlayer)

                if (!twoPlayerMode) {
                    binding.tvCpuName.text = if (state.usingStockfish) "CPU · SF" else "CPU"
                }
                // En modo 2P el nombre ya está puesto en onCreate y no cambia

                // En modo 2P: el "jugador activo" es siempre el que tiene el turno
                val playerActive = if (twoPlayerMode) {
                    state.currentTurn == playerPieceColor && !state.isThinking
                } else {
                    state.currentTurn == playerPieceColor && !state.isThinking
                }
                if (isPlayerTurnClock != playerActive) isPlayerTurnClock = playerActive

                if (timeLimitSeconds > 0L) {
                    // Con límite: tiempo RESTANTE con centésimas (mm:ss.cc)
                    val playerRemaining = (timeLimitSeconds * 1000L - state.playerTimeMs).coerceAtLeast(0)
                    val cpuRemaining    = (timeLimitSeconds * 1000L - state.cpuTimeMs).coerceAtLeast(0)
                    binding.tvClockPlayer.text = formatMsWithCs(playerRemaining)
                    binding.tvClockCpu.text    = formatMsWithCs(cpuRemaining)
                } else {
                    // Sin límite: reloj cosmético de centésimas
                    if (!clockRunning && state.result == GameResult.ONGOING) {
                        clockRunning = true; clockHandler.post(clockRunnable)
                    }
                }

                updateClockColors(playerActive)

                val choosingPromotion = state.pendingPromotion != null
                binding.btnUndo.isEnabled = state.boardHistory.size >= 3 &&
                        state.result == GameResult.ONGOING && !state.isThinking && !choosingPromotion
                binding.btnResign.isEnabled = state.result == GameResult.ONGOING && !choosingPromotion
                binding.layoutThinking.visibility =
                    if (state.isThinking) View.VISIBLE else View.GONE

                if (state.result != GameResult.ONGOING && !resultSaved) {
                    resultSaved = true; stopClock()
                    binding.boardView.onSquareTapped = null
                    binding.btnResign.isEnabled      = false
                    binding.btnUndo.isEnabled        = false
                    saveResult(state)
                    Handler(Looper.getMainLooper()).postDelayed({ showEndOverlay(state) }, 1500)
                }
            }
        }
    }

    // ── OVERLAY FIN DE PARTIDA ────────────────────────────────────────

    private fun showEndOverlay(state: GameState) {
        val ob = LayoutGameEndOverlayBinding.inflate(LayoutInflater.from(this))
        val d  = resources.displayMetrics.density

        if (twoPlayerMode) {
            // ── MODO 2 JUGADORES: mensaje personalizado con nombre del ganador ──
            val isDraw = state.result == GameResult.DRAW

            ob.tvEndIcon.text = if (isDraw) "🤝" else "🏆"

            if (isDraw) {
                ob.tvEndTitle.text = getString(R.string.two_players_draw)
            } else {
                // Determinar quién ganó según resultado vs perspectiva del J1 (playerColor)
                val winnerColor: PieceColor = when (state.result) {
                    GameResult.WIN    -> playerPieceColor              // J1 ganó
                    GameResult.LOSS   -> playerPieceColor.opponent()   // J2 ganó
                    GameResult.RESIGN -> playerPieceColor.opponent()   // J1 abandonó → J2 gana
                    else              -> playerPieceColor
                }
                val winnerIsP1   = winnerColor == player1Color
                val winnerNum    = if (winnerIsP1) getString(R.string.two_players_player1)
                                   else getString(R.string.two_players_player2)
                val winnerColorStr = if (winnerColor == PieceColor.WHITE)
                    getString(R.string.two_players_white)
                else getString(R.string.two_players_black)

                ob.tvEndTitle.text = "$winnerNum (${winnerColorStr})"
            }

            ob.tvEndReason.text = buildReasonText2P(state)

        } else {
            // ── MODO VS CPU ──
            ob.tvEndIcon.text = when (state.result) {
                GameResult.WIN    -> "🏆"; GameResult.LOSS -> "😞"
                GameResult.DRAW   -> "🤝"; GameResult.RESIGN -> "🏳"; else -> "♟"
            }
            ob.tvEndTitle.text = when (state.result) {
                GameResult.WIN    -> getString(R.string.result_win)
                GameResult.LOSS   -> getString(R.string.result_loss)
                GameResult.DRAW   -> getString(R.string.result_draw)
                GameResult.RESIGN -> getString(R.string.end_resign)
                else -> ""
            }
            ob.tvEndReason.text = buildReasonText(state)
        }
        ob.btnEndPlayAgain.text = getString(R.string.end_play_again)
        ob.btnEndMainMenu.text  = getString(R.string.end_main_menu)
        ob.tvEndClose.text      = getString(R.string.end_view_board)

        ob.btnEndPlayAgain.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 12 * d; setColor(appPalette.accent)
        }
        ob.btnEndPlayAgain.setTextColor(appPalette.surface)
        ob.btnEndMainMenu.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 12 * d
            setColor(appPalette.buttonBg); setStroke((1.5f * d).toInt(), appPalette.accent)
        }
        ob.btnEndMainMenu.setTextColor(appPalette.buttonText)
        ob.tvEndClose.setTextColor(appPalette.accent)

        ob.btnEndPlayAgain.setOnClickListener {
            if (twoPlayerMode) {
                val newColor = if (kotlin.random.Random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
                GameActivity.startTwoPlayer(this, prefs.activePaletteIndex, newColor)
            } else {
                GameActivity.start(this, playerName, difficulty, timeLimitSeconds, gameMode,
                    prefs.activePaletteIndex)
            }
            finish()
        }
        ob.btnEndMainMenu.setOnClickListener { goToMainMenu() }
        ob.tvEndClose.setOnClickListener {
            binding.overlayContainer.animate()
                .translationY(binding.overlayContainer.height.toFloat()).alpha(0f).setDuration(250)
                .withEndAction { binding.overlayContainer.visibility = View.GONE; showExitButton() }.start()
        }

        binding.overlayContainer.removeAllViews(); binding.overlayContainer.addView(ob.root)
        binding.overlayContainer.visibility = View.VISIBLE
        binding.overlayContainer.alpha = 0f; binding.overlayContainer.translationY = 120f
        binding.overlayContainer.animate().translationY(0f).alpha(1f).setDuration(350).start()
        binding.zonePlayer.visibility = View.GONE
    }

    private fun showExitButton() {
        val d = resources.displayMetrics.density

        // Ocultar Deshacer, convertir Abandonar en Salir con todo el ancho
        binding.btnUndo.visibility = View.GONE

        binding.btnResign.apply {
            text       = getString(R.string.game_exit_button)
            visibility = View.VISIBLE
            isEnabled  = true
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = 10 * d
                setColor(appPalette.buttonBg)
                setStroke((1.5f * d).toInt(), appPalette.accent)
            }
            setTextColor(appPalette.buttonText)
            setOnClickListener { goToMainMenu() }
        }
        (binding.btnResign.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { lp ->
            lp.weight      = 2f
            lp.marginStart = 0
            binding.btnResign.layoutParams = lp
        }

        // Restaurar zonePlayer → tablero completamente visible
        binding.zonePlayer.visibility = View.VISIBLE
    }

    private fun buildReasonText(state: GameState) = when (state.endReason) {
        GameEndReason.CHECKMATE -> if (state.result == GameResult.WIN)
            getString(R.string.end_checkmate_win) else getString(R.string.end_checkmate_loss)
        GameEndReason.STALEMATE             -> getString(R.string.end_stalemate)
        GameEndReason.INSUFFICIENT_MATERIAL -> getString(R.string.end_insufficient)
        GameEndReason.TIMEOUT -> if (state.result == GameResult.WIN)
            getString(R.string.end_timeout_win) else getString(R.string.end_timeout_loss)
        GameEndReason.RESIGN  -> getString(R.string.end_resign)
        GameEndReason.ONGOING -> ""
    }

    /** Razón de fin específica para modo 2 jugadores — sin referencia a CPU */
    private fun buildReasonText2P(state: GameState): String {
        return when (state.endReason) {
            GameEndReason.CHECKMATE         -> getString(R.string.end_reason_checkmate)
            GameEndReason.STALEMATE         -> getString(R.string.end_reason_stalemate)
            GameEndReason.INSUFFICIENT_MATERIAL -> getString(R.string.end_reason_insufficient)
            GameEndReason.TIMEOUT           -> getString(R.string.end_reason_timeout)
            GameEndReason.RESIGN            -> getString(R.string.end_reason_resign)
            GameEndReason.ONGOING           -> ""
        }
    }

    private fun saveResult(state: GameState) {
        // Grabar partida (todos los modos, incluyendo 2 jugadores)
        if (prefs.recordingEnabled) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                recordRepo.finishRecording(state.result.name)
            }
        }

        if (twoPlayerMode) return   // partidas 2P no van al historial personal

        val points = if (state.result == GameResult.WIN) repository.calculatePoints(difficulty) else 0
        lifecycleScope.launch {
            repository.saveGameResult(GameSession(
                playerName = playerName, mode = gameMode, difficulty = difficulty,
                result = state.result, endReason = state.endReason,
                playerTimeSeconds = state.playerTimeMs / 1000,
                cpuTimeSeconds    = state.cpuTimeMs / 1000,
                pointsEarned = points, timeLimitSeconds = timeLimitSeconds
            ))
        }
    }

    private fun confirmResign() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.game_resign))
            .setMessage(getString(R.string.game_resign_confirm))
            .setPositiveButton(getString(R.string.game_resign_yes)) { _, _ ->
                stopClock()
                if (!resultSaved) {
                    resultSaved = true
                    lifecycleScope.launch {
                        // Grabar abandono (todos los modos)
                        if (prefs.recordingEnabled) {
                            recordRepo.finishRecording("RESIGN")
                            // finishRecording es suspend, ya estamos en una coroutine IO-safe
                        }
                        if (!twoPlayerMode) {
                            repository.saveGameResult(GameSession(
                                playerName = playerName, mode = gameMode, difficulty = difficulty,
                                result = GameResult.RESIGN, endReason = GameEndReason.RESIGN,
                                playerTimeSeconds = playerCentesimals / 100,
                                cpuTimeSeconds    = cpuCentesimals / 100, pointsEarned = 0
                            ))
                        }
                        goToMainMenu()
                    }
                } else goToMainMenu()
            }
            .setNegativeButton(getString(R.string.game_resign_no), null).show()
    }

    private fun goToMainMenu() {
        startActivity(Intent(this, com.marsan.chess.ui.main.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }); finish()
    }

    // ── RELOJ ─────────────────────────────────────────────────────────

    private fun stopClock() { clockRunning = false; clockHandler.removeCallbacks(clockRunnable) }

    /** mm:ss.cc — tiempo restante con centésimas */
    private fun formatMsWithCs(ms: Long): String {
        val totalCs = ms / 10          // milisegundos → centésimas
        val cs      = totalCs % 100
        val totalS  = totalCs / 100
        val s       = totalS % 60
        val m       = totalS / 60
        return "%02d:%02d.%02d".format(m, s, cs)
    }

    /** mm:ss.cc — contador ascendente (partida sin límite) */
    private fun formatCs(cs: Long): String {
        val s = cs / 100; return "%02d:%02d.%02d".format(s / 60, s % 60, cs % 100)
    }

    private fun updateClockColors(playerActive: Boolean) {
        val dark  = appPalette.boardDark
        val light = appPalette.boardLight

        // Reloj jugador
        binding.cardClockPlayer.setCardBackgroundColor(if (playerActive) dark else light)
        binding.tvPlayerName.setTextColor(if (playerActive) light else dark)
        binding.tvClockPlayer.setTextColor(if (playerActive) light else dark)

        // Reloj CPU
        binding.cardClockCpu.setCardBackgroundColor(if (!playerActive) dark else light)
        binding.tvCpuName.setTextColor(if (!playerActive) light else dark)
        binding.tvClockCpu.setTextColor(if (!playerActive) light else dark)

        // Indicador de turno (▶) — visible solo en el reloj activo
        binding.tvTurnIndicatorPlayer.visibility =
            if (playerActive) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvTurnIndicatorCpu.visibility =
            if (!playerActive) android.view.View.VISIBLE else android.view.View.GONE

        binding.tvTurnIndicatorPlayer.setTextColor(light)
        binding.tvTurnIndicatorCpu.setTextColor(light)
    }

    private fun renderCaptured(container: LinearLayout, pieces: List<Piece>) {
        container.removeAllViews()
        val d      = resources.displayMetrics.density
        val size   = (22 * d).toInt()
        val margin = (2  * d).toInt()
        val style  = prefs.pieceStyle

        pieces.sortedByDescending { it.value }.forEach { piece ->
            when (style) {
                2 -> {
                    // Unicode: TextView con el símbolo de la pieza
                    val isWhite = piece.color == PieceColor.WHITE
                    val tv = android.widget.TextView(this).apply {
                        text     = piece.symbol
                        textSize = 13f
                        setTextColor(if (isWhite) 0xFFFFFFFF.toInt() else 0xFF111111.toInt())
                        setShadowLayer(if (isWhite) 1.5f else 0f, 0f, 0f, 0xFF000000.toInt())
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            setMargins(0, 0, margin, 0)
                        }
                        gravity = android.view.Gravity.CENTER
                        includeFontPadding = false
                    }
                    container.addView(tv)
                }
                else -> {
                    // PNG: tipo 1 → piece_*, tipo 3 → piece3_*
                    val prefix  = if (style == 3) "piece3" else "piece"
                    val color   = if (piece.color == PieceColor.WHITE) "w" else "b"
                    val resName = "${prefix}_${color}_${piece.type.name.lowercase()}"
                    val resId   = resources.getIdentifier(resName, "drawable", packageName)
                    val iv = ImageView(this).apply {
                        if (resId != 0) setImageResource(resId)
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            setMargins(0, 0, margin, 0)
                        }
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    container.addView(iv)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.boardView.pendingPromotion != null) return
        confirmResign()
    }

    override fun onDestroy() { super.onDestroy(); stopClock(); controller.release() }
}
