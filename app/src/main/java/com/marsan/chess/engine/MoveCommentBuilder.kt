package com.marsan.chess.engine

import kotlin.math.abs

/**
 * Fuente de textos traducidos para los comentarios. En la app se
 * implementa con los recursos de strings.xml (ES y EN); así este
 * archivo no depende de Android y se puede probar contra Stockfish
 * en una computadora.
 */
interface AnalysisText {
    fun s(key: String, vararg args: Any): String
    fun pool(key: String): List<String>
}

/**
 * Convierte los hechos de MoveReview en un comentario realista.
 *
 * Reglas:
 *   - Cada afirmación sale de un hecho medido (mate, material de la
 *     línea del motor, caída de evaluación). Si no hay un hecho
 *     concreto, el comentario solo habla de la evaluación.
 *   - Primero el veredicto con su causa, luego qué era mejor y por qué,
 *     y al final la línea probable: lo que el rival responde y cómo
 *     sigue la partida.
 *   - La apertura se nombra una sola vez (lo decide quien llama).
 */
class MoveCommentBuilder(private val t: AnalysisText) {

    data class Comment(val header: String, val label: String, val stats: String,
                       val body: String, val kinds: String)

    fun build(r: MoveReview, openingName: String?, step: Int): Comment {
        val letters  = t.s("an2_piece_letters")
        val color    = colorName(r.mover)
        val rival    = colorName(r.mover.opponent())
        val before   = r.boardBefore
        val bestSan   = r.best?.let { SanFormatter.toSan(before, it, letters) }
        val afterBoard = before.applyMove(r.played)
        val replySan = r.rivalReply?.let { SanFormatter.numbered(afterBoard, it, nextNumber(r), letters) }
        val pick = { key: String -> t.pool(key).let { if (it.isEmpty()) "" else it[step % it.size] } }

        val parts = mutableListOf<String>()

        // ── 0. Apertura (una sola vez) ──────────────────────────────
        if (openingName != null) parts += t.s("an2_opening_header", openingName)

        // ── 1. Contexto cuando la partida ya estaba decidida ────────
        val decidedLost = r.winBefore < 12.0 && r.moveClass != MoveClass.FORCED && !r.allowsMate
        val decidedWon  = r.winBefore > 88.0 && r.winAfter > 80.0 && !r.isMate &&
            r.mateBestMover == null

        // ── 2. Veredicto con su causa ───────────────────────────────
        val bad = r.moveClass.ordinal >= MoveClass.INACCURACY.ordinal
        val materialVsBest = r.materialPlayed - r.materialBest
        // Las líneas del motor se miran solo unas jugadas adelante, así
        // que el material puede no estar "asentado" (una pieza clavada
        // que cae más tarde, por ejemplo). Solo se habla de material
        // cuando la evaluación lo confirma.
        val cpLoss = if (r.mateBestMover == null && r.matePlayedMover == null)
            r.cpBestMover - r.cpPlayedMover else 0
        val lossConfirmed = { amount: Int -> cpLoss >= 50 * amount.coerceIn(1, 3) }
        val gained = if (r.isRecapture) r.materialRecapture else r.materialPlayed
        val gainConfirmed = gained >= 1 && r.matePlayedMover == null &&
            r.cpPlayedMover >= 50 * gained.coerceAtMost(5)
        val verdict: String = when {
            r.moveClass == MoveClass.FORCED -> t.s("an2_forced")

            r.isMate -> t.s("an2_mate_delivered", color)

            r.allowsMate -> {
                val n = abs(r.matePlayedMover ?: 1)
                if (n <= 1 && replySan != null) t.s("an2_allows_mate1", replySan)
                else t.s("an2_allows_mate", n, rival, replySan ?: t.s("an2_reply_generic"))
            }

            r.missedMate -> t.s("an2_missed_mate", color, abs(r.mateBestMover ?: 0),
                bestSan ?: t.s("an2_other_move"))

            r.keepsMate && r.isBest -> t.s("an2_keeps_mate_best", abs(r.matePlayedMover ?: 0))

            r.keepsMate -> t.s("an2_keeps_mate_slower", bestSan ?: "", abs(r.mateBestMover ?: 0),
                abs(r.matePlayedMover ?: 0))

            bad && r.hangsPiece && replySan != null && lossConfirmed(2) ->
                t.s("an2_hangs", pieceName(r.movedType), SanFormatter.square(r.played.toRow, r.played.toCol),
                    rival, replySan)

            bad && r.materialPlayed <= -1 && materialVsBest <= -1 && replySan != null &&
                lossConfirmed(-materialVsBest) ->
                t.s("an2_loses_material", materialName(-r.materialPlayed, r.lostType), replySan, color)

            bad && r.bestIsRecapture && bestSan != null && capturedNow(r) == null && lossConfirmed(1) ->
                t.s("an2_no_recapture", bestSan)

            bad && materialVsBest <= -2 && bestSan != null && lossConfirmed(-materialVsBest) ->
                t.s("an2_missed_gain", materialName(materialVsBest * -1, r.missedType), bestSan)

            bad && r.winBefore >= 60.0 && r.winAfter <= 40.0 ->
                t.s("an2_flip", color, eval(r.cpBestMover, r.mateBestMover), eval(r.cpPlayedMover, r.matePlayedMover))

            bad && r.winBefore >= 70.0 && r.winAfter < 60.0 ->
                t.s("an2_drained", color, eval(r.cpBestMover, r.mateBestMover), eval(r.cpPlayedMover, r.matePlayedMover))

            r.moveClass == MoveClass.BLUNDER || r.moveClass == MoveClass.MISTAKE ->
                t.s("an2_positional_bad", color, eval(r.cpBestMover, r.mateBestMover), eval(r.cpPlayedMover, r.matePlayedMover))

            r.moveClass == MoveClass.INACCURACY ->
                t.s("an2_positional_small", color, eval(r.cpBestMover, r.mateBestMover), eval(r.cpPlayedMover, r.matePlayedMover))

            r.moveClass == MoveClass.BOOK -> pick("an2_book_pool")

            r.onlyMove -> t.s("an2_only_move")

            r.forkTargets.isNotEmpty() ->
                t.s("an2_fork", pieceName(r.movedType), joinPieces(r.forkTargets))

            r.capturedType != null && gainConfirmed ->
                t.s(if (r.isRecapture) "an2_recapture_plus" else "an2_wins_material",
                    materialName(gained, r.gainType ?: r.capturedType))

            r.isRecapture && gained >= 0 -> t.s("an2_recapture")

            r.capturedType != null && gained == 0 -> t.s("an2_fair_trade")

            r.isCastling -> pick("an2_castle_pool")

            r.isPromotion -> t.s("an2_promotion")

            r.developsPiece -> pick("an2_develops_pool").format(pieceName(r.movedType),
                SanFormatter.square(r.played.toRow, r.played.toCol))

            r.isCheck -> t.s("an2_check_good")

            r.moveClass == MoveClass.BEST -> pick("an2_best_pool")

            r.moveClass == MoveClass.EXCELLENT -> pick("an2_excellent_pool")

            else -> t.s("an2_good", bestSan ?: t.s("an2_other_move"))
        }

        // El contexto solo se menciona cuando explica por qué una pérdida
        // grande de evaluación recibe una nota moderada.
        val showContext = cpLoss >= 100
        if (decidedLost && showContext) parts += t.s("an2_ctx_lost", color, eval(r.cpBestMover, r.mateBestMover))
        else if (decidedWon && showContext && r.moveClass.ordinal <= MoveClass.INACCURACY.ordinal) {
            parts += t.s("an2_ctx_won", color, eval(r.cpBestMover, r.mateBestMover))
        }
        parts += verdict

        // La respuesta del rival, cuando el veredicto no la nombró ya
        val verdictNamesReply = r.allowsMate || (bad && r.hangsPiece) ||
            verdict.contains(replySan ?: "\u0000")
        if (bad && !verdictNamesReply && replySan != null) {
            parts += t.s("an2_reply_hint", rival, replySan)
        }

        // ── 3. Pistas de por qué (solo en jugadas flojas) ───────────
        if (bad || r.winDrop >= 3.0) {
            if (r.earlyQueen) parts += t.s("an2_hint_early_queen")
            if (r.losesCastling) parts += t.s("an2_hint_castling")
            if (r.weakensKing) parts += t.s("an2_hint_weak_king")
        }

        // ── 4. Qué era mejor ─────────────────────────────────────────
        val verdictNamesBest = r.missedMate || (r.keepsMate && !r.isBest) || verdict.contains(bestSan ?: "\u0000") ||
            (bad && materialVsBest <= -2 && !r.hangsPiece && !(r.materialPlayed <= -1 && materialVsBest <= -1))
        if (!r.isBest && bestSan != null && r.moveClass != MoveClass.BOOK &&
            r.moveClass != MoveClass.FORCED && r.winDrop >= 2.0 && !r.isMate) {
            val bestLine = SanFormatter.lineToSan(before, r.bestLine, r.moveNumber, letters, 4)
            parts += if (verdictNamesBest) t.s("an2_best_line", bestLine)
                     else t.s("an2_better", bestSan, eval(r.cpBestMover, r.mateBestMover), color, bestLine)
        }

        // ── 5. Lo que viene: respuesta del rival y continuación ─────
        if (!r.isMate && r.playedLine.size >= 2) {
            parts += t.s("an2_line", SanFormatter.lineToSan(before, r.playedLine, r.moveNumber, letters, 6))
        }

        // ── Encabezado y datos ───────────────────────────────────────
        val header = SanFormatter.numbered(before, r.played, r.moveNumber, letters)
        val label = t.s(when {
            r.moveClass == MoveClass.BEST && r.onlyMove -> "an2_class_only"
            else -> "an2_class_" + r.moveClass.name.lowercase()
        })
        val whiteSign = if (r.mover == PieceColor.WHITE) 1 else -1
        val stats = buildString {
            append(t.s("an2_eval_label", evalWhite(r.cpPlayedMover * whiteSign, r.matePlayedMover?.let { it * whiteSign })))
            if (r.isBest) append("   ·   " + t.s("an2_best_check"))
            else if (bestSan != null) append("   ·   " + t.s("an2_best_label", bestSan))
        }
        val body = parts.filter { it.isNotBlank() }.joinToString("\n\n")
        return Comment(header, label, stats, body, kindsOf(r).joinToString(" · ") { t.s(it) })
    }

