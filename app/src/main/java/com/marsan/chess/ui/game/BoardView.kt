package com.marsan.chess.ui.game

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.marsan.chess.engine.*
import com.marsan.chess.util.PreferencesManager

data class BoardPalette(val lightColor: Int, val darkColor: Int)

object BoardPalettes {
    val A = BoardPalette(Color.parseColor("#DCE8F0"), Color.parseColor("#6A96B0"))
    val all = listOf(A)
    fun random() = A
}

class BoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    var palette: BoardPalette = BoardPalettes.A
        set(value) { field = value; invalidate() }

    private val colorCheck       = Color.parseColor("#CC2020")
    private val colorHintOnDark  = Color.argb(200, 255, 255, 255)
    private val colorHintOnLight = Color.argb(200, 30, 30, 30)
    private val colorRingOnDark  = Color.argb(220, 255, 255, 255)
    private val colorRingOnLight = Color.argb(220, 30, 30, 30)

    private val paintCell  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintPiece = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paintDot   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintRing  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintUnicode = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    // Selector de promoción
    private val paintPromoBg     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintPromoBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val paintPromoShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0)
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    var pendingPromotion: PendingPromotion? = null
        set(value) { field = value; invalidate() }
    var onPromotionSelected: ((PieceType) -> Unit)? = null
    private val promoRects = mutableMapOf<PieceType, RectF>()
    private val promoOrder = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

    // Estilo de piezas leído de PreferencesManager
    private var pieceStyle: Int = 1
    private val prefs by lazy { PreferencesManager.getInstance(context) }

    // Animación
    private var animating = false
    private var animPiece: Piece? = null
    private var animFromX = 0f; private var animFromY = 0f
    private var animToX   = 0f; private var animToY   = 0f
    private var animProgress = 0f; private var animStartTime = 0L
    private val animDuration = 260L
    private var animSkipRow = -1; private var animSkipCol = -1
    var animBoard: Board? = null

    private val bitmapCache = mutableMapOf<String, Bitmap>()

    // Pieza capturada que se desvanece durante la animación de captura
    private var capturedPiece: Piece? = null
    private var capturedRow = -1; private var capturedCol = -1

    // Pintura de la sombra bajo la pieza en vuelo
    private val paintShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun getPieceBitmap(piece: Piece, style: Int = pieceStyle): Bitmap? {
        val prefix  = when (style) { 3 -> "piece3"; else -> "piece" }
        val color   = if (piece.color == PieceColor.WHITE) "w" else "b"
        val type    = piece.type.name.lowercase()
        val key     = "${prefix}_${color}_${type}_${cellSize.toInt()}"
        bitmapCache[key]?.let { return it }
        val resName = "${prefix}_${color}_${type}"
        val resId   = context.resources.getIdentifier(resName, "drawable", context.packageName)
        if (resId == 0) return null
        val orig    = BitmapFactory.decodeResource(context.resources, resId) ?: return null
        val size    = (cellSize * 0.86f).toInt().coerceAtLeast(1)
        val scaled  = Bitmap.createScaledBitmap(orig, size, size, true)
        bitmapCache[key] = scaled
        return scaled
    }

    private fun getPromoBitmap(type: PieceType, color: PieceColor, sizePx: Int, style: Int): Bitmap? {
        val prefix  = when (style) { 3 -> "piece3"; else -> "piece" }
        val c       = if (color == PieceColor.WHITE) "w" else "b"
        val key     = "promo_${prefix}_${c}_${type}_$sizePx"
        bitmapCache[key]?.let { return it }
        val resId   = context.resources.getIdentifier("${prefix}_${c}_${type.name.lowercase()}", "drawable", context.packageName)
        if (resId == 0) return null
        val orig    = BitmapFactory.decodeResource(context.resources, resId) ?: return null
        val scaled  = Bitmap.createScaledBitmap(orig, sizePx, sizePx, true)
        bitmapCache[key] = scaled
        return scaled
    }

    // Símbolos Unicode por tipo y color
    private fun unicodeSymbol(piece: Piece): String = when (piece.color) {
        PieceColor.WHITE -> when (piece.type) {
            PieceType.KING   -> "♔"; PieceType.QUEEN  -> "♕"
            PieceType.ROOK   -> "♖"; PieceType.BISHOP -> "♗"
            PieceType.KNIGHT -> "♘"; PieceType.PAWN   -> "♙"
        }
        PieceColor.BLACK -> when (piece.type) {
            PieceType.KING   -> "♚"; PieceType.QUEEN  -> "♛"
            PieceType.ROOK   -> "♜"; PieceType.BISHOP -> "♝"
            PieceType.KNIGHT -> "♞"; PieceType.PAWN   -> "♟"
        }
    }
    private fun unicodeSymbolForType(type: PieceType, color: PieceColor): String {
        val p = Piece(type = type, color = color)
        return unicodeSymbol(p)
    }

    var board: Board? = null
        set(value) { field = value; invalidate() }
    var selectedSquare: Pair<Int, Int>? = null
        set(value) { field = value; invalidate() }
    var legalMoves: List<Move> = emptyList()
        set(value) { field = value; invalidate() }
    var lastMove: Move? = null
        set(value) { field = value; invalidate() }
    var kingInCheck: Pair<Int, Int>? = null
        set(value) { field = value; invalidate() }
    var checkAttackers: List<Pair<Int, Int>> = emptyList()
        set(value) { field = value; invalidate() }
    var playerColor: PieceColor = PieceColor.WHITE
        set(value) { field = value; isFlipped = (value == PieceColor.BLACK); invalidate() }
    private var isFlipped = false
    var onSquareTapped: ((Int, Int) -> Unit)? = null

    private var cellSize  = 0f
    private var boardSize = 0f
    private var labelPad  = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        paintBorder.style       = Paint.Style.STROKE
        paintBorder.color       = Color.parseColor("#CCCCCC")
        paintBorder.strokeWidth = 1f
        paintDot.style          = Paint.Style.FILL
        paintRing.style         = Paint.Style.STROKE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cellSize != w / 8f) { bitmapCache.values.forEach { it.recycle() }; bitmapCache.clear() }
        boardSize = minOf(w.toFloat(), h.toFloat())
        cellSize  = boardSize / 8f
        labelPad  = cellSize * 0.36f
        paintLabel.textSize   = cellSize * 0.34f
        paintLabel.typeface   = android.graphics.Typeface.DEFAULT_BOLD
        paintRing.strokeWidth = cellSize * 0.07f
        paintPromoBorder.strokeWidth = cellSize * 0.04f
        paintUnicode.textSize = cellSize * 0.70f
    }

    override fun onDraw(canvas: Canvas) {
        pieceStyle = prefs.pieceStyle   // leer en cada draw para reflejar cambios en prefs
        canvas.drawColor(palette.lightColor)
        val b = animBoard ?: board ?: return
        drawCells(canvas, b)
        drawLabels(canvas)
        if (animating) {
            drawPieces(canvas, b, animSkipRow, animSkipCol)
            drawAnimPiece(canvas)
            val elapsed = System.currentTimeMillis() - animStartTime
            animProgress = (elapsed.toFloat() / animDuration).coerceIn(0f, 1f)
            if (animProgress >= 1f) {
                animating = false; animSkipRow = -1; animSkipCol = -1
                animBoard = null; animPiece = null; capturedPiece = null
            }
            postInvalidateOnAnimation()
        } else {
            drawPieces(canvas, b, -1, -1)
        }
        canvas.drawRect(RectF(0f, 0f, boardSize, boardSize), paintBorder)
        pendingPromotion?.let { drawPromotionPicker(canvas, it) }
    }

    private fun drawCells(canvas: Canvas, board: Board) {
        val hints    = legalMoves.map { it.toRow to it.toCol }.toSet()
        val lastFrom = lastMove?.let { it.fromRow to it.fromCol }
        val lastTo   = lastMove?.let { it.toRow   to it.toCol   }

        // Pulso de jaque: factor 0..1 que oscila ~1 vez por segundo.
        // La casilla del rey "late" entre rojo suave y rojo intenso;
        // los atacantes quedan en un rojo fijo más tenue.
        val pulse = if (kingInCheck != null)
            (Math.sin(System.currentTimeMillis() / 160.0) * 0.5 + 0.5).toFloat()
        else 0f

        for (row in 0..7) for (col in 0..7) {
            val dr = if (isFlipped) 7-row else row
            val dc = if (isFlipped) 7-col else col
            val x  = col * cellSize; val y = row * cellSize
            val isLight    = (dr+dc)%2==0
            val isSelected = selectedSquare?.let { it.first==dr && it.second==dc } == true
            val isKing     = kingInCheck?.let { it.first==dr && it.second==dc } == true
            val isAttacker = checkAttackers.any { it.first==dr && it.second==dc }
            val isLast     = lastFrom?.let{it.first==dr&&it.second==dc}==true ||
                             lastTo?.let  {it.first==dr&&it.second==dc}==true
            val base       = if (isLight) palette.lightColor else palette.darkColor

            paintCell.color = when {
                isKing     -> blendColor(base, colorCheck, 0.45f + 0.40f * pulse)
                isAttacker -> blendColor(base, colorCheck, 0.40f)
                isSelected -> blendColor(palette.darkColor, Color.WHITE, 0.3f)
                isLast     -> if (isLight) blendColor(palette.lightColor, palette.darkColor, 0.25f)
                              else blendColor(palette.darkColor, palette.lightColor, 0.2f)
                else       -> base
            }
            canvas.drawRect(x, y, x+cellSize, y+cellSize, paintCell)

            if (hints.contains(dr to dc)) {
                val cx = x+cellSize/2f; val cy = y+cellSize/2f
                if (board[dr,dc] != null) {
                    paintRing.color = if (isLight) colorRingOnLight else colorRingOnDark
                    canvas.drawCircle(cx, cy, cellSize*0.44f, paintRing)
                } else {
                    paintDot.color = if (isLight) colorHintOnLight else colorHintOnDark
                    canvas.drawCircle(cx, cy, cellSize*0.16f, paintDot)
                }
            }
        }

        // Mantener el latido vivo mientras haya jaque en pantalla
        if (kingInCheck != null && !animating) postInvalidateOnAnimation()
    }

    private fun blendColor(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1)   + (Color.red(c2)   - Color.red(c1))   * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1)  + (Color.blue(c2)  - Color.blue(c1))  * t).toInt()
        return Color.rgb(r, g, b)
    }

    private fun drawLabels(canvas: Canvas) {
        val files = listOf("a","b","c","d","e","f","g","h")
        val ranks = listOf("8","7","6","5","4","3","2","1")

        // Colores del tablero con alta opacidad para máximo contraste
        val darkColor  = Color.argb(230,
            Color.red(palette.darkColor), Color.green(palette.darkColor), Color.blue(palette.darkColor))
        val lightColor = Color.argb(230,
            Color.red(palette.lightColor), Color.green(palette.lightColor), Color.blue(palette.lightColor))

        for (i in 0..7) {
            // La esquina inferior derecha de la columna i está en la fila 7 (fila 8 del tablero)
            // La casilla (fila=7, col=i): es oscura si (7+i)%2 != 0, clara si (7+i)%2 == 0
            // La letra va sobre esa casilla → usar el color CONTRARIO para que resalte
            val fileSquareIsLight = (7 + i) % 2 == 0
            // Color opuesto al cuadro donde va la letra
            val fileColor = if (fileSquareIsLight) darkColor else lightColor

            paintLabel.color     = fileColor
            paintLabel.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                if (isFlipped) files[7 - i] else files[i],
                i * cellSize + cellSize - cellSize * 0.05f,
                7 * cellSize + cellSize - cellSize * 0.04f,
                paintLabel
            )

            // El número de fila i va en la esquina superior izquierda de la fila i
            // La casilla (fila=i, col=0): es oscura si (i+0)%2 != 0, clara si (i+0)%2 == 0
            val rankSquareIsLight = i % 2 == 0
            val rankColor = if (rankSquareIsLight) darkColor else lightColor

            paintLabel.color     = rankColor
            paintLabel.textAlign = Paint.Align.LEFT
            canvas.drawText(
                if (isFlipped) ranks[7 - i] else ranks[i],
                cellSize * 0.05f,
                i * cellSize + labelPad * 1.0f,
                paintLabel
            )
        }
        paintLabel.textAlign = Paint.Align.CENTER
    }

    private fun drawPieces(canvas: Canvas, board: Board, skipRow: Int, skipCol: Int) {
        for (row in 0..7) for (col in 0..7) {
            val dr = if (isFlipped) 7-row else row
            val dc = if (isFlipped) 7-col else col
            if (dr==skipRow && dc==skipCol) continue
            val piece = board[dr,dc] ?: continue
            drawPiece(canvas, piece, col*cellSize, row*cellSize)
        }
    }

    private fun drawPiece(canvas: Canvas, piece: Piece, x: Float, y: Float) {
        when (pieceStyle) {
            2 -> {
                // Unicode: silueta negra (stroke) + relleno para visibilidad en cuadros claros
                val symbol  = unicodeSymbol(piece)
                val isWhite = piece.color == PieceColor.WHITE
                val cx      = x + cellSize / 2f
                val cy      = y + cellSize / 2f - (paintUnicode.descent() + paintUnicode.ascent()) / 2f
                if (isWhite) {
                    // Stroke negro como silueta
                    paintUnicode.style       = Paint.Style.STROKE
                    paintUnicode.strokeWidth = cellSize * 0.06f
                    paintUnicode.color       = Color.parseColor("#111111")
                    canvas.drawText(symbol, cx, cy, paintUnicode)
                    // Relleno blanco encima
                    paintUnicode.style       = Paint.Style.FILL
                    paintUnicode.strokeWidth = 0f
                    paintUnicode.color       = Color.parseColor("#FFFFFF")
                    canvas.drawText(symbol, cx, cy, paintUnicode)
                } else {
                    paintUnicode.style       = Paint.Style.FILL
                    paintUnicode.strokeWidth = 0f
                    paintUnicode.color       = Color.parseColor("#111111")
                    canvas.drawText(symbol, cx, cy, paintUnicode)
                }
            }
            else -> {
                val bmp = getPieceBitmap(piece) ?: return
                val offset = (cellSize - bmp.width) / 2f
                canvas.drawBitmap(bmp, x + offset, y + offset, paintPiece)
            }
        }
    }

    private fun drawAnimPiece(canvas: Canvas) {
        val piece = animPiece ?: return
        val t     = easeInOut(animProgress)
        val x     = animFromX + (animToX - animFromX) * t
        val y     = animFromY + (animToY - animFromY) * t

        // "Vuelo": la pieza se eleva al partir y aterriza al llegar.
        // lift va 0 → 1 → 0 durante el trayecto y controla la escala
        // de la pieza y el tamaño/opacidad de su sombra.
        val lift  = Math.sin(Math.PI * t).toFloat()
        val scale = 1f + 0.12f * lift

        // Pieza capturada desvaneciéndose en la casilla de destino
        capturedPiece?.let { cap ->
            val dc = if (isFlipped) 7-capturedCol else capturedCol
            val dr = if (isFlipped) 7-capturedRow else capturedRow
            val alpha = ((1f - animProgress) * 255).toInt().coerceIn(0, 255)
            drawPieceWithAlpha(canvas, cap, dc*cellSize, dr*cellSize, alpha)
        }

        // Sombra elíptica bajo la pieza: crece con la elevación
        val cx = x + cellSize / 2f
        val shadowW = cellSize * (0.34f + 0.16f * lift)
        val shadowH = cellSize * (0.10f + 0.05f * lift)
        val shadowY = y + cellSize * 0.88f
        paintShadow.color = Color.argb((70 * lift).toInt() + 20, 0, 0, 0)
        canvas.drawOval(RectF(cx-shadowW, shadowY-shadowH, cx+shadowW, shadowY+shadowH), paintShadow)

        canvas.save()
        canvas.scale(scale, scale, cx, y + cellSize / 2f)
        when (pieceStyle) {
            2 -> {
                val symbol  = unicodeSymbol(piece)
                val isWhite = piece.color == PieceColor.WHITE
                val tcx     = x + cellSize / 2f
                val tcy     = y + cellSize / 2f - (paintUnicode.descent() + paintUnicode.ascent()) / 2f
                if (isWhite) {
                    paintUnicode.style = Paint.Style.STROKE
                    paintUnicode.strokeWidth = cellSize * 0.06f
                    paintUnicode.color = Color.parseColor("#111111")
                    canvas.drawText(symbol, tcx, tcy, paintUnicode)
                    paintUnicode.style = Paint.Style.FILL
                    paintUnicode.strokeWidth = 0f
                    paintUnicode.color = Color.parseColor("#FFFFFF")
                    canvas.drawText(symbol, tcx, tcy, paintUnicode)
                } else {
                    paintUnicode.style = Paint.Style.FILL
                    paintUnicode.strokeWidth = 0f
                    paintUnicode.color = Color.parseColor("#111111")
                    canvas.drawText(symbol, tcx, tcy, paintUnicode)
                }
            }
            else -> {
                val bmp = getPieceBitmap(piece) ?: run { canvas.restore(); return }
                canvas.drawBitmap(bmp, x, y, paintPiece)
            }
        }
        canvas.restore()
    }

    /** Dibuja una pieza con transparencia (para la captura desvanecida). */
    private fun drawPieceWithAlpha(canvas: Canvas, piece: Piece, x: Float, y: Float, alpha: Int) {
        when (pieceStyle) {
            2 -> {
                val symbol = unicodeSymbol(piece)
                val cx = x + cellSize / 2f
                val cy = y + cellSize / 2f - (paintUnicode.descent() + paintUnicode.ascent()) / 2f
                paintUnicode.style = Paint.Style.FILL
                paintUnicode.strokeWidth = 0f
                paintUnicode.color =
                    if (piece.color == PieceColor.WHITE) Color.argb(alpha, 255, 255, 255)
                    else Color.argb(alpha, 17, 17, 17)
                canvas.drawText(symbol, cx, cy, paintUnicode)
            }
            else -> {
                val bmp = getPieceBitmap(piece) ?: return
                val offset = (cellSize - bmp.width) / 2f
                paintPiece.alpha = alpha
                canvas.drawBitmap(bmp, x + offset, y + offset, paintPiece)
                paintPiece.alpha = 255
            }
        }
    }

    private fun easeInOut(t: Float) = if (t < 0.5f) 2f*t*t else -1f+(4f-2f*t)*t

    fun startMoveAnimation(move: Move, boardBefore: Board) {
        val piece  = boardBefore[move.fromRow, move.fromCol] ?: return
        val bmp    = if (pieceStyle != 2) getPieceBitmap(piece) else null
        val offset = if (bmp != null) (cellSize - bmp.width) / 2f else 0f
        val fCol   = if (isFlipped) 7-move.fromCol else move.fromCol
        val fRow   = if (isFlipped) 7-move.fromRow else move.fromRow
        val tCol   = if (isFlipped) 7-move.toCol   else move.toCol
        val tRow   = if (isFlipped) 7-move.toRow   else move.toRow

        // Si hay pieza rival en el destino, se desvanecerá durante el vuelo
        capturedPiece = boardBefore[move.toRow, move.toCol]
        capturedRow   = move.toRow; capturedCol = move.toCol

        animPiece    = piece; animSkipRow = move.toRow; animSkipCol = move.toCol
        animFromX    = fCol*cellSize+offset; animFromY = fRow*cellSize+offset
        animToX      = tCol*cellSize+offset; animToY   = tRow*cellSize+offset
        animProgress = 0f; animStartTime = System.currentTimeMillis(); animating = true
        invalidate()
    }

    // ── SELECTOR DE PROMOCIÓN ─────────────────────────────────────────

    private fun drawPromotionPicker(canvas: Canvas, pending: PendingPromotion) {
        promoRects.clear()
        val dc = if (isFlipped) 7 - pending.col else pending.col
        val dr = if (isFlipped) 7 - pending.row else pending.row
        val cellX = dc * cellSize; val cellY = dr * cellSize
        val panelWidth  = cellSize * 0.92f
        val itemHeight  = cellSize * 0.92f
        val panelHeight = itemHeight * 4
        val panelLeft   = (cellX + cellSize / 2f - panelWidth / 2f)
            .coerceIn(4f, boardSize - panelWidth - 4f)
        val spaceBelow = boardSize - (cellY + cellSize)
        val opensDown  = spaceBelow >= panelHeight || cellY < boardSize / 2f
        val panelTop   = if (opensDown) (cellY).coerceAtMost(boardSize - panelHeight - 4f)
                         else (cellY + cellSize - panelHeight).coerceAtLeast(4f)
        val panelRect  = RectF(panelLeft, panelTop, panelLeft+panelWidth, panelTop+panelHeight)
        val corner     = cellSize * 0.16f

        canvas.drawRoundRect(RectF(panelRect.left, panelRect.top+3f, panelRect.right, panelRect.bottom+3f),
            corner, corner, paintPromoShadow)
        paintPromoBg.color = Color.WHITE
        canvas.drawRoundRect(panelRect, corner, corner, paintPromoBg)
        paintPromoBorder.color = palette.darkColor
        canvas.drawRoundRect(panelRect, corner, corner, paintPromoBorder)

        promoOrder.forEachIndexed { index, type ->
            val itemTop  = panelRect.top + itemHeight * index
            val itemRect = RectF(panelRect.left, itemTop, panelRect.right, itemTop + itemHeight)
            promoRects[type] = itemRect
            if (index > 0) {
                canvas.drawLine(itemRect.left+8f, itemRect.top, itemRect.right-8f, itemRect.top,
                    paintPromoBorder.apply { strokeWidth = 1f })
                paintPromoBorder.strokeWidth = cellSize * 0.04f
            }
            when (pieceStyle) {
                2 -> {
                    val symbol = unicodeSymbolForType(type, pending.color)
                    paintUnicode.textSize = itemHeight * 0.60f
                    paintUnicode.color    = if (pending.color == PieceColor.WHITE)
                        Color.parseColor("#222222") else Color.parseColor("#111111")
                    val cy = itemRect.centerY() - (paintUnicode.descent() + paintUnicode.ascent()) / 2f
                    canvas.drawText(symbol, itemRect.centerX(), cy, paintUnicode)
                    paintUnicode.textSize = cellSize * 0.70f
                }
                else -> {
                    val iconSize = (itemHeight * 0.7f).toInt().coerceAtLeast(1)
                    val bmp = getPromoBitmap(type, pending.color, iconSize, pieceStyle)
                    if (bmp != null) {
                        canvas.drawBitmap(bmp,
                            itemRect.centerX() - bmp.width / 2f,
                            itemRect.centerY() - bmp.height / 2f, paintPiece)
                    }
                }
            }
        }
    }

    // ── TOQUES ────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        pendingPromotion?.let {
            val pt = PointF(event.x, event.y)
            for ((type, rect) in promoRects) {
                if (rect.contains(pt.x, pt.y)) { onPromotionSelected?.invoke(type); return true }
            }
            return true
        }
        if (animating) return true
        val col = (event.x/cellSize).toInt(); val row = (event.y/cellSize).toInt()
        if (col !in 0..7 || row !in 0..7) return true
        onSquareTapped?.invoke(if (isFlipped) 7-row else row, if (isFlipped) 7-col else col)
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(size, size)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bitmapCache.values.forEach { it.recycle() }; bitmapCache.clear()
    }
}
