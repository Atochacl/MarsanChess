package com.marsan.chess.engine

import android.content.Context
import com.marsan.chess.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

typealias IntPair = Pair<Int, Int>

data class GameState(
    val board: Board                        = Board(),
    val status: GameStatus                  = GameStatus.ONGOING,
    val currentTurn: PieceColor             = PieceColor.WHITE,
    val selectedSquare: IntPair?            = null,
    val legalMovesForSelected: List<Move>   = emptyList(),
    val lastMove: Move?                     = null,
    val playerTimeMs: Long                  = 0L,
    val cpuTimeMs: Long                     = 0L,
    val playerTimeLimitMs: Long             = 0L,
    val cpuTimeLimitMs: Long                = 0L,
    val isThinking: Boolean                 = false,
    val result: GameResult                  = GameResult.ONGOING,
    val endReason: GameEndReason            = GameEndReason.ONGOING,
    val moveHistory: List<Move>             = emptyList(),
    val boardHistory: List<Board>           = emptyList(),
    val capturedByPlayer: List<Piece>       = emptyList(),
    val capturedByCpu: List<Piece>          = emptyList(),
    val checkAttackers: List<IntPair>       = emptyList(),
    val usingStockfish: Boolean             = false,
    val pendingPromotion: PendingPromotion? = null,
    val twoPlayerMode: Boolean              = false    // true → no CPU, ambos humanos
)

data class PendingPromotion(
    val row: Int,
    val col: Int,
    val color: PieceColor,
    val baseMove: Move
)

