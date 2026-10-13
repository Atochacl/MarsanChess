package com.marsan.chess.engine

object MoveGenerator {

    fun generateLegalMoves(board: Board, color: PieceColor): List<Move> {
        return generatePseudoLegalMoves(board, color).filter { move ->
            !isInCheck(board.applyMove(move), color)
        }
    }

    fun generatePseudoLegalMoves(board: Board, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for (r in 0..7) for (c in 0..7) {
            val piece = board[r, c] ?: continue
            if (piece.color != color) continue
            when (piece.type) {
                PieceType.PAWN   -> moves += pawnMoves(board, r, c, color)
                PieceType.KNIGHT -> moves += knightMoves(board, r, c, color)
                PieceType.BISHOP -> moves += slidingMoves(board, r, c, color, bishopDirs)
                PieceType.ROOK   -> moves += slidingMoves(board, r, c, color, rookDirs)
                PieceType.QUEEN  -> moves += slidingMoves(board, r, c, color, queenDirs)
                PieceType.KING   -> moves += kingMoves(board, r, c, color)
            }
        }
        return moves
    }

    // ===================== PEÓN =====================
    private fun pawnMoves(board: Board, r: Int, c: Int, color: PieceColor): List<Move> {
        val moves    = mutableListOf<Move>()
        val dir      = if (color == PieceColor.WHITE) -1 else 1
        val startRow = if (color == PieceColor.WHITE) 6 else 1
        val promRow  = if (color == PieceColor.WHITE) 0 else 7

        val nr = r + dir
        if (board.inBounds(nr, c) && board.isEmpty(nr, c)) {
            if (nr == promRow) promotionMoves(r, c, nr, c).forEach { moves += it }
            else {
                moves += Move(r, c, nr, c)
                val nr2 = r + dir * 2
                if (r == startRow && board.isEmpty(nr2, c)) moves += Move(r, c, nr2, c)
            }
        }
        for (dc in listOf(-1, 1)) {
            val nc = c + dc
            if (!board.inBounds(nr, nc)) continue
            if (board.isEnemy(nr, nc, color)) {
                if (nr == promRow) promotionMoves(r, c, nr, nc).forEach { moves += it }
                else moves += Move(r, c, nr, nc)
            }
            val ep = board.enPassantTarget
            if (ep != null && ep.first == nr && ep.second == nc)
                moves += Move(r, c, nr, nc, isEnPassant = true)
        }
        return moves
    }

    private fun promotionMoves(fr: Int, fc: Int, tr: Int, tc: Int) = listOf(
        Move(fr, fc, tr, tc, promotion = PieceType.QUEEN),
        Move(fr, fc, tr, tc, promotion = PieceType.ROOK),
        Move(fr, fc, tr, tc, promotion = PieceType.BISHOP),
        Move(fr, fc, tr, tc, promotion = PieceType.KNIGHT)
    )

    // ===================== CABALLO =====================
    private val knightOffsets = listOf(
        -2 to -1, -2 to 1, -1 to -2, -1 to 2,
         1 to -2,  1 to 2,  2 to -1,  2 to 1
    )

    private fun knightMoves(board: Board, r: Int, c: Int, color: PieceColor) =
        knightOffsets.mapNotNull { (dr, dc) ->
            val nr = r + dr; val nc = c + dc
            if (!board.inBounds(nr, nc) || board[nr, nc]?.color == color) null
            else Move(r, c, nr, nc)
        }

    // ===================== DESLIZANTES =====================
    private val bishopDirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
    private val rookDirs   = listOf(-1 to 0,  1 to 0,  0 to -1, 0 to 1)
    private val queenDirs  = bishopDirs + rookDirs

    private fun slidingMoves(board: Board, r: Int, c: Int, color: PieceColor,
                              dirs: List<Pair<Int,Int>>): List<Move> {
        val moves = mutableListOf<Move>()
        for ((dr, dc) in dirs) {
            var nr = r + dr; var nc = c + dc
            while (board.inBounds(nr, nc)) {
                val target = board[nr, nc]
                if (target == null) { moves += Move(r, c, nr, nc) }
                else {
                    if (target.color != color) moves += Move(r, c, nr, nc)
                    break
                }
                nr += dr; nc += dc
            }
        }
        return moves
    }

