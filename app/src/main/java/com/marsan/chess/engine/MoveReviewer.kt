package com.marsan.chess.engine

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Una línea de análisis de Stockfish.
 * scoreCp y mateIn vienen normalizados a la perspectiva de las BLANCAS
 * (ver StockfishEngine). Los mates se reflejan además en scoreCp como
 * ±(30000 - n) para que las comparaciones numéricas funcionen.
 */
data class EngineLine(
    val scoreCp: Int,
    val mateIn:  Int?,
    val depth:   Int,
    val pv:      List<String>
)

/** Clasificación de una jugada, de mejor a peor. */
enum class MoveClass { FORCED, BOOK, BEST, EXCELLENT, GOOD, INACCURACY, MISTAKE, BLUNDER }

enum class GamePhase { OPENING, MIDDLEGAME, ENDGAME }

/**
 * Hechos concretos de una jugada. Todo lo que el comentario afirma sale
 * de aquí, y todo lo que hay aquí sale del análisis de Stockfish sobre
 * la posición ANTERIOR a la jugada (misma raíz y misma profundidad para
 * la mejor jugada y la jugada hecha, así la comparación es justa).
 *
 * Perspectiva: "mover" es quien hizo la jugada. Las evaluaciones con
 * sufijo Mover están vistas desde ese jugador (positivo = le va bien).
 */
data class MoveReview(
    val moveClass:      MoveClass,
    val accuracy:       Int,        // 0-100, lo que se muestra como %
    val winBefore:      Double,     // % de ganar con la mejor jugada
    val winAfter:       Double,     // % de ganar tras la jugada hecha
    val winDrop:        Double,
    val cpBestMover:    Int,
    val cpPlayedMover:  Int,
    val mateBestMover:  Int?,       // >0: el que movió da mate; <0: lo recibe
    val matePlayedMover: Int?,
    val mover:          PieceColor,
    val moveNumber:     Int,
    val phase:          GamePhase,
    val boardBefore:    Board,
    val played:         Move,
    val best:           Move?,
    val isBest:         Boolean,
    val onlyMove:       Boolean,    // la mejor y la segunda muy distintas
    val playedLine:     List<Move>, // empieza con la jugada hecha
    val bestLine:       List<Move>, // empieza con la mejor jugada
    val movedType:      PieceType,
    val capturedType:   PieceType?,
    val isCheck:        Boolean,
    val isMate:         Boolean,
    val isCastling:     Boolean,
    val isPromotion:    Boolean,
    // Material neto (en peones) para el que movió al final de cada línea
    val materialPlayed: Int,
    val materialBest:   Int,
    val materialRecapture: Int,     // como materialPlayed, pero contado desde antes de la captura del rival
    val gainType:       PieceType?, // pieza más valiosa que se gana en la línea jugada
    val lostType:       PieceType?, // pieza más valiosa que se pierde en la línea jugada
    val missedType:     PieceType?, // pieza más valiosa que ganaba la mejor línea
    val hangsPiece:     Boolean,    // el rival captura en la casilla de destino y gana material
    val rivalReply:     Move?,
    val followUp:       Move?,      // la siguiente jugada propia en la línea jugada
    val forkTargets:    List<PieceType>,
    val threatTarget:   PieceType?, // única pieza rival que queda amenazada
    val isRetreat:      Boolean,
    val answersCheck:   Boolean,    // el que movió estaba en jaque
    val developsPiece:  Boolean,
    val earlyQueen:     Boolean,
    val losesCastling:  Boolean,
    val weakensKing:    Boolean,
    val isRecapture:    Boolean,    // responde a una captura del rival
    val bestIsRecapture: Boolean,   // la mejor jugada era recapturar y no se hizo
    val missedMate:     Boolean,
    val allowsMate:     Boolean,
    val keepsMate:      Boolean
)

object MoveReviewer {

    private const val MATE_CP = 30000