    /** Tipo de jugada, para el subtítulo: "Captura · Jaque", "Desarrollo"... */
    private fun kindsOf(r: MoveReview): List<String> {
        val k = mutableListOf<String>()
        when {
            r.isCastling   -> k += "an2_kind_castle"
            r.isPromotion  -> k += "an2_kind_promotion"
            r.isRecapture  -> k += "an2_kind_recapture"
            r.answersCheck && r.capturedType == null -> k += "an2_kind_check_defense"
            r.capturedType != null -> k += "an2_kind_capture"
            r.developsPiece -> k += "an2_kind_develop"
            r.movedType == PieceType.PAWN -> k += "an2_kind_pawn"
            r.isRetreat    -> k += "an2_kind_retreat"
            r.movedType == PieceType.KING -> k += "an2_kind_king"
            else           -> k += "an2_kind_maneuver"
        }
        when {
            r.isMate  -> k += "an2_kind_mate"
            r.isCheck -> k += "an2_kind_check"
        }
        if (r.forkTargets.isNotEmpty()) k += "an2_kind_fork"
        else if (r.threatTarget != null && !r.isCheck) k += "an2_kind_threat"
        return k
    }

    // ── Ayudas de texto ───────────────────────────────────────────────

    private fun capturedNow(r: MoveReview) = r.capturedType

