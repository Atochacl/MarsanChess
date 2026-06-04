package com.marsan.chess.engine

object Evaluator {

    // ===================== TABLAS POSICIONALES =====================
    // Bonus por posición de cada pieza en el tablero (perspectiva blancas)
    // Fuente: clásicas tablas de ajedrez de programación

    private val PAWN_TABLE = arrayOf(
        intArrayOf( 0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(50, 50, 50, 50, 50, 50, 50, 50),
        intArrayOf(10, 10, 20, 30, 30, 20, 10, 10),
        intArrayOf( 5,  5, 10, 25, 25, 10,  5,  5),
        intArrayOf( 0,  0,  0, 20, 20,  0,  0,  0),
        intArrayOf( 5, -5,-10,  0,  0,-10, -5,  5),
        intArrayOf( 5, 10, 10,-20,-20, 10, 10,  5),
        intArrayOf( 0,  0,  0,  0,  0,  0,  0,  0)
    )

    private val KNIGHT_TABLE = arrayOf(
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50),
        intArrayOf(-40,-20,  0,  0,  0,  0,-20,-40),
        intArrayOf(-30,  0, 10, 15, 15, 10,  0,-30),
        intArrayOf(-30,  5, 15, 20, 20, 15,  5,-30),
        intArrayOf(-30,  0, 15, 20, 20, 15,  0,-30),
        intArrayOf(-30,  5, 10, 15, 15, 10,  5,-30),
        intArrayOf(-40,-20,  0,  5,  5,  0,-20,-40),
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50)
    )

    private val BISHOP_TABLE = arrayOf(
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20),
        intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
        intArrayOf(-10,  0,  5, 10, 10,  5,  0,-10),
        intArrayOf(-10,  5,  5, 10, 10,  5,  5,-10),
        intArrayOf(-10,  0, 10, 10, 10, 10,  0,-10),
        intArrayOf(-10, 10, 10, 10, 10, 10, 10,-10),
        intArrayOf(-10,  5,  0,  0,  0,  0,  5,-10),
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20)
    )

    private val ROOK_TABLE = arrayOf(
        intArrayOf( 0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf( 5, 10, 10, 10, 10, 10, 10,  5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf( 0,  0,  0,  5,  5,  0,  0,  0)
    )

    private val QUEEN_TABLE = arrayOf(
        intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20),
        intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
        intArrayOf(-10,  0,  5,  5,  5,  5,  0,-10),
        intArrayOf( -5,  0,  5,  5,  5,  5,  0, -5),
        intArrayOf(  0,  0,  5,  5,  5,  5,  0, -5),
        intArrayOf(-10,  5,  5,  5,  5,  5,  0,-10),
        intArrayOf(-10,  0,  5,  0,  0,  0,  0,-10),
        intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20)
    )

    private val KING_MIDDLE_TABLE = arrayOf(
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-20,-30,-30,-40,-40,-30,-30,-20),
        intArrayOf(-10,-20,-20,-20,-20,-20,-20,-10),
        intArrayOf( 20, 20,  0,  0,  0,  0, 20, 20),
        intArrayOf( 20, 30, 10,  0,  0, 10, 30, 20)
    )

    private val KING_ENDGAME_TABLE = arrayOf(
        intArrayOf(-50,-40,-30,-20,-20,-30,-40,-50),
        intArrayOf(-30,-20,-10,  0,  0,-10,-20,-30),
        intArrayOf(-30,-10, 20, 30, 30, 20,-10,-30),
        intArrayOf(-30,-10, 30, 40, 40, 30,-10,-30),
        intArrayOf(-30,-10, 30, 40, 40, 30,-10,-30),
        intArrayOf(-30,-10, 20, 30, 30, 20,-10,-30),
        intArrayOf(-30,-30,  0,  0,  0,  0,-30,-30),
        intArrayOf(-50,-30,-30,-30,-30,-30,-30,-50)
    )

    // ===================== EVALUACIÓN PRINCIPAL =====================
    fun evaluate(board: Board): Int {
        var score = 0
        val isEndgame = isEndgame(board)

        for (r in 0..7) for (c in 0..7) {
            val piece = board[r, c] ?: continue
            val posBonus = getPositionBonus(piece, r, c, isEndgame)
            val pieceScore = piece.value + posBonus
            score += if (piece.color == PieceColor.WHITE) pieceScore else -pieceScore
        }

        // Bonus por movilidad
        val whiteMoves = MoveGenerator.generatePseudoLegalMoves(board, PieceColor.WHITE).size
        val blackMoves = MoveGenerator.generatePseudoLegalMoves(board, PieceColor.BLACK).size
        score += (whiteMoves - blackMoves) * 3

        return score
    }

    private fun getPositionBonus(piece: Piece, r: Int, c: Int, isEndgame: Boolean): Int {
        val row = if (piece.color == PieceColor.WHITE) r else 7 - r
        return when (piece.type) {
            PieceType.PAWN   -> PAWN_TABLE[row][c]
            PieceType.KNIGHT -> KNIGHT_TABLE[row][c]
            PieceType.BISHOP -> BISHOP_TABLE[row][c]
            PieceType.ROOK   -> ROOK_TABLE[row][c]
            PieceType.QUEEN  -> QUEEN_TABLE[row][c]
            PieceType.KING   -> if (isEndgame) KING_ENDGAME_TABLE[row][c]
                                else           KING_MIDDLE_TABLE[row][c]
        }
    }

    private fun isEndgame(board: Board): Boolean {
        var queens  = 0
        var majors  = 0
        for (r in 0..7) for (c in 0..7) {
            val p = board[r, c] ?: continue
            if (p.type == PieceType.QUEEN)  queens++
            if (p.type == PieceType.ROOK ||
                p.type == PieceType.QUEEN)  majors++
        }
        return queens == 0 || majors <= 2
    }

    // ===================== ORDENAMIENTO DE MOVIMIENTOS =====================
    // Mejora la poda Alpha-Beta priorizando capturas y movimientos fuertes
    fun scoreMoveForOrdering(board: Board, move: Move): Int {
        var score = 0
        val moving  = board[move.fromRow, move.fromCol] ?: return 0
        val captured = board[move.toRow, move.toCol]

        // MVV-LVA: capturar pieza de mucho valor con pieza de poco valor es bueno
        if (captured != null) {
            score += 10 * captured.value - moving.value
        }
        // Promoción de peón
        if (move.promotion == PieceType.QUEEN) score += 900
        // En passant
        if (move.isEnPassant) score += 100
        // Centro del tablero
        val centerBonus = when {
            move.toRow in 3..4 && move.toCol in 3..4 -> 30
            move.toRow in 2..5 && move.toCol in 2..5 -> 15
            else -> 0
        }
        score += centerBonus
        return score
    }

    fun sortMoves(board: Board, moves: List<Move>): List<Move> =
        moves.sortedByDescending { scoreMoveForOrdering(board, it) }
}
