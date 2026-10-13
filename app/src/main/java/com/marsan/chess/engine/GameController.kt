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
    // Tiempo consumido al cierre del último turno. El reloj en vivo
    // (que cambia cada 10 ms) va aparte, en GameController.clock.
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

/** Reloj en vivo: tiempo consumido por cada lado, en ms. */
data class ClockState(
    val playerMs: Long = 0L,
    val cpuMs: Long    = 0L
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

    // El reloj en vivo va en su propio flujo: así la pantalla solo
    // actualiza los dos textos del reloj cada 10 ms, en vez de
    // redibujar el tablero y las piezas capturadas.
    private val _clock = MutableStateFlow(ClockState())
    val clock: StateFlow<ClockState> = _clock

    // ── Reloj ────────────────────────────────────────────────────────
    private enum class Side { PLAYER, CPU }

    private var activeSide: Side? = null
    private var segmentStartMs    = 0L     // 0 = reloj detenido o en pausa
    private var paused            = false
    private var tickerJob: Job?   = null
    private var cpuJob: Job?      = null

    // Acumuladores de tiempo consumido (ms)
    private var playerAccumMs = 0L
    private var cpuAccumMs    = 0L

    private val limitMs: Long get() = timeLimitSeconds * 1000L

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
        initStockfish()
        startTurn()
    }

    /**
     * Retoma una partida en curso a partir de sus jugadas (UCI) y los
     * tiempos consumidos. Se usa cuando Android recrea la pantalla de
     * juego (por ejemplo, tras cerrar la app por falta de memoria).
     */
    fun restoreGame(uciMoves: List<String>, playerMs: Long, cpuMs: Long) {
        var board  = Board().apply { setup() }
        val boards = mutableListOf(board.copy())
        val moves  = mutableListOf<Move>()
        for (uci in uciMoves) {
            val move = UciMoves.parseUciDirect(uci, board) ?: break
            board = board.applyMove(move)
            moves += move
            boards += board.copy()
        }
        internalEngine.resetMoveCount()
        playerAccumMs = playerMs
        cpuAccumMs    = cpuMs

        val toMove = board.currentTurn
        val (byPlayer, byCpu) = recalculateCaptured(moves)
        val status    = MoveGenerator.getGameStatus(board, toMove)
        val (result, reason) = evaluateEnd(status, toMove, boards, moves)
        _state.value = GameState(
            board             = board,
            status            = status,
            currentTurn       = toMove,
            lastMove          = moves.lastOrNull(),
            moveHistory       = moves,
            boardHistory      = boards,
            capturedByPlayer  = byPlayer,
            capturedByCpu     = byCpu,
            checkAttackers    = attackersFor(status, board, toMove),
            playerTimeMs      = playerMs,
            cpuTimeMs         = cpuMs,
            playerTimeLimitMs = limitMs,
            cpuTimeLimitMs    = limitMs,
            result            = result,
            endReason         = reason,
            twoPlayerMode     = twoPlayerMode
        )
        publishClock()
        initStockfish()
        if (result == GameResult.ONGOING) startTurn()
    }

    private fun initStockfish() {
        if (stockfish == null) return
        scope.launch(Dispatchers.IO) {
            stockfishReady = stockfish.init()
            if (stockfishReady) {
                _state.value = _state.value.copy(usingStockfish = true)
                android.util.Log.d("GameController", "Stockfish ready ✓")
            }
        }
    }

    /** Arranca el turno de quien mueve en la posición actual. */
    private fun startTurn() {
        val toMove = _state.value.board.currentTurn
        if (twoPlayerMode || toMove == playerColor) startPlayerTurn()
        else startCpuTurn()
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

    /** Medias jugadas que retrocede "Deshacer": 1 en 2 Jugadores, 2 contra la CPU. */
    val undoStep: Int get() = if (twoPlayerMode) 1 else 2

    fun canUndo(s: GameState = _state.value): Boolean =
        s.boardHistory.size > undoStep && s.result == GameResult.ONGOING &&
                !s.isThinking && s.pendingPromotion == null

    fun undoMove() {
        val s = _state.value
        if (!canUndo(s)) return

        stopClock()
        val newHistory     = s.boardHistory.dropLast(undoStep)
        val restoredBoard  = newHistory.last()
        val newMoveHistory = s.moveHistory.dropLast(minOf(undoStep, s.moveHistory.size))
        val (capturedPlayer, capturedCpu) = recalculateCaptured(newMoveHistory)

        // El estado (jaque) se calcula para quien mueve ahora, que en
        // 2 Jugadores no siempre es el Jugador 1.
        val toMove = restoredBoard.currentTurn
        val status = MoveGenerator.getGameStatus(restoredBoard, toMove)

        _state.value = s.copy(
            board = restoredBoard, status = status,
            currentTurn = toMove,
            selectedSquare = null, legalMovesForSelected = emptyList(),
            lastMove = newMoveHistory.lastOrNull(), moveHistory = newMoveHistory,
            boardHistory = newHistory, capturedByPlayer = capturedPlayer,
            capturedByCpu = capturedCpu, checkAttackers = attackersFor(status, restoredBoard, toMove),
            result = GameResult.ONGOING, endReason = GameEndReason.ONGOING,
            playerTimeMs = playerAccumMs, cpuTimeMs = cpuAccumMs
        )
        startPlayerTurn()
    }

    private fun recalculateCaptured(moves: List<Move>): Pair<List<Piece>, List<Piece>> {
        val byPlayer = mutableListOf<Piece>()
        val byCpu    = mutableListOf<Piece>()
        var b = Board().apply { setup() }
        for (move in moves) {
            val captured = DrawRules.capturedPiece(b, move)
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
        stopClock()
        val s = _state.value

        val movingColor = s.currentTurn   // en 2P puede ser WHITE o BLACK
        val nextColor   = movingColor.opponent()

        val captured = DrawRules.capturedPiece(s.board, move)
        val newBoard = s.board.applyMove(move)
        val history  = s.moveHistory + move
        val boards   = s.boardHistory + newBoard.copy()

        // En 2P: J1 captura van a capturedByPlayer, J2 captura a capturedByCpu
        // En vs CPU: movingColor siempre == playerColor
        val capturedByPlayer = if (captured != null && movingColor == playerColor)
            s.capturedByPlayer + captured else s.capturedByPlayer
        val capturedByCpu = if (captured != null && movingColor != playerColor)
            s.capturedByCpu + captured else s.capturedByCpu

        val status = MoveGenerator.getGameStatus(newBoard, nextColor)
        val (result, reason) = evaluateEnd(status, nextColor, boards, history)

        _state.value = s.copy(
            board = newBoard, status = status, currentTurn = nextColor,
            selectedSquare = null, legalMovesForSelected = emptyList(),
            lastMove = move, moveHistory = history, boardHistory = boards,
            capturedByPlayer = capturedByPlayer, capturedByCpu = capturedByCpu,
            checkAttackers = attackersFor(status, newBoard, nextColor),
            result = result, endReason = reason,
            playerTimeMs = playerAccumMs, cpuTimeMs = cpuAccumMs, pendingPromotion = null
        )

        if (result == GameResult.ONGOING) {
            if (twoPlayerMode) startPlayerTurn()
            else startCpuTurn()
        }
    }

    // ===================== TURNO CPU =====================

    private fun startCpuTurn() {
        startClock(Side.CPU)
        cpuJob?.cancel()
        cpuJob = scope.launch { makeCpuMove() }
    }

    // ===================== MOVIMIENTO CPU =====================

    private suspend fun makeCpuMove() {
        _state.value = _state.value.copy(isThinking = true, checkAttackers = emptyList())
        val cpuColor = playerColor.opponent()

        delay(120L + kotlin.random.Random.nextLong(80L))

        val board = _state.value.board
        val move  = getCpuMove(board, cpuColor)

        // Mientras la CPU pensaba la partida pudo terminar (se le acabó
        // el tiempo o el jugador abandonó): en ese caso su jugada ya no
        // cuenta y no debe pisar el resultado.
        val s = _state.value
        if (s.result != GameResult.ONGOING) {
            _state.value = s.copy(isThinking = false)
            return
        }

        stopClock()

        if (move == null) {
            val status = MoveGenerator.getGameStatus(s.board, cpuColor)
            val (result, reason) = resolveResultForColor(status, cpuColor)
            finishGame(result, reason)
            return
        }

        val captured  = DrawRules.capturedPiece(s.board, move)
        val newBoard  = s.board.applyMove(move)
        val history   = s.moveHistory + move
        val boards    = s.boardHistory + newBoard.copy()
        val capturedByCpu = if (captured != null) s.capturedByCpu + captured
                            else s.capturedByCpu

        val status = MoveGenerator.getGameStatus(newBoard, playerColor)
        val (result, reason) = evaluateEnd(status, playerColor, boards, history)

        _state.value = s.copy(
            board = newBoard, status = status, currentTurn = playerColor,
            lastMove = move, moveHistory = history, boardHistory = boards,
            capturedByCpu = capturedByCpu,
            checkAttackers = attackersFor(status, newBoard, playerColor),
            isThinking = false, result = result, endReason = reason,
            playerTimeMs = playerAccumMs, cpuTimeMs = cpuAccumMs
        )

        if (result == GameResult.ONGOING) startPlayerTurn()
        else finishGame(result, reason)
    }

    private suspend fun getCpuMove(board: Board, cpuColor: PieceColor): Move? {
        if (stockfishReady && stockfish != null) {
            try {
                // Se envía la partida completa (posición inicial + jugadas)
                // para que Stockfish conozca las repeticiones y la regla de
                // las 50 jugadas, igual que el juego.
                val uciMoves = _state.value.moveHistory.map { UciMoves.toUci(it) }
                val uci = stockfish.getBestMove(START_FEN, difficulty.level, uciMoves)
                if (uci != null && uci != "(none)") {
                    val move = FenConverter.uciToMove(uci, board)
                    if (move != null) return move
                }
            } catch (e: CancellationException) {
                throw e
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

    private fun startPlayerTurn() = startClock(Side.PLAYER)

    // ===================== RELOJ =====================

    private fun now() = System.currentTimeMillis()

    private fun liveMs(side: Side): Long {
        val base    = if (side == Side.PLAYER) playerAccumMs else cpuAccumMs
        val running = if (activeSide == side && segmentStartMs > 0L) now() - segmentStartMs else 0L
        return base + running
    }

    private fun publishClock() {
        _clock.value = ClockState(liveMs(Side.PLAYER), liveMs(Side.CPU))
    }

    /** Detiene el tramo en curso y lo suma al lado que estaba jugando. */
    private fun chargeRunningSegment() {
        val side = activeSide
        if (side != null && segmentStartMs > 0L) {
            val elapsed = now() - segmentStartMs
            if (side == Side.PLAYER) playerAccumMs += elapsed else cpuAccumMs += elapsed
        }
        segmentStartMs = 0L
    }

    private fun startClock(side: Side) {
        stopClock()
        activeSide = side
        if (!paused) {
            segmentStartMs = now()
            startTicker()
        }
    }

    private fun stopClock() {
        tickerJob?.cancel(); tickerJob = null
        chargeRunningSegment()
        activeSide = null
        publishClock()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        if (limitMs <= 0L) return   // sin límite: el reloj visible es cosmético (GameActivity)
        tickerJob = scope.launch {
            while (isActive) {
                delay(10)   // centésimas visibles en la UI
                publishClock()
                val side = activeSide ?: continue
                // Elegir la coronación no hace perder por tiempo
                if (side == Side.PLAYER && _state.value.pendingPromotion != null) continue
                if (liveMs(side) >= limitMs) {
                    handleTimeout(playerRanOut = side == Side.PLAYER)
                    return@launch
                }
            }
        }
    }

    /**
     * La app pasó a segundo plano: el reloj se detiene para que una
     * llamada o cambiar de app no haga perder por tiempo.
     */
    fun pause() {
        if (paused) return
        paused = true
        tickerJob?.cancel(); tickerJob = null
        chargeRunningSegment()
        publishClock()
    }

    /** La app volvió a primer plano: el reloj sigue para quien tenía el turno. */
    fun resume() {
        if (!paused) return
        paused = false
        if (activeSide != null && _state.value.result == GameResult.ONGOING) {
            segmentStartMs = now()
            startTicker()
        }
    }

    // ===================== TIMEOUT =====================

    private fun handleTimeout(playerRanOut: Boolean) {
        stopClock()
        cpuJob?.cancel()
        val result = if (playerRanOut) GameResult.LOSS else GameResult.WIN
        _state.value = _state.value.copy(
            result = result, endReason = GameEndReason.TIMEOUT, isThinking = false,
            playerTimeMs = playerAccumMs, cpuTimeMs = cpuAccumMs
        )
    }

    // ===================== HELPERS =====================

    /**
     * Resultado tras una jugada: primero mate/ahogado/material
     * insuficiente (por la posición) y luego las tablas por triple
     * repetición o por la regla de las 50 jugadas (por el historial).
     * Un mate en la jugada 50 sigue siendo mate.
     */
    private fun evaluateEnd(
        status: GameStatus, colorToMove: PieceColor,
        boards: List<Board>, moves: List<Move>
    ): Pair<GameResult, GameEndReason> {
        val base = resolveResultForColor(status, colorToMove)
        if (base.first != GameResult.ONGOING) return base
        return when {
            DrawRules.isThreefoldRepetition(boards) ->
                Pair(GameResult.DRAW, GameEndReason.REPETITION)
            DrawRules.isFiftyMoveDraw(boards, moves) ->
                Pair(GameResult.DRAW, GameEndReason.FIFTY_MOVES)
            else -> base
        }
    }

    /**
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
        stopClock()
        _state.value = _state.value.copy(
            result = result, endReason = reason, isThinking = false,
            playerTimeMs = playerAccumMs, cpuTimeMs = cpuAccumMs
        )
    }

    private fun attackersFor(status: GameStatus, board: Board, colorInCheck: PieceColor) =
        if (status == GameStatus.CHECK || status == GameStatus.CHECKMATE)
            findCheckAttackers(board, colorInCheck) else emptyList()

    private fun findCheckAttackers(board: Board, colorInCheck: PieceColor): List<IntPair> {
        val (kingRow, kingCol) = board.kingPos(colorInCheck)
        val attackerColor      = colorInCheck.opponent()
        val moves = MoveGenerator.generatePseudoLegalMoves(board, attackerColor)
        return moves.filter { it.toRow == kingRow && it.toCol == kingCol }
            .map { Pair(it.fromRow, it.fromCol) }
            .distinct()
    }

    fun resign() {
        stopClock()
        cpuJob?.cancel()
        _state.value = _state.value.copy(
            result = GameResult.RESIGN, endReason = GameEndReason.RESIGN, isThinking = false,
            playerTimeMs = playerAccumMs, cpuTimeMs = cpuAccumMs
        )
    }

    fun release() {
        tickerJob?.cancel(); tickerJob = null
        cpuJob?.cancel()
        stockfish?.quit()
    }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }
}
