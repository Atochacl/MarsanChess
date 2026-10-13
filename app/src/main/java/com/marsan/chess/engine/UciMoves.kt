package com.marsan.chess.engine

/**
 * Conversión entre Move interno y notación UCI ("e2e4", "e7e8q").
 *
 * Un solo lugar para esta conversión: antes había tres copias
 * (grabación, replay y análisis) y todas convertían la promoción a
 * Caballo como "k" en vez de "n", así que esas partidas se
 * reproducían mal.
 */
object UciMoves {

    fun toUci(move: Move): String {
        val from  = "${'a' + move.fromCol}${8 - move.fromRow}"
        val to    = "${'a' + move.toCol}${8 - move.toRow}"
        val promo = when (move.promotion) {
            PieceType.QUEEN  -> "q"
            PieceType.ROOK   -> "r"
            PieceType.BISHOP -> "b"
            PieceType.KNIGHT -> "n"
            else             -> ""
        }
        return "$from$to$promo"
    }

    /**
     * Convierte UCI a Move sin validar contra movimientos legales.
     * Detecta enroque y captura al paso por contexto del tablero.
     * Es el parser que debe usar el replay (FenConverter.uciToMove
     * falla con enroque y al paso en partidas grabadas).
     */
    fun parseUciDirect(uci: String, board: Board): Move? {
        if (uci.length < 4) return null
        return try {
            val fromCol = uci[0] - 'a'
            val fromRow = 8 - (uci[1].digitToInt())
            val toCol   = uci[2] - 'a'
            val toRow   = 8 - (uci[3].digitToInt())

            if (fromCol !in 0..7 || fromRow !in 0..7 || toCol !in 0..7 || toRow !in 0..7) return null

            val promotion = if (uci.length >= 5) when (uci[4].lowercaseChar()) {
                'q' -> PieceType.QUEEN;  'r' -> PieceType.ROOK
                'b' -> PieceType.BISHOP; 'n' -> PieceType.KNIGHT
                // Partidas grabadas antes del Fix58 guardaban el Caballo como "k"
                'k' -> PieceType.KNIGHT
                else -> null
            } else null

            val piece = board[fromRow, fromCol] ?: return null

            // Enroque: el rey se mueve 2 columnas
            val isCastling = piece.type == PieceType.KING && Math.abs(toCol - fromCol) == 2

            // Al paso: peón que cambia de columna hacia una casilla vacía
            val isEnPassant = piece.type == PieceType.PAWN &&
                    fromCol != toCol && board[toRow, toCol] == null

            Move(fromRow, fromCol, toRow, toCol, promotion, isEnPassant, isCastling)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convierte una variante UCI completa (la "pv" de Stockfish) en
     * jugadas internas, aplicándolas una tras otra. Se detiene en la
     * primera jugada que no se pueda interpretar.
     */
    fun parseLine(pv: List<String>, start: Board): List<Move> {
        val out = mutableListOf<Move>()
        var b = start
        for (uci in pv) {
            val m = parseUciDirect(uci, b) ?: break
            out += m
            b = b.applyMove(m)
        }
        return out
    }
}
