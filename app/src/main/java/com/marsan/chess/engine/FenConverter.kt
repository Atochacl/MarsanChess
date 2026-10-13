package com.marsan.chess.engine

/**
 * Convierte el tablero interno de Marsan Chess a FEN
 * (Forsyth–Edwards Notation) para enviárselo a Stockfish.
 *
 * Ejemplo de FEN inicial:
 *   rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
 */
object FenConverter {

    fun toFen(board: Board, fullMove: Int = 1, halfMove: Int = 0): String {
        val sb = StringBuilder()

        // ── 1. Posición de las piezas (fila 0 = fila 8 del ajedrez) ──
        for (row in 0..7) {
            var empty = 0
            for (col in 0..7) {
                val piece = board[row, col]
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(pieceChar(piece))
                }
            }
            if (empty > 0) sb.append(empty)
            if (row < 7) sb.append('/')
        }

        sb.append(' ')

        // ── 2. Turno ──
        sb.append(if (board.currentTurn == PieceColor.WHITE) 'w' else 'b')
        sb.append(' ')

        // ── 3. Derechos de enroque ──
        val castling = buildString {
            if (board.whiteKingsideCastle)  append('K')
            if (board.whiteQueensideCastle) append('Q')
            if (board.blackKingsideCastle)  append('k')
            if (board.blackQueensideCastle) append('q')
        }
        sb.append(if (castling.isEmpty()) "-" else castling)
        sb.append(' ')

        // ── 4. En passant ──
        val ep = board.enPassantTarget
        if (ep != null) {
            val file = 'a' + ep.second
            val rank = 8 - ep.first
            sb.append("$file$rank")
        } else {
            sb.append('-')
        }
        sb.append(' ')

        // ── 5. Medio movimiento (simplificado) ──
        sb.append(halfMove)
        sb.append(' ')

        // ── 6. Movimiento completo ──
        sb.append(fullMove)

        return sb.toString()
    }

    private fun pieceChar(piece: Piece): Char {
        val c = when (piece.type) {
            PieceType.PAWN   -> 'p'
            PieceType.KNIGHT -> 'n'
            PieceType.BISHOP -> 'b'
            PieceType.ROOK   -> 'r'
            PieceType.QUEEN  -> 'q'
            PieceType.KING   -> 'k'
        }
        return if (piece.color == PieceColor.WHITE) c.uppercaseChar() else c
    }

    /**
     * Convierte un movimiento UCI ("e2e4", "e7e8q") al Move interno.
     * Devuelve null si el movimiento no se encuentra en la lista legal.
     */
    fun uciToMove(uci: String, board: Board): Move? {
        if (uci.length < 4) return null

        val fromCol = uci[0] - 'a'
        val fromRow = 8 - (uci[1] - '0')
        val toCol   = uci[2] - 'a'
        val toRow   = 8 - (uci[3] - '0')

        val promotionChar = if (uci.length >= 5) uci[4] else null
        val promotion = when (promotionChar) {
            'q' -> PieceType.QUEEN
            'r' -> PieceType.ROOK
            'b' -> PieceType.BISHOP
            'n' -> PieceType.KNIGHT
            else -> null
        }

        // Buscar el movimiento legal que coincida
        val color = board.currentTurn
        val legalMoves = MoveGenerator.generateLegalMoves(board, color)

        return legalMoves.firstOrNull { move ->
            move.fromRow == fromRow && move.fromCol == fromCol &&
            move.toRow   == toRow   && move.toCol   == toCol   &&
            (promotion == null || move.promotion == promotion)
        }
    }
}
