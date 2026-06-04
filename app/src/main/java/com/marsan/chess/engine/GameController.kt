package com.marsan.chess.engine

import com.marsan.chess.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Typealias para evitar problemas de parser con generics anidados
typealias IntPair = Pair<Int, Int>

data class GameState(
    val board: Board                        = Board(),
    val status: GameStatus                  = GameStatus.ONGOING,
    val currentTurn: PieceColor             = PieceColor.WHITE,
    val selectedSquare: IntPair?            = null,
    val legalMovesForSelected: List<Move>   = emptyList(),
    val lastMove: Move?                     = null,
    val playerTimeSeconds: Long             = 0L,
    val cpuTimeSeconds: Long                = 0L,
    val isThinking: Boolean                 = false,
    val result: GameResult                  = GameResult.ONGOING,
    val moveHistory: List<Move>             = emptyList(),
    val boardHistory: List<Board>           = emptyList(),
    val capturedByPlayer: List<Piece>       = emptyList(),
    val capturedByCpu: List<Piece>          = emptyList(),
    val checkAttackers: List<IntPair>       = emptyList()
)

class GameController(
    val playerColor: PieceColor = PieceColor.WHITE,
    private val difficulty: Difficulty = Difficulty.LEVEL_3,
    private val rival: ArcadeRival? = null,
    private val scope: CoroutineScope
) {
    private val engine = ChessEngine()
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    private var playerTimerJob: Job? = null
    private var cpuTimerJob: Job?    = null
    private var playerSeconds = 0L
    private var cpuSeconds    = 0L

    fun startGame() {
        val board = Board().apply { setup() }
        engine.resetMoveCount()
        _state.value = GameState(
            board        = board,
            currentTurn  = PieceColor.WHITE,
            boardHistory = listOf(board.copy())
        )
        startPlayerTimer()
        if (playerColor == PieceColor.BLACK) makeCpuMove()
    }

    // ===================== TOQUE DEL JUGADOR =====================
    fun onSquareTapped(row: Int, col: Int) {
        val s = _state.value
        if (s.isThinking || s.result != GameResult.ONGOING) return
        if (s.currentTurn != playerColor) return

        val piece = s.board[row, col]
        if (s.selectedSquare != null) {
            val move = s.legalMovesForSelected.find { it.toRow == row && it.toCol == col }
            if (move != null) { applyPlayerMove(move); return }
        }
        if (piece?.color == playerColor) {
            val legalMoves = MoveGenerator.generateLegalMoves(s.board, playerColor)
            val movesForPiece = legalMoves.filter { it.fromRow == row && it.fromCol == col }
            _state.value = s.copy(
                selectedSquare        = Pair(row, col),
                legalMovesForSelected = movesForPiece
            )
        } else {
            _state.value = s.copy(selectedSquare = null, legalMovesForSelected = emptyList())
        }
    }

    // ===================== DESHACER JUGADA =====================
    fun undoMove() {
        val s = _state.value
        if (s.isThinking || s.result != GameResult.ONGOING) return
        // Necesitamos deshacer dos movimientos: el del CPU y el del jugador
        val history = s.boardHistory
        if (history.size < 3) return // necesitamos al menos el estado inicial + 2 movimientos

        val newHistory     = history.dropLast(2)
        val restoredBoard  = newHistory.last()
        val newMoveHistory = s.moveHistory.dropLast(minOf(2, s.moveHistory.size))

        // Recalcular piezas comidas desde el historial
        val (capturedPlayer, capturedCpu) = recalculateCaptured(newMoveHistory, restoredBoard)

        val cpuColor = playerColor.opponent()
        val status   = MoveGenerator.getGameStatus(restoredBoard, playerColor)
        val attackers = if (status == GameStatus.CHECK || status == GameStatus.CHECKMATE)
            findCheckAttackers(restoredBoard, playerColor) else emptyList()

        _state.value = s.copy(
            board                 = restoredBoard,
            status                = status,
            currentTurn           = playerColor,
            selectedSquare        = null,
            legalMovesForSelected = emptyList(),
            lastMove              = if (newMoveHistory.isEmpty()) null else newMoveHistory.last(),
            moveHistory           = newMoveHistory,
            boardHistory          = newHistory,
            capturedByPlayer      = capturedPlayer,
            capturedByCpu         = capturedCpu,
            checkAttackers        = attackers,
            result                = GameResult.ONGOING
        )
    }

    private fun recalculateCaptured(moves: List<Move>, currentBoard: Board): Pair<List<Piece>, List<Piece>> {
        // Reconstruir desde cero para obtener capturas precisas
        val startBoard = Board().apply { setup() }
        val capturedByPlayer = mutableListOf<Piece>()
        val capturedByCpu    = mutableListOf<Piece>()
        var b = startBoard.copy()
        for ((index, move) in moves.withIndex()) {
            val captured = b[move.toRow, move.toCol]
            val movingPiece = b[move.fromRow, move.fromCol]
            if (captured != null && movingPiece != null) {
                if (movingPiece.color == playerColor) capturedByPlayer.add(captured)
                else capturedByCpu.add(captured)
            }
            b = b.applyMove(move)
        }
        return Pair(capturedByPlayer, capturedByCpu)
    }

    // ===================== MOVIMIENTO JUGADOR =====================
    private fun applyPlayerMove(move: Move) {
        val s = _state.value
        val captured = s.board[move.toRow, move.toCol]
        val newBoard  = s.board.applyMove(move)
        val history   = s.moveHistory + move
        val boards    = s.boardHistory + newBoard.copy()

        val capturedByPlayer = if (captured != null)
            s.capturedByPlayer + captured else s.capturedByPlayer

        stopPlayerTimer(); startCpuTimer()

        val cpuColor = playerColor.opponent()
        val status   = MoveGenerator.getGameStatus(newBoard, cpuColor)
        val result   = when (status) {
            GameStatus.CHECKMATE -> GameResult.WIN
            GameStatus.STALEMATE, GameStatus.DRAW -> GameResult.DRAW
            else -> GameResult.ONGOING
        }
        val attackers = if (status == GameStatus.CHECK || status == GameStatus.CHECKMATE)
            findCheckAttackers(newBoard, cpuColor) else emptyList()

        _state.value = s.copy(
            board                 = newBoard,
            status                = status,
            currentTurn           = cpuColor,
            selectedSquare        = null,
            legalMovesForSelected = emptyList(),
            lastMove              = move,
            moveHistory           = history,
            boardHistory          = boards,
            capturedByPlayer      = capturedByPlayer,
            checkAttackers        = attackers,
            result                = result
        )

        if (result == GameResult.ONGOING) makeCpuMove()
        else finishGame(result)
    }

    // ===================== MOVIMIENTO CPU =====================
    private fun makeCpuMove() {
        _state.value = _state.value.copy(isThinking = true, checkAttackers = emptyList())
        val cpuColor = playerColor.opponent()

        scope.launch {
            delay(350L + kotlin.random.Random.nextLong(250L))
            val s    = _state.value
            val move = engine.getBestMove(s.board, difficulty, cpuColor, rival)

            if (move == null) {
                val status = MoveGenerator.getGameStatus(s.board, cpuColor)
                finishGame(if (status == GameStatus.CHECKMATE) GameResult.LOSS else GameResult.DRAW)
                return@launch
            }

            val captured = s.board[move.toRow, move.toCol]
            val newBoard = s.board.applyMove(move)
            val history  = s.moveHistory + move
            val boards   = s.boardHistory + newBoard.copy()

            val capturedByCpu = if (captured != null)
                s.capturedByCpu + captured else s.capturedByCpu

            val status   = MoveGenerator.getGameStatus(newBoard, playerColor)
            val result   = when (status) {
                GameStatus.CHECKMATE -> GameResult.LOSS
                GameStatus.STALEMATE, GameStatus.DRAW -> GameResult.DRAW
                else -> GameResult.ONGOING
            }
            val attackers = if (status == GameStatus.CHECK || status == GameStatus.CHECKMATE)
                findCheckAttackers(newBoard, playerColor) else emptyList()

            stopCpuTimer()

            _state.value = s.copy(
                board            = newBoard,
                status           = status,
                currentTurn      = playerColor,
                lastMove         = move,
                moveHistory      = history,
                boardHistory     = boards,
                capturedByCpu    = capturedByCpu,
                checkAttackers   = attackers,
                isThinking       = false,
                result           = result
            )

            if (result == GameResult.ONGOING) startPlayerTimer()
            else finishGame(result)
        }
    }

    // ===================== ATACANTES DE JAQUE =====================
    private fun findCheckAttackers(board: Board, colorInCheck: PieceColor): List<IntPair> {
        val (kingRow, kingCol) = board.kingPos(colorInCheck)
        val attackerColor      = colorInCheck.opponent()
        val attackers          = mutableListOf<IntPair>()
        for (r in 0..7) for (c in 0..7) {
            val piece = board[r, c] ?: continue
            if (piece.color != attackerColor) continue
            val moves = MoveGenerator.generatePseudoLegalMoves(board, attackerColor)
            if (moves.any { it.fromRow == r && it.fromCol == c && it.toRow == kingRow && it.toCol == kingCol }) {
                attackers.add(Pair(r, c))
            }
        }
        return attackers
    }

    // ===================== RENDIRSE =====================
    fun resign() {
        stopAllTimers()
        finishGame(GameResult.RESIGN)
    }

    // ===================== FIN DE PARTIDA =====================
    private fun finishGame(result: GameResult) {
        stopAllTimers()
        _state.value = _state.value.copy(
            result           = result,
            isThinking       = false,
            playerTimeSeconds= playerSeconds,
            cpuTimeSeconds   = cpuSeconds
        )
    }

    // ===================== CRONÓMETROS =====================
    private fun startPlayerTimer() {
        playerTimerJob = scope.launch {
            while (true) { delay(1000L); playerSeconds++; _state.value = _state.value.copy(playerTimeSeconds = playerSeconds) }
        }
    }
    private fun startCpuTimer() {
        cpuTimerJob = scope.launch {
            while (true) { delay(1000L); cpuSeconds++; _state.value = _state.value.copy(cpuTimeSeconds = cpuSeconds) }
        }
    }
    private fun stopPlayerTimer() { playerTimerJob?.cancel(); playerTimerJob = null }
    private fun stopCpuTimer()    { cpuTimerJob?.cancel();    cpuTimerJob    = null }
    private fun stopAllTimers()   { stopPlayerTimer(); stopCpuTimer() }

    fun formatTime(seconds: Long): String {
        val m = seconds / 60; val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
