package com.marsan.chess.engine

import com.marsan.chess.data.model.ArcadeRival
import com.marsan.chess.data.model.Difficulty
import com.marsan.chess.data.model.RivalStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class ChessEngine {

    companion object {
        private const val INF = 1_000_000

        // Tiempo máximo por jugada según dificultad (ms)
        // Niveles altos usan Iterative Deepening con límite de tiempo
        private val TIME_LIMITS = mapOf(
            1 to 300L,
            2 to 500L,
            3 to 1_200L,
            4 to 2_500L,
            5 to 4_000L
        )
        private const val MARSAN_FATIGUE_THRESHOLD = 30
    }

    private var moveCount  = 0
    private var rivalStyle = RivalStyle.STRATEGIC

    // Tabla de transposición simple (Zobrist no impl. aquí — usamos board hash)
    // Capacidad limitada para no consumir demasiada memoria
    private val transpositionTable = HashMap<Int, TTEntry>(1 shl 16)

    data class TTEntry(val depth: Int, val score: Int, val flag: Int) {
        companion object { const val EXACT = 0; const val LOWER = 1; const val UPPER = 2 }
    }

    // ===================== API PRINCIPAL =====================

    suspend fun getBestMove(
        board: Board,
        difficulty: Difficulty,
        color: PieceColor,
        rival: ArcadeRival? = null
    ): Move? = withContext(Dispatchers.Default) {

        rivalStyle = rival?.style ?: RivalStyle.STRATEGIC
        val depth  = rival?.engineDepth ?: difficulty.depth
        moveCount++
        transpositionTable.clear()

        val legalMoves = MoveGenerator.generateLegalMoves(board, color)
        if (legalMoves.isEmpty()) return@withContext null

        when {
            rival != null -> getArcadeMove(board, legalMoves, color, depth)
            else -> when (difficulty) {
                Difficulty.LEVEL_1 -> getLevel1Move(legalMoves)
                Difficulty.LEVEL_2 -> getLevel2Move(board, legalMoves, color)
                Difficulty.LEVEL_3 -> iterativeDeepening(board, legalMoves, color, depth,
                    TIME_LIMITS[3]!!)
                Difficulty.LEVEL_4 -> iterativeDeepening(board, legalMoves, color, depth,
                    TIME_LIMITS[4]!!)
                Difficulty.LEVEL_5 -> getMasterMove(board, legalMoves, color, depth)
            }
        }
    }

    // ===================== ITERATIVE DEEPENING =====================
    // Busca hasta la profundidad máxima pero respeta el límite de tiempo.
    // Si se acaba el tiempo durante una profundidad, usa el mejor resultado anterior.
    private fun iterativeDeepening(
        board: Board, moves: List<Move>, color: PieceColor,
        maxDepth: Int, timeLimitMs: Long
    ): Move {
        val deadline  = System.currentTimeMillis() + timeLimitMs
        var bestMove  = Evaluator.sortMoves(board, moves).first()

        for (d in 1..maxDepth) {
            if (System.currentTimeMillis() >= deadline) break
            val result = minimaxTimed(board, moves, color, d, deadline)
            if (result != null) bestMove = result
            if (System.currentTimeMillis() >= deadline) break
        }
        return bestMove
    }

    private fun minimaxTimed(
        board: Board, moves: List<Move>, color: PieceColor,
        depth: Int, deadline: Long
    ): Move? {
        val sorted    = Evaluator.sortMoves(board, moves)
        val maximize  = color == PieceColor.WHITE
        var bestMove: Move? = null
        var bestScore = if (maximize) -INF else INF
        var alpha = -INF; var beta = INF

        for (move in sorted) {
            if (System.currentTimeMillis() >= deadline) return bestMove
            val next  = board.applyMove(move)
            val score = alphaBetaTimed(next, depth - 1, alpha, beta, !maximize, deadline)
            if (maximize) {
                if (score > bestScore) { bestScore = score; bestMove = move }
                alpha = maxOf(alpha, score)
            } else {
                if (score < bestScore) { bestScore = score; bestMove = move }
                beta = minOf(beta, score)
            }
            if (beta <= alpha) break
        }
        return bestMove
    }

    private fun alphaBetaTimed(
        board: Board, depth: Int, alpha: Int, beta: Int,
        maximize: Boolean, deadline: Long
    ): Int {
        if (System.currentTimeMillis() >= deadline) return Evaluator.evaluate(board)
        if (depth == 0) return quiescence(board, alpha, beta, maximize, deadline)

        // Tabla de transposición
        val hash  = board.hashCode()
        val ttEntry = transpositionTable[hash]
        if (ttEntry != null && ttEntry.depth >= depth) {
            when (ttEntry.flag) {
                TTEntry.EXACT -> return ttEntry.score
                TTEntry.LOWER -> if (ttEntry.score >= beta) return ttEntry.score
                TTEntry.UPPER -> if (ttEntry.score <= alpha) return ttEntry.score
            }
        }

        val color = if (maximize) PieceColor.WHITE else PieceColor.BLACK
        val moves = MoveGenerator.generateLegalMoves(board, color)

        if (moves.isEmpty()) {
            return if (MoveGenerator.isInCheck(board, color))
                if (maximize) -INF + (10 - depth) else INF - (10 - depth)
            else 0
        }

        val sorted = Evaluator.sortMoves(board, moves)
        var a = alpha; var b = beta
        var best: Int

        if (maximize) {
            best = -INF
            for (move in sorted) {
                if (System.currentTimeMillis() >= deadline) break
                val score = alphaBetaTimed(board.applyMove(move), depth - 1, a, b, false, deadline)
                best = maxOf(best, score); a = maxOf(a, score)
                if (b <= a) break
            }
        } else {
            best = INF
            for (move in sorted) {
                if (System.currentTimeMillis() >= deadline) break
                val score = alphaBetaTimed(board.applyMove(move), depth - 1, a, b, true, deadline)
                best = minOf(best, score); b = minOf(b, score)
                if (b <= a) break
            }
        }

        // Guardar en tabla de transposición
        val flag = when {
            best <= alpha -> TTEntry.UPPER
            best >= beta  -> TTEntry.LOWER
            else          -> TTEntry.EXACT
        }
        if (transpositionTable.size < (1 shl 16)) {
            transpositionTable[hash] = TTEntry(depth, best, flag)
        }
        return best
    }

    // ===================== NIVELES BAJOS =====================

    private fun getLevel1Move(moves: List<Move>): Move {
        return if (Random.nextFloat() < 0.15f && moves.any { true }) moves.random()
        else moves.random()
    }

    private fun getLevel2Move(board: Board, moves: List<Move>, color: PieceColor): Move {
        val r = Random.nextFloat()
        return when {
            r < 0.40f -> moves.random()
            r < 0.65f -> {
                val captures = moves.filter { board[it.toRow, it.toCol] != null }
                captures.maxByOrNull { board[it.toRow, it.toCol]?.value ?: 0 } ?: moves.random()
            }
            else -> minimax(board, moves, color, 1)
        }
    }

    // ===================== ARCADE =====================

    private fun getArcadeMove(
        board: Board, moves: List<Move>, color: PieceColor, depth: Int
    ): Move = when (rivalStyle) {
        RivalStyle.RANDOM    -> moves.random()
        RivalStyle.BEGINNER  -> getBeginnerMove(board, moves, color)
        RivalStyle.AGGRESSIVE-> getAggressiveMove(board, moves, color, depth)
        RivalStyle.DEFENSIVE -> getDefensiveMove(board, moves, color, depth)
        RivalStyle.STRATEGIC -> minimax(board, moves, color, depth)
        RivalStyle.CHAOTIC   -> getChaoticMove(board, moves, color, depth)
        RivalStyle.MASTER    -> getMasterMove(board, moves, color, depth)
    }

    private fun getBeginnerMove(board: Board, moves: List<Move>, color: PieceColor): Move {
        if (moveCount <= 6) {
            val centralPawns = moves.filter { m ->
                board[m.fromRow, m.fromCol]?.type == PieceType.PAWN &&
                m.fromCol in 2..5 && m.toCol in 2..5
            }
            if (centralPawns.isNotEmpty()) return centralPawns.random()
        }
        return if (Random.nextFloat() < 0.6f)
            Evaluator.sortMoves(board, moves).take(3).random()
        else moves.random()
    }

    private fun getAggressiveMove(
        board: Board, moves: List<Move>, color: PieceColor, depth: Int
    ): Move {
        val captures = moves.filter { board[it.toRow, it.toCol] != null || it.isEnPassant }
        if (captures.isNotEmpty() && Random.nextFloat() < 0.85f)
            return captures.maxByOrNull { board[it.toRow, it.toCol]?.value ?: 0 }!!
        val enemyKing = board.kingPos(color.opponent())
        val aggressive = moves.maxByOrNull { m ->
            -(Math.abs(m.toRow - enemyKing.first) + Math.abs(m.toCol - enemyKing.second))
        }
        return if (Random.nextFloat() < 0.7f) aggressive ?: moves.random()
        else minimax(board, moves, color, minOf(depth, 2))
    }

    private fun getDefensiveMove(
        board: Board, moves: List<Move>, color: PieceColor, depth: Int
    ): Move {
        val safeMoves = moves.filter { m ->
            !MoveGenerator.isSquareAttacked(board.applyMove(m), m.toRow, m.toCol, color)
        }
        val pool = if (safeMoves.size > 3) safeMoves else moves
        return if (Random.nextFloat() < 0.6f) minimax(board, pool, color, minOf(depth, 2))
        else pool.random()
    }

    private fun getChaoticMove(
        board: Board, moves: List<Move>, color: PieceColor, depth: Int
    ): Move {
        val r = Random.nextFloat()
        return when {
            r < 0.15f -> moves.random()
            r < 0.35f -> Evaluator.sortMoves(board, moves).last()
            r < 0.65f -> minimax(board, moves, color, depth)
            else      -> minimax(board, moves, color, (depth - 1).coerceAtLeast(1))
        }
    }

    private fun getMasterMove(
        board: Board, moves: List<Move>, color: PieceColor, depth: Int
    ): Move {
        val fatigue = moveCount > MARSAN_FATIGUE_THRESHOLD
        return if (fatigue && Random.nextFloat() < 0.08f)
            Evaluator.sortMoves(board, moves).take(minOf(3, moves.size)).random()
        else iterativeDeepening(board, moves, color, depth, TIME_LIMITS[5]!!)
    }

    // ===================== MINIMAX CLÁSICO (niveles bajos/arcade) =====================

    private fun minimax(
        board: Board, moves: List<Move>, color: PieceColor, depth: Int
    ): Move {
        val sorted    = Evaluator.sortMoves(board, moves)
        val maximize  = color == PieceColor.WHITE
        var bestMove  = sorted.first()
        var bestScore = if (maximize) -INF else INF
        var alpha = -INF; var beta = INF

        for (move in sorted) {
            val score = alphaBeta(board.applyMove(move), depth - 1, alpha, beta, !maximize)
            if (maximize) {
                if (score > bestScore) { bestScore = score; bestMove = move }
                alpha = maxOf(alpha, score)
            } else {
                if (score < bestScore) { bestScore = score; bestMove = move }
                beta = minOf(beta, score)
            }
            if (beta <= alpha) break
        }
        return bestMove
    }

    private fun alphaBeta(
        board: Board, depth: Int, alpha: Int, beta: Int, maximize: Boolean
    ): Int {
        if (depth == 0) return quiescence(board, alpha, beta, maximize, Long.MAX_VALUE)
        val color = if (maximize) PieceColor.WHITE else PieceColor.BLACK
        val moves = MoveGenerator.generateLegalMoves(board, color)
        if (moves.isEmpty()) return if (MoveGenerator.isInCheck(board, color))
            if (maximize) -INF + (10 - depth) else INF - (10 - depth)
        else 0
        val sorted = Evaluator.sortMoves(board, moves)
        var a = alpha; var b = beta
        return if (maximize) {
            var best = -INF
            for (m in sorted) {
                val s = alphaBeta(board.applyMove(m), depth - 1, a, b, false)
                best = maxOf(best, s); a = maxOf(a, s); if (b <= a) break
            }; best
        } else {
            var best = INF
            for (m in sorted) {
                val s = alphaBeta(board.applyMove(m), depth - 1, a, b, true)
                best = minOf(best, s); b = minOf(b, s); if (b <= a) break
            }; best
        }
    }

    private fun quiescence(
        board: Board, alpha: Int, beta: Int, maximize: Boolean, deadline: Long
    ): Int {
        val standPat = Evaluator.evaluate(board)
        var a = alpha; var b = beta
        if (maximize) {
            if (standPat >= b) return standPat
            a = maxOf(a, standPat)
        } else {
            if (standPat <= a) return standPat
            b = minOf(b, standPat)
        }
        val color    = if (maximize) PieceColor.WHITE else PieceColor.BLACK
        val captures = MoveGenerator.generateLegalMoves(board, color)
            .filter { board[it.toRow, it.toCol] != null || it.isEnPassant }
        if (captures.isEmpty()) return standPat
        var best = standPat
        for (move in Evaluator.sortMoves(board, captures)) {
            if (System.currentTimeMillis() >= deadline) break
            val score = quiescence(board.applyMove(move), a, b, !maximize, deadline)
            if (maximize) { best = maxOf(best, score); a = maxOf(a, score) }
            else          { best = minOf(best, score); b = minOf(b, score) }
            if (b <= a) break
        }
        return best
    }

    fun resetMoveCount() { moveCount = 0; transpositionTable.clear() }
}