class GameController(
    val playerColor: PieceColor        = PieceColor.WHITE,
    private val difficulty: Difficulty = Difficulty.LEVEL_3,
    private val timeLimitSeconds: Long = 0L,
    private val scope: CoroutineScope,
    val twoPlayerMode: Boolean         = false,   // true → sin CPU
    context: Context? = null
) {
    private val internalEngine = ChessEngine()
    private val stockfish      = context?.let { StockfishEngine(it) }
    private var stockfishReady = false

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    // ── Timers ───────────────────────────────────────────────────────
    private var playerTimerJob: Job? = null
    private var cpuTimerJob:    Job? = null

    // Acumuladores de tiempo consumido (ms)
    private var playerAccumMs = 0L
    private var cpuAccumMs    = 0L

    // Marca de inicio del turno actual
    private var turnStartMs = 0L

    private val limitMs: Long get() = timeLimitSeconds * 1000L
    private var fullMoveNumber = 1

    // ===================== INICIO =====================

    fun startGame() {
        val board = Board().apply { setup() }
        internalEngine.resetMoveCount()

        _state.value = GameState(
            board             = board,
            currentTurn       = PieceColor.WHITE,
            boardHistory      = listOf(board.copy()),
            playerTimeLimitMs = limitMs,
            cpuTimeLimitMs    = limitMs,
            twoPlayerMode     = twoPlayerMode
        )

        scope.launch(Dispatchers.IO) {
            stockfishReady = stockfish?.init() ?: false
            if (stockfishReady) {
                _state.value = _state.value.copy(usingStockfish = true)
                android.util.Log.d("GameController", "Stockfish ready ✓")
            }
        }

        if (twoPlayerMode) {
            startPlayerTurn()   // en modo 2 jugadores, siempre inicia el turno humano (blancas)
        } else if (playerColor == PieceColor.WHITE) {
            startPlayerTurn()
        } else {
            startCpuTurn { makeCpuMove() }
        }
    }

    // ===================== TOQUE DEL JUGADOR =====================

    fun onSquareTapped(row: Int, col: Int) {
        val s = _state.value
        if (s.isThinking || s.result != GameResult.ONGOING) return
        if (s.pendingPromotion != null) return
        // En modo 2 jugadores ambos colores son "humanos" — no bloquear por color
        if (!twoPlayerMode && s.currentTurn != playerColor) return

        val piece = s.board[row, col]
        if (s.selectedSquare != null) {
            val candidates = s.legalMovesForSelected.filter {
                it.toRow == row && it.toCol == col
            }
            if (candidates.isNotEmpty()) {
                if (candidates.any { it.promotion != null }) {
                    val promoColor = if (twoPlayerMode) s.currentTurn else playerColor
                    _state.value = s.copy(
                        selectedSquare        = null,
                        legalMovesForSelected = emptyList(),
                        pendingPromotion = PendingPromotion(
                            row      = row, col = col,
                            color    = promoColor,
                            baseMove = candidates.first()
                        )
                    )
                } else {
                    applyPlayerMove(candidates.first())
                }
                return
            }
        }
        val activeColor = if (twoPlayerMode) s.currentTurn else playerColor
        if (piece?.color == activeColor) {
            val legal = MoveGenerator.generateLegalMoves(s.board, activeColor)
            _state.value = s.copy(
                selectedSquare        = Pair(row, col),
                legalMovesForSelected = legal.filter { it.fromRow == row && it.fromCol == col }
            )
        } else {
            _state.value = s.copy(selectedSquare = null, legalMovesForSelected = emptyList())
        }
    }

    fun choosePromotion(pieceType: PieceType) {
        val s       = _state.value
        val pending = s.pendingPromotion ?: return
        val move    = Move(
            fromRow   = pending.baseMove.fromRow, fromCol = pending.baseMove.fromCol,
            toRow     = pending.baseMove.toRow,   toCol   = pending.baseMove.toCol,
            promotion = pieceType
        )
        _state.value = s.copy(pendingPromotion = null)
        applyPlayerMove(move)
    }

    // ===================== DESHACER =====================

    fun undoMove() {
        val s = _state.value
        if (s.isThinking || s.result != GameResult.ONGOING) return
        if (s.pendingPromotion != null) return
        val history = s.boardHistory
        if (history.size < 3) return

        stopAllTimers()
        val newHistory     = history.dropLast(2)
        val restoredBoard  = newHistory.last()
        val newMoveHistory = s.moveHistory.dropLast(minOf(2, s.moveHistory.size))
        val (capturedPlayer, capturedCpu) = recalculateCaptured(newMoveHistory)
        if (fullMoveNumber > 1) fullMoveNumber--

        val status    = MoveGenerator.getGameStatus(restoredBoard, playerColor)
        val attackers = if (status == GameStatus.CHECK || status == GameStatus.CHECKMATE)
            findCheckAttackers(restoredBoard, playerColor) else emptyList()

        _state.value = s.copy(
            board = restoredBoard, status = status,
            currentTurn = if (twoPlayerMode) restoredBoard.currentTurn else playerColor,
            selectedSquare = null, legalMovesForSelected = emptyList(),
            lastMove = newMoveHistory.lastOrNull(), moveHistory = newMoveHistory,
            boardHistory = newHistory, capturedByPlayer = capturedPlayer,
            capturedByCpu = capturedCpu, checkAttackers = attackers,
            result = GameResult.ONGOING, endReason = GameEndReason.ONGOING
        )
        startPlayerTurn()
    }

    private fun recalculateCaptured(moves: List<Move>): Pair<List<Piece>, List<Piece>> {
        val start    = Board().apply { setup() }
        val byPlayer = mutableListOf<Piece>()
        val byCpu    = mutableListOf<Piece>()
        var b = start.copy()
        for (move in moves) {
            val captured = b[move.toRow, move.toCol]
            val moving   = b[move.fromRow, move.fromCol]
            if (captured != null && moving != null) {
                if (moving.color == playerColor) byPlayer.add(captured)
                else byCpu.add(captured)
            }
            b = b.applyMove(move)
        }
        return Pair(byPlayer, byCpu)
    }

    // ===================== MOVIMIENTO JUGADOR =====================

    private fun applyPlayerMove(move: Move) {
        val s          = _state.value
        val elapsedMs  = stopPlayerTimer()
        playerAccumMs += elapsedMs

        val movingColor = s.currentTurn   // en 2P puede ser WHITE o BLACK
        val nextColor   = movingColor.opponent()

        val captured = s.board[move.toRow, move.toCol]
        val newBoard = s.board.applyMove(move)
        val history  = s.moveHistory + move
        val boards   = s.boardHistory + newBoard.copy()

        // En 2P: J1 captura van a capturedByPlayer, J2 captura a capturedByCpu
        // En vs CPU: movingColor siempre == playerColor
        val capturedByPlayer = if (captured != null && movingColor == playerColor)
            s.capturedByPlayer + captured else s.capturedByPlayer
        val capturedByCpu = if (captured != null && movingColor != playerColor)
            s.capturedByCpu + captured else s.capturedByCpu

        val status   = MoveGenerator.getGameStatus(newBoard, nextColor)
        // forPlayer=true si el resultado afecta al que ACABA de mover (nextColor es quien recibe)
        val (result, reason) = resolveResultForColor(status, nextColor)
        val attackers = if (status == GameStatus.CHECK || status == GameStatus.CHECKMATE)
            findCheckAttackers(newBoard, nextColor) else emptyList()

        _state.value = s.copy(
            board = newBoard, status = status, currentTurn = nextColor,
            selectedSquare = null, legalMovesForSelected = emptyList(),
            lastMove = move, moveHistory = history, boardHistory = boards,
            capturedByPlayer = capturedByPlayer, capturedByCpu = capturedByCpu,
            checkAttackers = attackers, result = result, endReason = reason,
            playerTimeMs = playerAccumMs, pendingPromotion = null
        )

        if (result == GameResult.ONGOING) {
            if (twoPlayerMode) startPlayerTurn()
            else startCpuTurn { makeCpuMove() }
        }
    }

    // ===================== TURNO CPU =====================

    /**
     * Inicia el timer de la CPU y luego ejecuta el bloque de juego.
     * El timer corre mientras la CPU piensa, descontando de su tiempo.
     */
    private fun startCpuTurn(block: suspend () -> Unit) {
        if (limitMs > 0L) {
            turnStartMs = System.currentTimeMillis()
            cpuTimerJob?.cancel()
            cpuTimerJob = scope.launch {
                while (true) {
                    delay(10)   // actualizar cada 10ms → centésimas visibles en UI
                    val elapsed = System.currentTimeMillis() - turnStartMs
                    val totalMs = cpuAccumMs + elapsed
                    _state.value = _state.value.copy(cpuTimeMs = totalMs)
                    if (totalMs >= limitMs) {
                        handleTimeout(playerRanOut = false)
                        return@launch
                    }
                }
            }
        }
        scope.launch { block() }
    }

    // ===================== MOVIMIENTO CPU =====================

    private suspend fun makeCpuMove() {
        _state.value = _state.value.copy(isThinking = true, checkAttackers = emptyList())
        val cpuColor = playerColor.opponent()

        delay(120L + kotlin.random.Random.nextLong(80L))

        val s    = _state.value
        val move = getCpuMove(s.board, cpuColor)

        // Detener timer CPU y acumular tiempo
        val cpuElapsed = stopCpuTimer()
        cpuAccumMs += cpuElapsed

        if (move == null) {
            val status = MoveGenerator.getGameStatus(s.board, cpuColor)
            val (result, reason) = resolveResult(status, forPlayer = true)
            finishGame(result, reason)
            return
        }

        val captured  = s.board[move.toRow, move.toCol]
        val newBoard  = s.board.applyMove(move)
        val history   = s.moveHistory + move
        val boards    = s.boardHistory + newBoard.copy()
        val capturedByCpu = if (captured != null) s.capturedByCpu + captured
                            else s.capturedByCpu

        if (cpuColor == PieceColor.BLACK || playerColor == PieceColor.BLACK)
            fullMoveNumber++

        val status   = MoveGenerator.getGameStatus(newBoard, playerColor)
        val (result, reason) = resolveResult(status, forPlayer = true)
        val attackers = if (status == GameStatus.CHECK || status == GameStatus.CHECKMATE)
            findCheckAttackers(newBoard, playerColor) else emptyList()

        _state.value = s.copy(
            board = newBoard, status = status, currentTurn = playerColor,
            lastMove = move, moveHistory = history, boardHistory = boards,
            capturedByCpu = capturedByCpu, checkAttackers = attackers,
            isThinking = false, result = result, endReason = reason,
            cpuTimeMs = cpuAccumMs
        )

        if (result == GameResult.ONGOING) startPlayerTurn()
        else finishGame(result, reason)
    }

    private suspend fun getCpuMove(board: Board, cpuColor: PieceColor): Move? {
        if (stockfishReady && stockfish != null) {
            try {
                val fen = FenConverter.toFen(board, fullMoveNumber)
                val uci = stockfish.getBestMove(fen, difficulty.level)
                if (uci != null && uci != "(none)") {
                    val move = FenConverter.uciToMove(uci, board)
                    if (move != null) return move
                }
            } catch (e: Exception) {
                android.util.Log.e("GameController", "Stockfish error: ${e.message}")
            }
        }
        val move = internalEngine.getBestMove(board, difficulty, cpuColor, null)
        return move?.let { resolveCpuPromotionIfNeeded(board, it, cpuColor) }
    }

    private fun resolveCpuPromotionIfNeeded(board: Board, move: Move, cpuColor: PieceColor): Move {
        if (move.promotion == null) return move
        val candidates = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
            .map { Move(move.fromRow, move.fromCol, move.toRow, move.toCol, promotion = it) }
        val maximize = cpuColor == PieceColor.WHITE
        var best = candidates.first(); var bestScore = if (maximize) Int.MIN_VALUE else Int.MAX_VALUE
        for (c in candidates) {
            val score = Evaluator.evaluate(board.applyMove(c))
            if (maximize && score > bestScore) { bestScore = score; best = c }
            if (!maximize && score < bestScore) { bestScore = score; best = c }
        }
        return best
    }

    // ===================== TURNO DEL JUGADOR =====================

    private fun startPlayerTurn() {
        if (limitMs <= 0L) return
        turnStartMs   = System.currentTimeMillis()
        playerTimerJob?.cancel()
        playerTimerJob = scope.launch {
            while (true) {
                delay(10)   // centésimas
                val s = _state.value
                if (s.pendingPromotion != null) continue
                val elapsed = System.currentTimeMillis() - turnStartMs
                val totalMs = playerAccumMs + elapsed
                _state.value = _state.value.copy(playerTimeMs = totalMs)
                if (totalMs >= limitMs) {
                    handleTimeout(playerRanOut = true)
                    return@launch
                }
            }
        }
    }

    // ===================== TIMEOUT =====================

    private fun handleTimeout(playerRanOut: Boolean) {
        stopAllTimers()
        val result = if (playerRanOut) GameResult.LOSS else GameResult.WIN
        _state.value = _state.value.copy(
            result = result, endReason = GameEndReason.TIMEOUT, isThinking = false
        )
    }

    // ===================== HELPERS =====================

    private fun resolveResult(status: GameStatus, forPlayer: Boolean) = when (status) {
        GameStatus.CHECKMATE -> if (forPlayer)
            Pair(GameResult.LOSS, GameEndReason.CHECKMATE)
        else Pair(GameResult.WIN, GameEndReason.CHECKMATE)
        GameStatus.STALEMATE -> Pair(GameResult.DRAW, GameEndReason.STALEMATE)
        GameStatus.DRAW      -> Pair(GameResult.DRAW, GameEndReason.INSUFFICIENT_MATERIAL)
        else                 -> Pair(GameResult.ONGOING, GameEndReason.ONGOING)
    }

    /**
     * Versión para modo 2P y fin de turno general:
     * colorToMove = el color que debe mover ahora (quien recibió el turno).
     * Si ese color está en jaque mate, PIERDE.
     * Si está en ahogado, tablas.
     * El WIN/LOSS se asigna desde la perspectiva del jugador 1 (playerColor).
     */
    private fun resolveResultForColor(status: GameStatus, colorToMove: PieceColor) = when (status) {
        GameStatus.CHECKMATE -> if (colorToMove == playerColor)
            Pair(GameResult.LOSS, GameEndReason.CHECKMATE)   // J1 recibe jaque mate → pierde
        else
            Pair(GameResult.WIN, GameEndReason.CHECKMATE)    // J2 (o CPU) recibe jaque mate → J1 gana
        GameStatus.STALEMATE -> Pair(GameResult.DRAW, GameEndReason.STALEMATE)
        GameStatus.DRAW      -> Pair(GameResult.DRAW, GameEndReason.INSUFFICIENT_MATERIAL)
        else                 -> Pair(GameResult.ONGOING, GameEndReason.ONGOING)
    }

    private fun finishGame(result: GameResult, reason: GameEndReason) {
        stopAllTimers()
        _state.value = _state.value.copy(result = result, endReason = reason, isThinking = false)
    }

    private fun findCheckAttackers(board: Board, colorInCheck: PieceColor): List<IntPair> {
        val (kingRow, kingCol) = board.kingPos(colorInCheck)
        val attackerColor      = colorInCheck.opponent()
        return buildList {
            for (r in 0..7) for (c in 0..7) {
                val piece = board[r, c] ?: continue
                if (piece.color != attackerColor) continue
                val moves = MoveGenerator.generatePseudoLegalMoves(board, attackerColor)
                if (moves.any { it.fromRow == r && it.fromCol == c &&
                            it.toRow == kingRow && it.toCol == kingCol })
                    add(Pair(r, c))
            }
        }
    }

    fun resign() {
        stopAllTimers()
        _state.value = _state.value.copy(
            result = GameResult.RESIGN, endReason = GameEndReason.RESIGN, isThinking = false
        )
    }

    // ===================== TIMERS =====================

    private fun stopPlayerTimer(): Long {
        playerTimerJob?.cancel(); playerTimerJob = null
        return if (turnStartMs > 0L) System.currentTimeMillis() - turnStartMs else 0L
    }

    private fun stopCpuTimer(): Long {
        cpuTimerJob?.cancel(); cpuTimerJob = null
        return if (turnStartMs > 0L) System.currentTimeMillis() - turnStartMs else 0L
    }

    private fun stopAllTimers() {
        playerTimerJob?.cancel(); playerTimerJob = null
        cpuTimerJob?.cancel();    cpuTimerJob    = null
    }

    fun release() {
        stopAllTimers()
        stockfish?.quit()
    }
}
