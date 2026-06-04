package com.marsan.chess.ui.game

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.marsan.chess.engine.*

data class BoardPalette(val lightColor: Int, val darkColor: Int)

object BoardPalettes {
    val A = BoardPalette(Color.parseColor("#DCE8F0"), Color.parseColor("#6A96B0"))
    val B = BoardPalette(Color.parseColor("#D4EAD8"), Color.parseColor("#6FAE7A"))
    val C = BoardPalette(Color.parseColor("#E4DFF5"), Color.parseColor("#8E7DC8"))
    val D = BoardPalette(Color.parseColor("#F5E4D4"), Color.parseColor("#C48A60"))
    val E = BoardPalette(Color.parseColor("#F5DCE0"), Color.parseColor("#C27A86"))
    val G = BoardPalette(Color.parseColor("#D4EEF0"), Color.parseColor("#5A9FA8"))
    val H = BoardPalette(Color.parseColor("#F5EDD0"), Color.parseColor("#C4A84A"))
    val all = listOf(A, B, C, D, E, G, H)
    fun random() = all.random()
}

class BoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var palette: BoardPalette = BoardPalettes.A
        set(value) { field = value; invalidate() }

    // Colores de selección y último movimiento derivados de la paleta
    private val colorCheck       = Color.parseColor("#CC2020")
    private val colorHintOnDark  = Color.argb(200, 255, 255, 255)
    private val colorHintOnLight = Color.argb(200, 30, 30, 30)
    private val colorRingOnDark  = Color.argb(220, 255, 255, 255)
    private val colorRingOnLight = Color.argb(220, 30, 30, 30)

    private val paintCell   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintPiece  = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paintDot    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintRing   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintLabel  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG)

    // Animación
    private var animating = false
    private var animPiece: Piece? = null
    private var animFromX = 0f; private var animFromY = 0f
    private var animToX   = 0f; private var animToY   = 0f
    private var animProgress = 0f; private var animStartTime = 0L
    private val animDuration = 260L
    private var animSkipRow = -1; private var animSkipCol = -1
    var animBoard: Board? = null

    // Bitmap cache
    private val bitmapCache = mutableMapOf<String, Bitmap>()
    private fun getPieceBitmap(piece: Piece): Bitmap? {
        val key = "${piece.color}_${piece.type}_${cellSize.toInt()}"
        bitmapCache[key]?.let { return it }
        val name  = "${if (piece.color == PieceColor.WHITE) "piece_w" else "piece_b"}_${piece.type.name.lowercase()}"
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (resId == 0) return null
        val orig   = BitmapFactory.decodeResource(context.resources, resId) ?: return null
        val size   = (cellSize * 0.86f).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(orig, size, size, true)
        bitmapCache[key] = scaled
        return scaled
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
        paintBorder.style      = Paint.Style.STROKE
        paintBorder.color      = Color.parseColor("#CCCCCC")
        paintBorder.strokeWidth= 1f
        paintDot.style         = Paint.Style.FILL
        paintRing.style        = Paint.Style.STROKE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cellSize != w / 8f) { bitmapCache.values.forEach { it.recycle() }; bitmapCache.clear() }
        boardSize = minOf(w.toFloat(), h.toFloat())
        cellSize  = boardSize / 8f
        labelPad  = cellSize * 0.22f
        paintLabel.textSize   = cellSize * 0.22f
        paintRing.strokeWidth = cellSize * 0.07f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.lightColor)
        val b = animBoard ?: board ?: return
        drawCells(canvas, b)
        drawLabels(canvas)
        if (animating) {
            drawPieces(canvas, b, animSkipRow, animSkipCol)
            drawAnimPiece(canvas)
            val elapsed = System.currentTimeMillis() - animStartTime
            animProgress = (elapsed.toFloat() / animDuration).coerceIn(0f, 1f)
            if (animProgress >= 1f) { animating=false; animSkipRow=-1; animSkipCol=-1; animBoard=null; animPiece=null }
            postInvalidateOnAnimation()
        } else { drawPieces(canvas, b, -1, -1) }
        canvas.drawRect(RectF(0f,0f,boardSize,boardSize), paintBorder)
    }

    private fun drawCells(canvas: Canvas, board: Board) {
        val hints    = legalMoves.map { it.toRow to it.toCol }.toSet()
        val lastFrom = lastMove?.let { it.fromRow to it.fromCol }
        val lastTo   = lastMove?.let { it.toRow   to it.toCol }
        val redSet   = mutableSetOf<Pair<Int,Int>>().also {
            kingInCheck?.let { k -> it.add(k) }; checkAttackers.forEach { a -> it.add(a) }
        }

        for (row in 0..7) for (col in 0..7) {
            val dr = if (isFlipped) 7-row else row
            val dc = if (isFlipped) 7-col else col
            val x  = col * cellSize; val y = row * cellSize
            val isLight    = (dr+dc)%2==0
            val isSelected = selectedSquare?.let { it.first==dr && it.second==dc } == true
            val isRed      = redSet.contains(dr to dc)
            val isLast     = lastFrom?.let{it.first==dr&&it.second==dc}==true ||
                             lastTo?.let{it.first==dr&&it.second==dc}==true

            // Colores de selección y último movimiento usando la paleta actual
            paintCell.color = when {
                isRed      -> colorCheck
                isSelected -> blendColor(palette.darkColor, Color.WHITE, 0.3f)
                isLast     -> if (isLight) blendColor(palette.lightColor, palette.darkColor, 0.25f)
                              else blendColor(palette.darkColor, palette.lightColor, 0.2f)
                isLight    -> palette.lightColor
                else       -> palette.darkColor
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
    }

    // Mezcla dos colores con factor t (0=color1, 1=color2)
    private fun blendColor(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1)   + (Color.red(c2)   - Color.red(c1))   * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1)  + (Color.blue(c2)  - Color.blue(c1))  * t).toInt()
        return Color.rgb(r, g, b)
    }

    private fun drawLabels(canvas: Canvas) {
        val files = listOf("a","b","c","d","e","f","g","h")
        val ranks = listOf("8","7","6","5","4","3","2","1")
        for (i in 0..7) {
            val isDark = i%2!=0
            paintLabel.color    = if (isDark) Color.parseColor("#DDDDDD") else Color.parseColor("#777777")
            paintLabel.textAlign = Paint.Align.RIGHT
            canvas.drawText(if (isFlipped) files[7-i] else files[i],
                i*cellSize+cellSize-labelPad*0.3f, 7*cellSize+cellSize-labelPad*0.25f, paintLabel)
            paintLabel.textAlign = Paint.Align.LEFT
            canvas.drawText(if (isFlipped) ranks[7-i] else ranks[i],
                labelPad*0.25f, i*cellSize+labelPad*1.1f, paintLabel)
        }
        paintLabel.textAlign = Paint.Align.CENTER
    }

    private fun drawPieces(canvas: Canvas, board: Board, skipRow: Int, skipCol: Int) {
        for (row in 0..7) for (col in 0..7) {
            val dr = if (isFlipped) 7-row else row
            val dc = if (isFlipped) 7-col else col
            if (dr==skipRow && dc==skipCol) continue
            val piece = board[dr,dc] ?: continue
            val bmp   = getPieceBitmap(piece) ?: continue
            val offset = (cellSize - bmp.width) / 2f
            canvas.drawBitmap(bmp, col*cellSize+offset, row*cellSize+offset, paintPiece)
        }
    }

    private fun drawAnimPiece(canvas: Canvas) {
        val bmp = animPiece?.let { getPieceBitmap(it) } ?: return
        val t   = easeInOut(animProgress)
        canvas.drawBitmap(bmp, animFromX+(animToX-animFromX)*t, animFromY+(animToY-animFromY)*t, paintPiece)
    }

    private fun easeInOut(t: Float) = if (t < 0.5f) 2f*t*t else -1f+(4f-2f*t)*t

    fun startMoveAnimation(move: Move, boardBefore: Board) {
        val piece  = boardBefore[move.fromRow, move.fromCol] ?: return
        val bmp    = getPieceBitmap(piece)
        val offset = if (bmp != null) (cellSize - bmp.width) / 2f else 0f
        val fCol = if (isFlipped) 7-move.fromCol else move.fromCol
        val fRow = if (isFlipped) 7-move.fromRow else move.fromRow
        val tCol = if (isFlipped) 7-move.toCol   else move.toCol
        val tRow = if (isFlipped) 7-move.toRow   else move.toRow
        animPiece   = piece; animSkipRow = move.toRow; animSkipCol = move.toCol
        animFromX   = fCol*cellSize+offset; animFromY = fRow*cellSize+offset
        animToX     = tCol*cellSize+offset; animToY   = tRow*cellSize+offset
        animProgress= 0f; animStartTime = System.currentTimeMillis(); animating = true
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP || animating) return true
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