    // ===================== REY =====================
    private fun kingMoves(board: Board, r: Int, c: Int, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr; val nc = c + dc
            if (!board.inBounds(nr, nc) || board[nr, nc]?.color == color) continue
            moves += Move(r, c, nr, nc)
        }
        moves += castlingMoves(board, r, c, color)
        return moves
    }

    private fun castlingMoves(board: Board, r: Int, c: Int, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        if (isInCheck(board, color)) return moves
        val (kingSide, queenSide) = if (color == PieceColor.WHITE)
            board.whiteKingsideCastle to board.whiteQueensideCastle
        else board.blackKingsideCastle to board.blackQueensideCastle

        if (kingSide && board.isEmpty(r,5) && board.isEmpty(r,6) &&
            !isSquareAttacked(board,r,5,color) && !isSquareAttacked(board,r,6,color))
            moves += Move(r,c,r,6, isCastling=true)

        if (queenSide && board.isEmpty(r,3) && board.isEmpty(r,2) && board.isEmpty(r,1) &&
            !isSquareAttacked(board,r,3,color) && !isSquareAttacked(board,r,2,color))
            moves += Move(r,c,r,2, isCastling=true)
        return moves
    }

    // ===================== JAQUE =====================
    fun isInCheck(board: Board, color: PieceColor): Boolean {
        val (kr, kc) = board.kingPos(color)
        return isSquareAttacked(board, kr, kc, color)
    }

    fun isSquareAttacked(board: Board, r: Int, c: Int, defenderColor: PieceColor): Boolean {
        val attackerColor = defenderColor.opponent()
        for ((dr, dc) in knightOffsets) {
            val nr = r+dr; val nc = c+dc
            if (board.inBounds(nr,nc)) {
                val p = board[nr,nc]
                if (p?.color == attackerColor && p.type == PieceType.KNIGHT) return true
            }
        }
        for ((dr,dc) in rookDirs) {
            var nr = r+dr; var nc = c+dc
            while (board.inBounds(nr,nc)) {
                val p = board[nr,nc]
                if (p == null) { nr+=dr; nc+=dc; continue }
                if (p.color==attackerColor &&
                    (p.type==PieceType.ROOK||p.type==PieceType.QUEEN)) return true
                break
            }
        }
        for ((dr,dc) in bishopDirs) {
            var nr = r+dr; var nc = c+dc
            while (board.inBounds(nr,nc)) {
                val p = board[nr,nc]
                if (p == null) { nr+=dr; nc+=dc; continue }
                if (p.color==attackerColor &&
                    (p.type==PieceType.BISHOP||p.type==PieceType.QUEEN)) return true
                break
            }
        }
        val pawnDir = if (defenderColor==PieceColor.WHITE) -1 else 1
        for (dc in listOf(-1,1)) {
            val nr = r+pawnDir; val nc = c+dc
            if (board.inBounds(nr,nc)) {
                val p = board[nr,nc]
                if (p?.color==attackerColor && p.type==PieceType.PAWN) return true
            }
        }
        for (dr in -1..1) for (dc in -1..1) {
            if (dr==0 && dc==0) continue
            val nr = r+dr; val nc = c+dc
            if (board.inBounds(nr,nc)) {
                val p = board[nr,nc]
                if (p?.color==attackerColor && p.type==PieceType.KING) return true
            }
        }
        return false
    }

    // ===================== ESTADO DE PARTIDA =====================
    fun getGameStatus(board: Board, color: PieceColor): GameStatus {
        val moves = generateLegalMoves(board, color)
        return when {
            moves.isEmpty() && isInCheck(board, color) -> GameStatus.CHECKMATE
            moves.isEmpty()                            -> GameStatus.STALEMATE
            isInCheck(board, color)                    -> GameStatus.CHECK
            isInsufficientMaterial(board)              -> GameStatus.DRAW
            else                                       -> GameStatus.ONGOING
        }
    }

    private fun isInsufficientMaterial(board: Board): Boolean {
        val pieces = mutableListOf<Piece>()
        for (r in 0..7) for (c in 0..7) board[r,c]?.let { pieces += it }
        if (pieces.size == 2) return true
        if (pieces.size == 3) {
            val minor = pieces.find { it.type != PieceType.KING }
            if (minor?.type == PieceType.BISHOP || minor?.type == PieceType.KNIGHT) return true
        }
        return false
    }
}

enum class GameStatus { ONGOING, CHECK, CHECKMATE, STALEMATE, DRAW }
