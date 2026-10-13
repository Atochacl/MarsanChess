package com.marsan.chess.engine

/**
 * Notación algebraica estándar (SAN): "Cf3", "exd5", "O-O", "Dxf7#".
 *
 * Las letras de las piezas dependen del idioma, así que se reciben
 * como parámetro en el orden Rey, Dama, Torre, Alfil, Caballo:
 *   español "RDTAC", inglés "KQRBN".
 */
object SanFormatter {

    fun toSan(board: Board, move: Move, letters: String): String {
        val piece = board[move.fromRow, move.fromCol] ?: return UciMoves.toUci(move)
        val sb = StringBuilder()

        if (move.isCastling) {
            sb.append(if (move.toCol == 6) "O-O" else "O-O-O")
        } else {
            val isCapture = board[move.toRow, move.toCol] != null || move.isEnPassant
            if (piece.type == PieceType.PAWN) {
                if (isCapture) sb.append('a' + move.fromCol).append('x')
                sb.append(square(move.toRow, move.toCol))
                move.promotion?.let { sb.append('=').append(letter(it, letters)) }
            } else {
                sb.append(letter(piece.type, letters))
                // Desambiguación: otra pieza igual puede ir a la misma casilla
                val rivals = MoveGenerator.generateLegalMoves(board, piece.color).filter {
                    it.toRow == move.toRow && it.toCol == move.toCol &&
                        !(it.fromRow == move.fromRow && it.fromCol == move.fromCol) &&
                        board[it.fromRow, it.fromCol]?.type == piece.type
                }
                if (rivals.isNotEmpty()) {
                    val sameFile = rivals.any { it.fromCol == move.fromCol }
                    val sameRank = rivals.any { it.fromRow == move.fromRow }
                    when {
                        !sameFile -> sb.append('a' + move.fromCol)
                        !sameRank -> sb.append(8 - move.fromRow)
                        else      -> sb.append(square(move.fromRow, move.fromCol))
                    }
                }
                if (isCapture) sb.append('x')
                sb.append(square(move.toRow, move.toCol))
            }
        }

        val after    = board.applyMove(move)
        val opponent = piece.color.opponent()
        if (MoveGenerator.isInCheck(after, opponent)) {
            sb.append(if (MoveGenerator.generateLegalMoves(after, opponent).isEmpty()) "#" else "+")
        }
        return sb.toString()
    }

    /**
     * Variante con numeración: "12. Cf3 Cc6 13. d4" o, si empieza
     * moviendo el negro, "12... Cc6 13. d4".
     */
    fun lineToSan(start: Board, moves: List<Move>, startMoveNumber: Int,
                  letters: String, maxPlies: Int = moves.size): String {
        val sb = StringBuilder()
        var b = start
        var num = startMoveNumber
        moves.take(maxPlies).forEachIndexed { i, m ->
            val white = b.currentTurn == PieceColor.WHITE
            if (white) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append("$num. ")
            } else if (i == 0) {
                sb.append("$num... ")
            } else {
                sb.append(' ')
            }
            sb.append(toSan(b, m, letters))
            b = b.applyMove(m)
            if (!white) num++
        }
        return sb.toString()
    }

    /** Jugada sola con su número: "12. Cf3" o "12... Cc6". */
    fun numbered(board: Board, move: Move, moveNumber: Int, letters: String): String =
        lineToSan(board, listOf(move), moveNumber, letters)

    fun square(row: Int, col: Int) = "${'a' + col}${8 - row}"

    fun letter(type: PieceType, letters: String): Char {
        val idx = when (type) {
            PieceType.KING   -> 0
            PieceType.QUEEN  -> 1
            PieceType.ROOK   -> 2
            PieceType.BISHOP -> 3
            PieceType.KNIGHT -> 4
            PieceType.PAWN   -> return 'P'
        }
        return letters.getOrNull(idx) ?: "KQRBN"[idx]
    }
}
