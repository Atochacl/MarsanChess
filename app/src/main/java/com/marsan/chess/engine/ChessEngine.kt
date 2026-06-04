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
        // Umbral de movimientos jugados para que Marsan cometa error calculado
        private const val MARSAN_FATIGUE_THRESHOLD = 30
    }

    private var moveCount = 0
    private var rivalStyle: RivalStyle = RivalStyle.STRATEGIC

    // ===================== API PRINCIPAL =====================

    suspend fun getBestMove(
        board: Board,
        difficulty: Difficulty,
        color: PieceColor,
        rival: ArcadeRival? = null
    ): Move? = withContext(Dispatchers.Default) {

        rivalStyle = rival?.style ?: RivalStyle.STRATEGIC
        val depth   = rival?.engineDepth ?: difficulty.depth
        moveCount++

        val legalMoves = MoveGenerator.generateLegalMoves(board, color)
        if (legalMoves.isEmpty()) return@withContext null

        when (rivalStyle) {
            RivalStyle.RANDOM    -> getRandomMove(legalMoves)
            RivalStyle.BEGINNER  -> getBeginnerMove(board, legalMoves, color)
            RivalStyle.AGGRESSIVE-> getAggressiveMove(board, legalMoves, color, depth)
            RivalStyle.DEFENSIVE -> getDefensiveMove(board, legalMoves, color, depth)
            RivalStyle.STRATEGIC -> getStrategicMove(board, legalMoves, color, depth)
            RivalStyle.CHAOTIC   -> getChaoticMove(board, legalMoves, color, depth)
            RivalStyle.MASTER    -> getMasterMove(board, legalMoves, color, depth)
        }
    }

    // ===================== ESTILOS DE JUEGO =====================

    // Manchita — mueve casi al azar, ocasionalmente captura
    private fun getRandomMove(moves: List<Move>): Move {
        val captures = moves.filter { true } // 30% captura si puede
        return if (Random.nextFloat() < 0.3f && captures.isNotEmpty())
            captures.random()
        else moves.random()
    }

    // Alejandra — aperturas básicas, falla en medio juego
    private fun getBeginnerMove(board: Board, moves: List<Move>, color: PieceColor): Move {
        // En apertura intenta mover peones centrales
        if (moveCount <= 6) {
            val centralPawnMoves = moves.filter { move ->
                val piece = board[move.fromRow, move.fromCol]
                piece?.type == PieceType.PAWN &&
                move.fromCol in 2..5 &&
                move.toCol in 2..5
            }
            if (centralPawnMoves.isNotEmpty()) return centralPawnMoves.random()
        }
        // 60% del tiempo hace un movimiento razonable, 40% aleatorio
        return if (Random.nextFloat() < 0.6f) {
            val scored = Evaluator.sortMoves(board, moves)
            scored.take(3).random()
        } else {
            moves.random()
        }
    }

    // Laurita — busca capturas y ataques agresivos
    private fun getAggressiveMove(board: Board, moves: List<Move>,
                                   color: PieceColor, depth: Int): Move {
        // Prioriza capturas siempre
        val captures = moves.filter { board[it.toRow, it.toCol] != null || it.isEnPassant }
        if (captures.isNotEmpty() && Random.nextFloat() < 0.85f) {
            return captures.maxByOrNull {
                board[it.toRow, it.toCol]?.value ?: 0
            } ?: captures.random()
        }
        // Busca movimientos que acerquen piezas al rey enemigo
        val enemyKingPos = board.kingPos(color.opponent())
        val aggressive = moves.maxByOrNull { move ->
            val dr = Math.abs(move.toRow - enemyKingPos.first)
            val dc = Math.abs(move.toCol - enemyKingPos.second)
            -(dr + dc) // mientras más cerca del rey enemigo, mejor
        }
        return if (Random.nextFloat() < 0.7f) aggressive ?: moves.random()
        else minimax(board, moves, color, minOf(depth, 2))
    }

    // Maca — mueve para proteger sus piezas y espera errores
    private fun getDefensiveMove(board: Board, moves: List<Move>,
                                  color: PieceColor, depth: Int): Move {
        // Evita mover piezas a casillas atacadas
        val safeMoves = moves.filter { move ->
            val next = board.applyMove(move)
            !MoveGenerator.isSquareAttacked(next, move.toRow, move.toCol, color)
        }
        val pool = if (safeMoves.size > 3) safeMoves else moves
        return if (Random.nextFloat() < 0.6f) minimax(board, pool, color, minOf(depth, 2))
        else pool.random()
    }

    // Cristian — equilibrado, usa Minimax con buena profundidad
    private fun getStrategicMove(board: Board, moves: List<Move>,
                                  color: PieceColor, depth: Int): Move {
        return minimax(board, moves, color, depth)
    }

    // Lop585 — impredecible: a veces brillante, a veces falla
    private fun getChaoticMove(board: Board, moves: List<Move>,
                                color: PieceColor, depth: Int): Move {
        val r = Random.nextFloat()
        return when {
            r < 0.15f -> moves.random()                          // 15% completamente aleatorio
            r < 0.35f -> Evaluator.sortMoves(board, moves).last()// 20% peor movimiento
            r < 0.65f -> minimax(board, moves, color, depth)     // 30% juega bien
            else      -> minimax(board, moves, color, depth - 1) // 35% juega casi bien
        }
    }

    // Marsan — el más fuerte, falla levemente en partidas largas
    private fun getMasterMove(board: Board, moves: List<Move>,
                               color: PieceColor, depth: Int): Move {
        // Fatiga calculada: en partidas largas introduce un pequeño error
        val fatigue = moveCount > MARSAN_FATIGUE_THRESHOLD
        return if (fatigue && Random.nextFloat() < 0.12f) {
            // Error sutil: elige el 2do o 3er mejor movimiento
            val sorted = Evaluator.sortMoves(board, moves)
            val candidates = sorted.take(minOf(3, sorted.size))
            candidates.random()
        } else {
            minimax(board, moves, color, depth)
        }
    }

    // ===================== MINIMAX + ALPHA-BETA =====================

    private fun minimax(board: Board, moves: List<Move>,
                         color: PieceColor, depth: Int): Move {
        val sorted   = Evaluator.sortMoves(board, moves)
        val maximize = color == PieceColor.WHITE
        var bestMove = sorted.first()
        var bestScore = if (maximize) -INF else INF
        var alpha = -INF
        var beta  = INF

        for (move in sorted) {
            val next  = board.applyMove(move)
            val score = alphaBeta(next, depth - 1, alpha, beta, !maximize)
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

    private fun alphaBeta(board: Board, depth: Int, alpha: Int, beta: Int,
                           maximize: Boolean): Int {
        if (depth == 0) return quiescence(board, alpha, beta, maximize)

        val color  = if (maximize) PieceColor.WHITE else PieceColor.BLACK
        val moves  = MoveGenerator.generateLegalMoves(board, color)

        if (moves.isEmpty()) {
            return if (MoveGenerator.isInCheck(board, color))
                if (maximize) -INF + (10 - depth) else INF - (10 - depth)
            else 0 // Tablas por ahogado
        }

        val sorted = Evaluator.sortMoves(board, moves)
        var a = alpha; var b = beta

        if (maximize) {
            var best = -INF
            for (move in sorted) {
                val score = alphaBeta(board.applyMove(move), depth - 1, a, b, false)
                best = maxOf(best, score)
                a    = maxOf(a, score)
                if (b <= a) break
            }
            return best
        } else {
            var best = INF
            for (move in sorted) {
                val score = alphaBeta(board.applyMove(move), depth - 1, a, b, true)
                best = minOf(best, score)
                b    = minOf(b, score)
                if (b <= a) break
            }
            return best
        }
    }

    // Búsqueda de quietud — evita el efecto horizonte evaluando capturas extra
    private fun quiescence(board: Board, alpha: Int, beta: Int, maximize: Boolean): Int {
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

        val sorted = Evaluator.sortMoves(board, captures)
        var best   = standPat

        if (maximize) {
            for (move in sorted) {
                val score = quiescence(board.applyMove(move), a, b, false)
                best = maxOf(best, score)
                a    = maxOf(a, score)
                if (b <= a) break
            }
        } else {
            for (move in sorted) {
                val score = quiescence(board.applyMove(move), a, b, true)
                best = minOf(best, score)
                b    = minOf(b, score)
                if (b <= a) break
            }
        }
        return best
    }

    fun resetMoveCount() { moveCount = 0 }
}