    /**
     * Probabilidad de ganar (0-100) a partir de centipeones, con la
     * curva que usa Lichess. Sirve para que la gravedad dependa del
     * contexto: perder 2 peones con +8 no cambia nada; con 0.0 decide.
     */
    fun winPercent(cpMover: Int): Double {
        val cp = cpMover.coerceIn(-MATE_CP, MATE_CP).toDouble()
        return 50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * cp)) - 1.0)
    }

    /** Precisión 0-100 de una jugada según la caída de % de ganar (fórmula de Lichess). */
    fun accuracyFromDrop(drop: Double): Int =
        (103.1668 * exp(-0.04354 * drop) - 3.1669).roundToInt().coerceIn(0, 100)

    /**
     * @param lines      MultiPV de la posición anterior (la 1 es la mejor)
     * @param playedLine búsqueda restringida a la jugada hecha
     *                   (go ... searchmoves), o null si ya está en lines
     */
    fun review(
        boardBefore: Board,
        played:      Move,
        moveNumber:  Int,
        lines:       List<EngineLine>,
        playedLine:  EngineLine?,
        isBook:      Boolean,
        previous:    Pair<Board, Move>? = null
    ): MoveReview? {
        if (lines.isEmpty()) return null
        val mover = boardBefore.currentTurn
        val sign  = if (mover == PieceColor.WHITE) 1 else -1
        val playedUci = UciMoves.toUci(played)

        val bestEngine   = lines[0]
        val playedEngine = lines.firstOrNull { it.pv.firstOrNull() == playedUci } ?: playedLine ?: return null

        val legalCount = MoveGenerator.generateLegalMoves(boardBefore, mover).size

        val cpBest    = bestEngine.scoreCp * sign
        val cpPlayed  = playedEngine.scoreCp * sign
        val mateBest  = bestEngine.mateIn?.let { it * sign }
        val matePlayed = playedEngine.mateIn?.let { it * sign }

        val winBefore = winPercent(cpBest)
        val winAfter  = winPercent(cpPlayed)
        // Una jugada no puede ser mejor que la mejor: si la búsqueda
        // restringida sale un poco más alta es ruido de profundidad.
        val drop = max(0.0, winBefore - winAfter)

        val bestMoves  = UciMoves.parseLine(bestEngine.pv, boardBefore)
        val playedMovesRaw = UciMoves.parseLine(playedEngine.pv, boardBefore)
        val playedMoves = if (playedMovesRaw.firstOrNull()?.let { UciMoves.toUci(it) } == playedUci)
            playedMovesRaw else listOf(played)
        val best   = bestMoves.firstOrNull()
        val isBest = best != null && UciMoves.toUci(best) == playedUci

        val boardAfter  = boardBefore.applyMove(played)
        val movedPiece  = boardBefore[played.fromRow, played.fromCol]
        val movedType   = movedPiece?.type ?: PieceType.PAWN
        val capturedType = when {
            played.isEnPassant -> PieceType.PAWN
            else -> boardBefore[played.toRow, played.toCol]?.type
        }
        val rival   = mover.opponent()
        val isCheck = MoveGenerator.isInCheck(boardAfter, rival)
        val wasInCheck = MoveGenerator.isInCheck(boardBefore, mover)
        val isMate  = isCheck && MoveGenerator.generateLegalMoves(boardAfter, rival).isEmpty()

        // ¿Jugada única? La mejor era mucho mejor que la segunda opción.
        // No cuenta para capturas (casi siempre son recapturas obvias)
        // ni para el mate mismo.
        val second = lines.getOrNull(1)
        val onlyMove = isBest && second != null && legalCount > 1 &&
            capturedType == null && !isMate &&
            winBefore - winPercent(second.scoreCp * sign) >= 15.0 &&
            winBefore >= 30.0

        // ── Material a lo largo de las líneas ───────────────────────
        // Si el rival acaba de capturar, el material se mide desde ANTES
        // de esa captura: así una recaptura cuenta como recuperar lo
        // perdido y no como "ganar una pieza".
        val prevBoard = previous?.first
        val prevMove  = previous?.second
        val rivalJustCaptured = prevBoard != null && prevMove != null &&
            (prevBoard[prevMove.toRow, prevMove.toCol] != null || prevMove.isEnPassant)
        // Recaptura = capturar en la casilla donde el rival acaba de capturar
        val isRecapture = rivalJustCaptured && capturedType != null &&
            played.toRow == prevMove!!.toRow && played.toCol == prevMove.toCol
        val bestIsRecapture = rivalJustCaptured && best != null && !isBest &&
            best.toRow == prevMove!!.toRow && best.toCol == prevMove.toCol &&
            boardBefore[best.toRow, best.toCol] != null
        val playedMat = materialAlongLine(boardBefore, playedMoves, mover)
        val bestMat   = materialAlongLine(boardBefore, bestMoves, mover)
        val playedMatBase = if (isRecapture)
            materialAlongLine(boardBefore, playedMoves, mover, prevBoard!!) else playedMat

        val rivalReply = playedMoves.getOrNull(1)
        val followUp   = playedMoves.getOrNull(2)

        // Pieza colgada: el rival responde capturando en la casilla a la
        // que se acaba de mover y la línea termina con material perdido.
        val replyCapturesDest = rivalReply != null &&
            rivalReply.toRow == played.toRow && rivalReply.toCol == played.toCol
        val movedValue = pawnUnits(movedType)
        val hangsPiece = replyCapturesDest && playedMat.net <= -1 &&
            (movedValue == 1 || playedMat.net <= -2)

        // ── Mates ────────────────────────────────────────────────────
        val hadMate    = mateBest != null && mateBest > 0
        val keepsMate  = matePlayed != null && matePlayed > 0
        val missedMate = hadMate && !keepsMate
        val allowsMate = matePlayed != null && matePlayed < 0 && (mateBest == null || mateBest > 0)

        // ── Clasificación ────────────────────────────────────────────
        var cls = when {
            legalCount == 1          -> MoveClass.FORCED
            isBook && drop < 5.0     -> MoveClass.BOOK
            isBest                   -> MoveClass.BEST
            drop < 2.0               -> MoveClass.EXCELLENT
            drop < 5.0               -> MoveClass.GOOD
            drop < 10.0              -> MoveClass.INACCURACY
            drop < 20.0              -> MoveClass.MISTAKE
            else                     -> MoveClass.BLUNDER
        }
        val forcedOrBook = cls == MoveClass.FORCED || cls == MoveClass.BOOK
        // Regalar un mate forzado siempre es un error grave, aunque la
        // posición ya estuviera mal (el % de ganar casi no baja, pero
        // la partida termina).
        if (allowsMate && !forcedOrBook) cls = MoveClass.BLUNDER
        // Perder mucha evaluación sin quedar claramente ganando también
        // se marca, aunque el % de ganar se mueva poco porque la
        // posición ya estaba desequilibrada.
        val cpLoss = if (mateBest == null && matePlayed == null) cpBest - cpPlayed else 0
        if (!forcedOrBook && cpPlayed < 300) {
            if (cpLoss >= 300 && cls.ordinal < MoveClass.MISTAKE.ordinal) cls = MoveClass.MISTAKE
            else if (cpLoss >= 150 && cls.ordinal < MoveClass.INACCURACY.ordinal) cls = MoveClass.INACCURACY
        }
        // Dejar pasar un mate en 1 o 2 es un error aunque se siga ganando.
        if (missedMate && (mateBest ?: 99) <= 2 && cls.ordinal < MoveClass.INACCURACY.ordinal &&
            cls != MoveClass.FORCED) {
            cls = MoveClass.INACCURACY
        }
        // Decisión de Santini: una jugada de peón inicial no se llama
        // imprecisa. Los errores reales (caída >= 10%) sí se marcan.
        if (moveNumber <= 2 && movedType == PieceType.PAWN && cls == MoveClass.INACCURACY && !missedMate) {
            cls = MoveClass.GOOD
        }

        var accuracy = accuracyFromDrop(drop)
        accuracy = when (cls) {
            MoveClass.FORCED, MoveClass.BEST -> 100
            MoveClass.BOOK      -> max(accuracy, 95)
            MoveClass.EXCELLENT -> accuracy.coerceIn(90, 99)
            MoveClass.GOOD      -> accuracy.coerceIn(78, 89)
            MoveClass.INACCURACY -> accuracy.coerceIn(60, 77)
            MoveClass.MISTAKE   -> accuracy.coerceIn(35, 59)
            MoveClass.BLUNDER   -> accuracy.coerceAtMost(34)
        }

        // ── Motivos de la jugada ─────────────────────────────────────
        val targets     = if (isMate) emptyList() else attackTargets(boardAfter, played, mover)
        // Dos objetivos (al menos uno que no sea peón) = ataque doble
        val forkTargets = if (targets.size >= 2 && targets.any { it != PieceType.PAWN }) targets else emptyList()
        val threatTarget = targets.singleOrNull()?.takeIf { it != PieceType.PAWN }
        val isRetreat = movedType != PieceType.PAWN && movedType != PieceType.KING && !played.isCastling &&
            capturedType == null &&
            (if (mover == PieceColor.WHITE) played.toRow > played.fromRow else played.toRow < played.fromRow)
        val homeRow = if (mover == PieceColor.WHITE) 7 else 0
        val developsPiece = moveNumber <= 12 && played.fromRow == homeRow && played.toRow != homeRow &&
            (movedType == PieceType.KNIGHT || movedType == PieceType.BISHOP)
        val earlyQueen = moveNumber <= 6 && movedType == PieceType.QUEEN
        val hadCastling = if (mover == PieceColor.WHITE)
            boardBefore.whiteKingsideCastle || boardBefore.whiteQueensideCastle
        else boardBefore.blackKingsideCastle || boardBefore.blackQueensideCastle
        val losesCastling = movedType == PieceType.KING && !played.isCastling && hadCastling &&
            moveNumber <= 25
        val (kr, kc) = boardBefore.kingPos(mover)
        val weakensKing = movedType == PieceType.PAWN && kr == homeRow &&
            ((kc >= 6 && played.fromCol >= 5) || (kc <= 2 && played.fromCol <= 2))

        return MoveReview(
            moveClass       = cls,
            accuracy        = accuracy,
            winBefore       = winBefore,
            winAfter        = winAfter,
            winDrop         = drop,
            cpBestMover     = cpBest,
            cpPlayedMover   = cpPlayed,
            mateBestMover   = mateBest,
            matePlayedMover = matePlayed,
            mover           = mover,
            moveNumber      = moveNumber,
            phase           = phaseOf(boardBefore, moveNumber),
            boardBefore     = boardBefore,
            played          = played,
            best            = best,
            isBest          = isBest,
            onlyMove        = onlyMove,
            playedLine      = playedMoves,
            bestLine        = bestMoves,
            movedType       = movedType,
            capturedType    = capturedType,
            isCheck         = isCheck,
            isMate          = isMate,
            isCastling      = played.isCastling,
            isPromotion     = played.promotion != null,
            materialPlayed  = playedMat.net,
            materialBest    = bestMat.net,
            materialRecapture = playedMatBase.net,
            gainType        = (if (isRecapture) playedMatBase else playedMat).biggestGain,
            lostType        = playedMat.biggestLoss,
            missedType      = bestMat.biggestGain,
            hangsPiece      = hangsPiece,
            rivalReply      = rivalReply,
            followUp        = followUp,
            forkTargets     = forkTargets,
            threatTarget    = threatTarget,
            isRetreat       = isRetreat,
            answersCheck    = wasInCheck,
            developsPiece   = developsPiece,
            earlyQueen      = earlyQueen,
            losesCastling   = losesCastling,
            weakensKing     = weakensKing,
            isRecapture     = isRecapture,
            bestIsRecapture = bestIsRecapture,
            missedMate      = missedMate,
            allowsMate      = allowsMate,
            keepsMate       = keepsMate
        )
    }

    /**
     * Análisis completo de una jugada con Stockfish:
     *   1. MultiPV 2 sobre la posición ANTERIOR: mejor jugada, segunda
     *      opción y la variante que el motor espera.
     *   2. Si la jugada hecha no es ninguna de esas dos, una búsqueda
     *      limitada a ella (searchmoves) desde la misma posición y con
     *      la misma profundidad.
     * Así la jugada hecha y la mejor se miden con la misma vara.
     */
    suspend fun analyze(
        sf:          StockfishEngine,
        boardBefore: Board,
        played:      Move,
        moveNumber:  Int,
        isBook:      Boolean,
        previous:    Pair<Board, Move>? = null,
        depth:       Int  = ANALYSIS_DEPTH,
        maxTimeMs:   Long = ANALYSIS_TIME_MS
    ): MoveReview? {
        val fen   = FenConverter.toFen(boardBefore, moveNumber)
        val uci   = UciMoves.toUci(played)
        val lines = sf.analyzeLines(fen, depth, multiPv = 2, maxTimeMs = maxTimeMs)
        if (lines.isEmpty()) return null
        val playedLine = if (lines.any { it.pv.firstOrNull() == uci }) null
            else sf.analyzeLines(fen, depth, multiPv = 1, searchMoves = listOf(uci),
                                 maxTimeMs = maxTimeMs).firstOrNull()
        return review(boardBefore, played, moveNumber, lines, playedLine, isBook, previous)
    }

    const val ANALYSIS_DEPTH   = 16
    const val ANALYSIS_TIME_MS = 3500L

    // ── Material ──────────────────────────────────────────────────────

    data class MaterialResult(val net: Int, val biggestLoss: PieceType?, val biggestGain: PieceType?)

    fun pawnUnits(type: PieceType) = when (type) {
        PieceType.PAWN -> 1; PieceType.KNIGHT -> 3; PieceType.BISHOP -> 3
        PieceType.ROOK -> 5; PieceType.QUEEN -> 9; PieceType.KING -> 0
    }

    private fun material(board: Board, color: PieceColor): Int {
        var total = 0
        for (r in 0..7) for (c in 0..7) {
            val p = board[r, c] ?: continue
            if (p.color == color) total += pawnUnits(p.type)
        }
        return total
    }

    private fun counts(board: Board, color: PieceColor): IntArray {
        val out = IntArray(PieceType.values().size)
        for (r in 0..7) for (c in 0..7) {
            val p = board[r, c] ?: continue
            if (p.color == color) out[p.type.ordinal]++
        }
        return out
    }

    /**
     * Cambio de material neto para "color" siguiendo la línea del motor.
     * Se mira unas 6 medias jugadas adelante y, si la última fue una
     * captura, se sigue mientras haya capturas (para no cortar a mitad
     * de un intercambio y creer que se ganó o perdió algo).
     */
    fun materialAlongLine(start: Board, line: List<Move>, color: PieceColor,
                          base: Board = start): MaterialResult {
        val rival = color.opponent()
        val baseNet = material(base, color) - material(base, rival)
        var b = start
        var plies = 0
        var lastWasCapture = false
        for (m in line) {
            if (plies >= 6 && !lastWasCapture) break
            if (plies >= 10) break
            lastWasCapture = b[m.toRow, m.toCol] != null || m.isEnPassant
            b = b.applyMove(m)
            plies++
        }
        val net = (material(b, color) - material(b, rival)) - baseNet

        val myBefore = counts(base, color);  val myAfter = counts(b, color)
        val rvBefore = counts(base, rival);  val rvAfter = counts(b, rival)
        var loss: PieceType? = null
        var gain: PieceType? = null
        // De la más valiosa a la menos: la primera que cambia en neto
        for (t in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)) {
            val myLost = myBefore[t.ordinal] - myAfter[t.ordinal]
            val rvLost = rvBefore[t.ordinal] - rvAfter[t.ordinal]
            if (loss == null && myLost > rvLost && net < 0) loss = t
            if (gain == null && rvLost > myLost && net > 0) gain = t
        }
        return MaterialResult(net, loss, gain)
    }

    // ── Motivos ───────────────────────────────────────────────────────

    /**
     * Piezas rivales atacadas por la pieza que acaba de mover que valen
     * más que ella o están sin defensa. Dos o más = ataque doble.
     */
    private fun attackTargets(after: Board, played: Move, mover: PieceColor): List<PieceType> {
        val piece = after[played.toRow, played.toCol] ?: return emptyList()
        val myValue = if (piece.type == PieceType.KING) 100 else pawnUnits(piece.type)
        val attacks = MoveGenerator.generatePseudoLegalMoves(after, mover)
            .filter { it.fromRow == played.toRow && it.fromCol == played.toCol && !it.isCastling }
        val targets = mutableListOf<PieceType>()
        val seen = mutableSetOf<Int>()
        for (m in attacks) {
            val target = after[m.toRow, m.toCol] ?: continue
            if (target.color == mover || !seen.add(m.toRow * 8 + m.toCol)) continue
            val defended = MoveGenerator.isSquareAttacked(after, m.toRow, m.toCol, mover)
            val valuable = target.type == PieceType.KING || pawnUnits(target.type) > myValue
            if (valuable || !defended) targets += target.type
        }
        return targets
    }

    private fun phaseOf(board: Board, moveNumber: Int): GamePhase {
        var nonPawn = 0
        for (r in 0..7) for (c in 0..7) {
            val p = board[r, c] ?: continue
            if (p.type != PieceType.PAWN && p.type != PieceType.KING) nonPawn += pawnUnits(p.type)
        }
        return when {
            nonPawn <= 26  -> GamePhase.ENDGAME   // ej. Torre + pieza menor por bando
            moveNumber <= 12 -> GamePhase.OPENING
            else           -> GamePhase.MIDDLEGAME
        }
    }
}
