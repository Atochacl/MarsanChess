package com.marsan.chess.engine

/**
 * Reglas de tablas que dependen del historial de la partida
 * (no solo de la posición actual):
 *  - Triple repetición: la misma posición aparece 3 veces.
 *  - Regla de las 50 jugadas: 50 jugadas de cada bando (100 medias
 *    jugadas) sin capturas ni movimientos de peón.
 *
 * Ambas se aplican automáticamente (sin que el jugador las reclame),
 * como hacen la mayoría de las apps de ajedrez.
 */
object DrawRules {

    const val FIFTY_MOVE_HALF_MOVES = 100

    /**
     * Clave que identifica una posición según las reglas de repetición:
     * piezas, turno, derechos de enroque y captura al paso. La captura
     * al paso solo cuenta si de verdad se puede capturar al paso.
     */
    fun positionKey(board: Board): String = buildString {
        for (r in 0..7) for (c in 0..7) {
            val p = board[r, c]
            append(
                if (p == null) '.'
                else {
                    val ch = when (p.type) {
                        PieceType.PAWN -> 'p'; PieceType.KNIGHT -> 'n'
                        PieceType.BISHOP -> 'b'; PieceType.ROOK -> 'r'
                        PieceType.QUEEN -> 'q'; PieceType.KING -> 'k'
                    }
                    if (p.color == PieceColor.WHITE) ch.uppercaseChar() else ch
                }
            )
        }
        append(if (board.currentTurn == PieceColor.WHITE) 'w' else 'b')
        if (board.whiteKingsideCastle)  append('K')
        if (board.whiteQueensideCastle) append('Q')
        if (board.blackKingsideCastle)  append('k')
        if (board.blackQueensideCastle) append('q')
        val ep = board.enPassantTarget
        if (ep != null && hasEnPassantCapture(board)) append("e${ep.first}${ep.second}")
    }

    private fun hasEnPassantCapture(board: Board): Boolean =
        MoveGenerator.generateLegalMoves(board, board.currentTurn).any { it.isEnPassant }

    /** true si la última posición de [boards] ya apareció 3 veces o más. */
    fun isThreefoldRepetition(boards: List<Board>): Boolean {
        if (boards.size < 5) return false
        val last = positionKey(boards.last())
        return boards.count { positionKey(it) == last } >= 3
    }

    /**
     * Medias jugadas desde la última captura o movimiento de peón.
     * [boards][i] es la posición ANTES de [moves][i].
     */
    fun halfMoveClock(boards: List<Board>, moves: List<Move>): Int {
        var clock = 0
        for (i in moves.indices.reversed()) {
            val before = boards.getOrNull(i) ?: break
            if (resetsClock(before, moves[i])) break
            clock++
        }
        return clock
    }

    private fun resetsClock(before: Board, move: Move): Boolean {
        val piece = before[move.fromRow, move.fromCol] ?: return true
        return piece.type == PieceType.PAWN || move.isEnPassant ||
                before[move.toRow, move.toCol] != null
    }

    fun isFiftyMoveDraw(boards: List<Board>, moves: List<Move>): Boolean =
        halfMoveClock(boards, moves) >= FIFTY_MOVE_HALF_MOVES

    /** La pieza que captura [move] en [before] (incluida la captura al paso). */
    fun capturedPiece(before: Board, move: Move): Piece? {
        if (move.isEnPassant) {
            val mover = before[move.fromRow, move.fromCol] ?: return null
            return Piece(PieceType.PAWN, mover.color.opponent())
        }
        if (move.isCastling) return null
        return before[move.toRow, move.toCol]
    }
}
