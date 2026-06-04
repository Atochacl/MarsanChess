package com.marsan.chess.engine

// ===================== PIEZAS =====================
enum class PieceType { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }
enum class PieceColor { WHITE, BLACK }

data class Piece(val type: PieceType, val color: PieceColor) {
    val symbol: String get() = when (color) {
        PieceColor.WHITE -> when (type) {
            PieceType.PAWN   -> "♙"; PieceType.KNIGHT -> "♘"
            PieceType.BISHOP -> "♗"; PieceType.ROOK   -> "♖"
            PieceType.QUEEN  -> "♕"; PieceType.KING   -> "♔"
        }
        PieceColor.BLACK -> when (type) {
            PieceType.PAWN   -> "♟"; PieceType.KNIGHT -> "♞"
            PieceType.BISHOP -> "♝"; PieceType.ROOK   -> "♜"
            PieceType.QUEEN  -> "♛"; PieceType.KING   -> "♚"
        }
    }
    val value: Int get() = when (type) {
        PieceType.PAWN   -> 100;  PieceType.KNIGHT -> 320
        PieceType.BISHOP -> 330;  PieceType.ROOK   -> 500
        PieceType.QUEEN  -> 900;  PieceType.KING   -> 20000
    }
}

// ===================== MOVIMIENTO =====================
data class Move(
    val fromRow: Int, val fromCol: Int,
    val toRow: Int,   val toCol: Int,
    val promotion: PieceType? = null,
    val isEnPassant: Boolean = false,
    val isCastling: Boolean = false
) {
    override fun toString() = "${'A' + fromCol}${8 - fromRow}→${'A' + toCol}${8 - toRow}"
}

// ===================== TABLERO =====================
class Board {
    val squares = Array(8) { arrayOfNulls<Piece>(8) }
    var currentTurn = PieceColor.WHITE
    var enPassantTarget: Pair<Int, Int>? = null

    // Derechos de enroque
    var whiteKingsideCastle  = true
    var whiteQueensideCastle = true
    var blackKingsideCastle  = true
    var blackQueensideCastle = true

    // Posición del rey (cache para jaque rápido)
    var whiteKingPos = Pair(7, 4)
    var blackKingPos = Pair(0, 4)

    fun setup() {
        // Peones
        for (c in 0..7) {
            squares[1][c] = Piece(PieceType.PAWN, PieceColor.BLACK)
            squares[6][c] = Piece(PieceType.PAWN, PieceColor.WHITE)
        }
        // Piezas negras
        val order = listOf(PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP,
                           PieceType.QUEEN, PieceType.KING,
                           PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK)
        order.forEachIndexed { c, type ->
            squares[0][c] = Piece(type, PieceColor.BLACK)
            squares[7][c] = Piece(type, PieceColor.WHITE)
        }
        currentTurn   = PieceColor.WHITE
        whiteKingPos  = Pair(7, 4)
        blackKingPos  = Pair(0, 4)
    }

    fun copy(): Board {
        val b = Board()
        for (r in 0..7) for (c in 0..7) b.squares[r][c] = squares[r][c]
        b.currentTurn          = currentTurn
        b.enPassantTarget      = enPassantTarget
        b.whiteKingsideCastle  = whiteKingsideCastle
        b.whiteQueensideCastle = whiteQueensideCastle
        b.blackKingsideCastle  = blackKingsideCastle
        b.blackQueensideCastle = blackQueensideCastle
        b.whiteKingPos         = whiteKingPos
        b.blackKingPos         = blackKingPos
        return b
    }

    operator fun get(r: Int, c: Int): Piece?         = squares[r][c]
    operator fun set(r: Int, c: Int, p: Piece?)      { squares[r][c] = p }
    fun inBounds(r: Int, c: Int)                     = r in 0..7 && c in 0..7
    fun isEmpty(r: Int, c: Int)                      = squares[r][c] == null
    fun isEnemy(r: Int, c: Int, color: PieceColor)   = squares[r][c]?.color == color.opponent()

    fun kingPos(color: PieceColor) =
        if (color == PieceColor.WHITE) whiteKingPos else blackKingPos

    fun applyMove(move: Move): Board {
        val b     = copy()
        val piece = b[move.fromRow, move.fromCol] ?: return b
        b.enPassantTarget = null

        // Enroque
        if (move.isCastling) {
            b[move.toRow, move.toCol]     = piece
            b[move.fromRow, move.fromCol] = null
            val kingSide = move.toCol == 6
            val rookFromCol = if (kingSide) 7 else 0
            val rookToCol   = if (kingSide) 5 else 3
            b[move.toRow, rookToCol]   = b[move.toRow, rookFromCol]
            b[move.toRow, rookFromCol] = null
        }
        // En passant
        else if (move.isEnPassant) {
            b[move.toRow, move.toCol]     = piece
            b[move.fromRow, move.fromCol] = null
            val capturedRow = if (piece.color == PieceColor.WHITE) move.toRow + 1 else move.toRow - 1
            b[capturedRow, move.toCol]    = null
        }
        // Movimiento normal
        else {
            val promotion = move.promotion
            b[move.toRow, move.toCol] = if (promotion != null)
                Piece(promotion, piece.color) else piece
            b[move.fromRow, move.fromCol] = null
        }

        // Actualizar en passant target
        if (piece.type == PieceType.PAWN && Math.abs(move.toRow - move.fromRow) == 2) {
            b.enPassantTarget = Pair((move.fromRow + move.toRow) / 2, move.fromCol)
        }

        // Actualizar derechos de enroque
        if (piece.type == PieceType.KING) {
            if (piece.color == PieceColor.WHITE) {
                b.whiteKingsideCastle  = false
                b.whiteQueensideCastle = false
                b.whiteKingPos         = Pair(move.toRow, move.toCol)
            } else {
                b.blackKingsideCastle  = false
                b.blackQueensideCastle = false
                b.blackKingPos         = Pair(move.toRow, move.toCol)
            }
        }
        if (piece.type == PieceType.ROOK) {
            when {
                move.fromRow == 7 && move.fromCol == 7 -> b.whiteKingsideCastle  = false
                move.fromRow == 7 && move.fromCol == 0 -> b.whiteQueensideCastle = false
                move.fromRow == 0 && move.fromCol == 7 -> b.blackKingsideCastle  = false
                move.fromRow == 0 && move.fromCol == 0 -> b.blackQueensideCastle = false
            }
        }

        // Actualizar posición del rey si capturó al rey enemigo (no debería pasar en juego legal)
        if (piece.type == PieceType.KING) {
            if (piece.color == PieceColor.WHITE) b.whiteKingPos = Pair(move.toRow, move.toCol)
            else                                 b.blackKingPos = Pair(move.toRow, move.toCol)
        }

        b.currentTurn = b.currentTurn.opponent()
        return b
    }
}

fun PieceColor.opponent() = if (this == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