    private fun nextNumber(r: MoveReview) =
        if (r.mover == PieceColor.BLACK) r.moveNumber + 1 else r.moveNumber

    private fun colorName(c: PieceColor) =
        t.s(if (c == PieceColor.WHITE) "color_white" else "color_black")

    private fun pieceName(type: PieceType) = t.s(when (type) {
        PieceType.KING   -> "piece_king"
        PieceType.QUEEN  -> "piece_queen"
        PieceType.ROOK   -> "piece_rook"
        PieceType.BISHOP -> "piece_bishop"
        PieceType.KNIGHT -> "piece_knight"
        PieceType.PAWN   -> "piece_pawn"
    })

    private fun joinPieces(types: List<PieceType>): String {
        val names = types.map { pieceName(it) }
        return if (names.size == 1) names[0]
        else names.dropLast(1).joinToString(", ") + " " + t.s("an2_and") + " " + names.last()
    }

    /** "un peón", "3 peones", "la calidad", "un Caballo", "una Torre", "la Dama". */
    private fun materialName(amount: Int, type: PieceType?): String = when {
        type == PieceType.QUEEN && amount >= 6 -> t.s("an2_mat_queen")
        type == PieceType.ROOK && amount >= 4  -> t.s("an2_mat_rook")
        type == PieceType.ROOK && amount == 2  -> t.s("an2_mat_exchange")
        type == PieceType.BISHOP && amount >= 3 -> t.s("an2_mat_bishop")
        type == PieceType.KNIGHT && amount >= 3 -> t.s("an2_mat_knight")
        amount <= 1 -> t.s("an2_mat_pawn")
        else        -> t.s("an2_mat_pawns", amount)
    }

    /** Evaluación desde el que movió: "+1.3", "-0.4" o "mate en 3". */
    private fun eval(cp: Int, mate: Int?): String = when {
        mate != null && mate > 0 -> t.s("an2_eval_mate_for", mate)
        mate != null && mate < 0 -> t.s("an2_eval_mate_against", -mate)
        else -> String.format(java.util.Locale.US, "%+.1f", cp / 100.0)
    }

    /** Evaluación estándar del motor (positivo = Blancas mejor). */
    private fun evalWhite(cp: Int, mate: Int?): String = when {
        mate != null -> t.s(if (mate > 0) "an2_eval_mate_white" else "an2_eval_mate_black", abs(mate))
        else -> String.format(java.util.Locale.US, "%+.1f", cp / 100.0)
    }
}
