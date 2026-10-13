package com.marsan.chess.ui.replay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.marsan.chess.R
import com.marsan.chess.data.repository.GameRecordRepository
import com.marsan.chess.engine.*
import com.marsan.chess.ui.BaseActivity
import com.marsan.chess.ui.game.BoardPalette
import com.marsan.chess.ui.game.BoardView
import com.marsan.chess.ui.main.AppPalette
import com.marsan.chess.ui.main.AppPalettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class GameReplayActivity : BaseActivity() {

    companion object {
        private const val EXTRA_RECORD_ID = "record_id"
        private const val EXTRA_PALETTE   = "palette_idx"
        private const val AUTO_DELAY_MS   = 1400L

        fun start(context: Context, recordId: Int, paletteIdx: Int = 0) {
            context.startActivity(Intent(context, GameReplayActivity::class.java).apply {
                putExtra(EXTRA_RECORD_ID, recordId)
                putExtra(EXTRA_PALETTE,   paletteIdx)
            })
        }
    }

    // ── Estado ────────────────────────────────────────────────────────
    private var moves:  List<Move>  = emptyList()
    private var boards: List<Board> = emptyList()
    private var currentStep = 0
    private var isAutoPlaying = false
    private val autoHandler  = Handler(Looper.getMainLooper())
    private val autoRunnable = object : Runnable {
        override fun run() {
            if (currentStep < moves.size) {
                goForward(); autoHandler.postDelayed(this, AUTO_DELAY_MS)
            } else stopAuto()
        }
    }

    private var stockfish: StockfishEngine? = null

    /** Resultado completo de análisis de una jugada, para no re-analizar al navegar. */
    private data class MoveAnalysisResult(
        val quality:     Int,
        val pieceType:   PieceType?,
        val pieceColor:  PieceColor,
        val moveText:    String,   // "e3 → f4"
        val subText:     String,   // "Blancas · Jugada 5 · Escandinava"
        val statsText:   String,   // "Evaluación −0,5   ·   Mejor: e3→d2"
        val explanation: String
    )

    /** Análisis completos por paso (índice = currentStep). */
    private val analysisCache = mutableMapOf<Int, MoveAnalysisResult>()

    /**
     * Evaluaciones de posición por índice de tablero (perspectiva blancas).
     * Clave extra: analizar la jugada N deja guardada la evaluación de su
     * posición final, que es la posición inicial de la jugada N+1 →
     * analizar jugadas consecutivas cuesta UNA sola búsqueda nueva.
     */
    private val evalCache = mutableMapOf<Int, EngineAnalysis>()

    /** La sesión de análisis (ucinewgame) se inicia una sola vez por partida. */
    private var analysisSessionStarted = false

    private lateinit var palette: AppPalette
    private var paletteIdx = 0

    // ── Views ─────────────────────────────────────────────────────────
    private lateinit var boardView:      BoardView
    private lateinit var tvMoveLabel:    TextView
    private lateinit var tvMoveUci:      TextView
    private lateinit var btnBack:        androidx.appcompat.widget.AppCompatButton
    private lateinit var btnForward:     androidx.appcompat.widget.AppCompatButton
    private lateinit var btnAuto:        androidx.appcompat.widget.AppCompatButton
    private lateinit var btnAnalyze:     androidx.appcompat.widget.AppCompatButton
    private lateinit var pieceIconBox:   FrameLayout
    private lateinit var tvMoveBig:      TextView
    private lateinit var tvMoveSub:      TextView
    private lateinit var tvQualityChip:  TextView
    private lateinit var tvStats:        TextView
    private lateinit var tvAnalysis:     TextView
    private lateinit var layoutAnalysis: LinearLayout

    // ── Lifecycle ─────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recordId = intent.getIntExtra(EXTRA_RECORD_ID, -1)
        paletteIdx   = intent.getIntExtra(EXTRA_PALETTE, 0).coerceIn(0, AppPalettes.all.size - 1)
        palette      = AppPalettes.all[paletteIdx]

        buildLayout()
        if (recordId < 0) { finish(); return }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { loadRecord(recordId) }
            if (result == null || result.second.isEmpty()) {
                // Partida sin movimientos grabados - mostrar posición inicial
                moves  = emptyList()
                boards = listOf(Board().apply { setup() })
                tvMoveLabel.text = getString(R.string.replay_no_moves)
            } else {
                moves  = result.first
                boards = result.second
            }
            boardView.playerColor = PieceColor.WHITE
            updateDisplay()
            updateButtons()

            withContext(Dispatchers.IO) {
                stockfish = StockfishEngine(this@GameReplayActivity)
                stockfish?.init()
            }
        }
    }

    // ── Carga en IO ───────────────────────────────────────────────────

    private suspend fun loadRecord(recordId: Int): Pair<List<Move>, List<Board>>? {
        return try {
            val record = GameRecordRepository.getInstance(this).getById(recordId) ?: return null
            val uciList = movesFromJson(record.movesJson)
            android.util.Log.d("GameReplay", "UCI moves to parse: ${uciList.size} → $uciList")

            val initial   = Board().apply { setup() }
            val boardList = mutableListOf<Board>(initial.copy())
            val moveList  = mutableListOf<Move>()
            var current   = initial.copy()

            for (uci in uciList) {
                // Parsear UCI directamente sin validar contra lista legal
                // Esto evita que movimientos por castling/en-passant se salteen
                val move = parseUciDirect(uci, current)
                if (move == null) {
                    android.util.Log.w("GameReplay", "Could not parse UCI: '$uci'")
                    continue
                }
                moveList.add(move)
                current = current.applyMove(move)
                boardList.add(current.copy())
            }

            android.util.Log.d("GameReplay", "Parsed ${moveList.size}/${uciList.size} moves, ${boardList.size} boards")
            Pair(moveList, boardList)
        } catch (e: Exception) {
            android.util.Log.e("GameReplay", "loadRecord failed: ${e.message}", e)
            null
        }
    }

    /**
     * Convierte UCI a Move sin validar contra movimientos legales.
     * Detecta castling y en passant por contexto del tablero.
     */
    private fun parseUciDirect(uci: String, board: Board): Move? {
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
                else -> null
            } else null

            val piece = board[fromRow, fromCol] ?: return null

            // Detectar castling: rey mueve 2 casillas horizontalmente
            val isCastling = piece.type == PieceType.KING && Math.abs(toCol - fromCol) == 2

            // Detectar en passant: peón captura en diagonal a casilla vacía
            val isEnPassant = piece.type == PieceType.PAWN &&
                    fromCol != toCol && board[toRow, toCol] == null

            Move(fromRow, fromCol, toRow, toCol, promotion, isEnPassant, isCastling)
        } catch (e: Exception) {
            android.util.Log.w("GameReplay", "parseUciDirect failed for '$uci': ${e.message}")
            null
        }
    }

    // ── Layout ────────────────────────────────────────────────────────

    private fun buildLayout() {
        val d    = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }

        // ── EDGE-TO-EDGE (Android 15 / targetSdk 35) ─────────────────
        // El sistema ahora dibuja la app DETRÁS de las barras (estado y
        // navegación). Este listener lee el tamaño real de las barras en
        // CADA teléfono (gestos, 3 botones, notch...) y lo aplica como
        // padding, para que ningún control quede tapado ni inaccesible.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val toolbar = Toolbar(this).apply {
            title = getString(R.string.replay_title)
            setTitleTextColor(palette.textPrimary)
            setBackgroundColor(palette.background)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.navigationIcon?.setTint(palette.accent)

        tvMoveLabel = TextView(this).apply {
            textSize = 13f; gravity = Gravity.CENTER
            setTextColor(palette.textSecondary)
            setPadding(0, (8*d).toInt(), 0, (2*d).toInt())
            text = getString(R.string.replay_move_initial)
        }
        root.addView(tvMoveLabel)

        tvMoveUci = TextView(this).apply {
            textSize = 16f; gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            setTextColor(palette.accent)
            setPadding(0, 0, 0, (4*d).toInt())
            text = ""
        }
        root.addView(tvMoveUci)

        boardView = BoardView(this).apply {
            palette = BoardPalette(
                lightColor = this@GameReplayActivity.palette.boardLight,
                darkColor  = this@GameReplayActivity.palette.boardDark
            )
        }
        root.addView(boardView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })

        // Panel análisis: tarjeta con el estilo del tema activo.
        // El color se DERIVA de la paleta (fondo teñido con el acento),
        // así combina en los 21 temas sin depender de palette.surface,
        // que en varios temas es blanco puro.
        layoutAnalysis = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = 12 * d
                setColor(blendColors(palette.background, palette.accent, 0.10f))
                setStroke((1.5f * d).toInt(), palette.accent)
            }
            setPadding((12*d).toInt(), (10*d).toInt(), (12*d).toInt(), (10*d).toInt())
        }
        root.addView(layoutAnalysis, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins((8*d).toInt(), (4*d).toInt(), (8*d).toInt(), (4*d).toInt()) })

        // ── Encabezado Opción A: pieza + movimiento + chip de calidad ──
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        pieceIconBox = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(blendColors(palette.background, palette.accent, 0.18f))
            }
        }
        headerRow.addView(pieceIconBox, LinearLayout.LayoutParams(
            (40*d).toInt(), (40*d).toInt()).apply { rightMargin = (10*d).toInt() })

        val headerTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tvMoveBig = TextView(this).apply {
            textSize = 16f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.textPrimary)
        }
        tvMoveSub = TextView(this).apply {
            textSize = 12f; setTextColor(palette.textSecondary)
        }
        headerTexts.addView(tvMoveBig)
        headerTexts.addView(tvMoveSub)
        headerRow.addView(headerTexts, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f })

        tvQualityChip = TextView(this).apply {
            textSize = 13f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding((10*d).toInt(), (5*d).toInt(), (10*d).toInt(), (5*d).toInt())
        }
        headerRow.addView(tvQualityChip)
        layoutAnalysis.addView(headerRow)

        // Línea de datos: evaluación y mejor jugada
        tvStats = TextView(this).apply {
            textSize = 13f; setTextColor(palette.textSecondary)
            setPadding(0, (8*d).toInt(), 0, (6*d).toInt())
        }
        layoutAnalysis.addView(tvStats)
        tvAnalysis = TextView(this).apply {
            textSize = 13f; setTextColor(palette.textPrimary); setLineSpacing(0f, 1.4f)
        }
        // El comentario va dentro de un ScrollView con altura MÁXIMA:
        // si el texto es corto ocupa lo justo, y si es largo se desplaza
        // con el dedo en vez de empujar los botones fuera de la pantalla.
        val analysisScroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val capped = MeasureSpec.makeMeasureSpec(
                    (170 * d).toInt(), MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, capped)
            }
        }.apply {
            isVerticalScrollBarEnabled = true
            addView(tvAnalysis, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        layoutAnalysis.addView(analysisScroll)

        // Botones fila 1
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding((8*d).toInt(), (8*d).toInt(), (8*d).toInt(), (10*d).toInt())
        }
        btnBack    = makeBtn("◀",           d, square = true)
        btnAuto    = makeBtn(getString(R.string.replay_btn_auto),    d, square = false)
        btnForward = makeBtn("▶",           d, square = true)
        btnAnalyze = makeBtn(getString(R.string.replay_btn_analyze), d, square = false)
        listOf(btnBack, btnAuto, btnForward, btnAnalyze).forEach { row1.addView(it) }
        root.addView(row1)

        btnBack.setOnClickListener    { stopAuto(); goBack() }
        btnForward.setOnClickListener { stopAuto(); goForward() }
        btnAuto.setOnClickListener    { if (isAutoPlaying) stopAuto() else startAuto() }
        btnAnalyze.setOnClickListener { analyzeCurrentPosition() }

        setContentView(root)
    }

    private fun makeBtn(label: String, d: Float, square: Boolean = false) =
        androidx.appcompat.widget.AppCompatButton(this).apply {
            text     = label
            textSize = if (square) 20f else 12f   // flechas más grandes
            setTextColor(palette.buttonText)
            backgroundTintList = null; stateListAnimator = null
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = 10 * d
                setColor(palette.buttonBg)
                setStroke((1.5f * d).toInt(), palette.accent)
            }
            val sizePx = (44 * d).toInt()
            if (square) {
                // Botones cuadrados para ◀ y ▶
                val ph = (8 * d).toInt()
                setPadding(ph, 0, ph, 0)
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins((4*d).toInt(), 0, (4*d).toInt(), 0)
                }
            } else {
                // Botones rectangulares para Auto y Analizar
                setPadding((14*d).toInt(), (8*d).toInt(), (14*d).toInt(), (8*d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, sizePx
                ).apply { setMargins((4*d).toInt(), 0, (4*d).toInt(), 0) }
            }
        }

    // ── Navegación ────────────────────────────────────────────────────

    private fun goForward() {
        if (currentStep >= moves.size) return
        // Animación de deslizamiento: la jugada que entra es moves[currentStep]
        // y el tablero previo a ella es boards[currentStep]
        val move   = moves.getOrNull(currentStep)
        val before = boards.getOrNull(currentStep)
        currentStep++; updateDisplay(); updateButtons()
        if (move != null && before != null) {
            boardView.animBoard = before
            boardView.startMoveAnimation(move, before)
        }
    }

    private fun goBack() {
        if (currentStep <= 0) return
        currentStep--; updateDisplay(); updateButtons()
    }

    private fun startAuto() {
        if (currentStep >= moves.size) return
        isAutoPlaying = true; btnAuto.text = getString(R.string.replay_btn_pause)
        autoHandler.post(autoRunnable)
    }

    private fun stopAuto() {
        isAutoPlaying = false; btnAuto.text = getString(R.string.replay_btn_auto)
        autoHandler.removeCallbacks(autoRunnable)
    }

    private fun updateDisplay() {
        val board = boards.getOrNull(currentStep) ?: return
        boardView.board          = board
        boardView.lastMove       = if (currentStep > 0) moves.getOrNull(currentStep - 1) else null
        boardView.selectedSquare = null
        boardView.legalMoves     = emptyList()
        boardView.checkAttackers = emptyList()
        boardView.kingInCheck    = null

        val total = moves.size
        if (currentStep == 0) {
            tvMoveLabel.text = getString(R.string.replay_move_initial)
            tvMoveUci.text   = ""
        } else {
            val move     = moves[currentStep - 1]
            val moveNum  = ((currentStep - 1) / 2) + 1
            val isWhite  = (currentStep % 2 == 1)
            val colorStr = getString(if (isWhite) R.string.color_white else R.string.color_black)
            tvMoveLabel.text = getString(R.string.replay_move_label,
                currentStep, total, moveNum, colorStr)
            tvMoveUci.text   = moveToUci(move)
        }
        val cached = analysisCache[currentStep]
        if (cached != null) {
            showCachedAnalysis(cached)
        } else {
            // Sin análisis para este paso: ocultamos el panel para no
            // mostrar el comentario de otra jugada por accidente.
            layoutAnalysis.visibility = View.GONE
        }
    }

    private fun updateButtons() {
        btnBack.isEnabled    = currentStep > 0
        btnForward.isEnabled = currentStep < moves.size
        btnAuto.isEnabled    = moves.isNotEmpty() && currentStep < moves.size
        btnAnalyze.isEnabled = boards.size > 1 || moves.isNotEmpty()
    }

    // ── Análisis ──────────────────────────────────────────────────────


    // ── Sistema de detección de aperturas ────────────────────────────
    // Mapa de secuencias UCI → nombre de apertura
    // Clave: primeros N movimientos en notación UCI separados por espacio
    private val OPENING_BOOK = mapOf(
        // Aperturas de peón rey (1.e4)
        "e2e4"                          to R.string.op_kings_pawn,
        "e2e4 e7e5"                     to R.string.op_open_game,
        "e2e4 e7e5 g1f3"               to R.string.op_kings_knight,
        "e2e4 e7e5 g1f3 b8c6"          to R.string.op_italian_spanish,
        "e2e4 e7e5 g1f3 b8c6 f1c4"     to R.string.op_italian,
        "e2e4 e7e5 g1f3 b8c6 f1b5"     to R.string.op_ruy_lopez,
        "e2e4 e7e5 f2f4"               to R.string.op_kings_gambit,
        "e2e4 e7e5 f2f4 e5f4"          to R.string.op_kings_gambit_acc,
        "e2e4 e7e5 f2f4 d8h4"          to R.string.op_kings_gambit_dec,
        "e2e4 e7e5 g1f3 g8f6"          to R.string.op_petrov,
        "e2e4 e7e5 g1f3 f7f5"          to R.string.op_latvian,
        "e2e4 c7c5"                     to R.string.op_sicilian,
        "e2e4 c7c5 g1f3 d7d6"          to R.string.op_sicilian_najdorf,
        "e2e4 c7c5 g1f3 b8c6"          to R.string.op_sicilian_classical,
        "e2e4 c7c5 g1f3 e7e6"          to R.string.op_sicilian_scheveningen,
        "e2e4 e7e6"                     to R.string.op_french,
        "e2e4 e7e6 d2d4 d7d5"          to R.string.op_french_main,
        "e2e4 e7e6 d2d4 d7d5 b1c3"     to R.string.op_french_winawer,
        "e2e4 c7c6"                     to R.string.op_caro_kann,
        "e2e4 c7c6 d2d4 d7d5"          to R.string.op_caro_kann_main,
        "e2e4 d7d5"                     to R.string.op_scandinavian,
        "e2e4 d7d5 e4d5"               to R.string.op_scandinavian_acc,
        "e2e4 d7d6"                     to R.string.op_pirc,
        "e2e4 g7g6"                     to R.string.op_modern,
        "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6" to R.string.op_pirc_classical,
        // Aperturas de peón dama (1.d4)
        "d2d4"                          to R.string.op_queens_pawn,
        "d2d4 d7d5"                     to R.string.op_queens_game,
        "d2d4 d7d5 c2c4"               to R.string.op_queens_gambit,
        "d2d4 d7d5 c2c4 d5c4"          to R.string.op_qga,
        "d2d4 d7d5 c2c4 e7e6"          to R.string.op_qgd,
        "d2d4 d7d5 c2c4 c7c6"          to R.string.op_slav,
        "d2d4 g8f6"                     to R.string.op_indian,
        "d2d4 g8f6 c2c4 g7g6"          to R.string.op_kings_indian,
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7" to R.string.op_grunfeld,
        "d2d4 g8f6 c2c4 e7e6"          to R.string.op_nimzo_queens,
        "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4" to R.string.op_nimzo,
        "d2d4 g8f6 c2c4 e7e6 g1f3 b7b6"  to R.string.op_queens_indian,
        "d2d4 d7d6 c2c4 g8f6 b1c3 g7g6" to R.string.op_kings_indian_classical,
        // Aperturas de flanco / Inglesa
        "c2c4"                          to R.string.op_english,
        "c2c4 e7e5"                     to R.string.op_english_rev,
        "c2c4 c7c5"                     to R.string.op_english_sym,
        "g1f3"                          to R.string.op_reti,
        "g1f3 d7d5 c2c4"               to R.string.op_reti_main,
        "g2g3"                          to R.string.op_benko,
        "b2b3"                          to R.string.op_larsen,
        "f2f4"                          to R.string.op_bird
    )

    /**
     * Detecta si la secuencia de movimientos actuales corresponde a una
     * apertura conocida y devuelve su nombre, o null si no se reconoce.
     */
    private fun detectOpening(movesSoFar: List<Move>): String? {
        if (movesSoFar.isEmpty()) return null
        val uciSeq = movesSoFar.joinToString(" ") { moveToUci(it) }
        // Buscar la entrada más larga que sea prefijo de la secuencia actual
        var bestMatch: Int? = null
        var bestLen = 0
        for ((key, nameRes) in OPENING_BOOK) {
            if (uciSeq.startsWith(key) && key.length > bestLen) {
                bestMatch = nameRes
                bestLen   = key.length
            }
        }
        // El nombre sale de strings.xml → se traduce solo según idioma
        return bestMatch?.let { getString(it) }
    }

    /**
     * Determina si el movimiento es parte de teoría de apertura reconocida.
     * Un movimiento es "teórico" solo si la secuencia COMPLETA hasta él
     * (inclusive) coincide con una línea del libro o es prefijo de una.
     *
     * FIX: la versión anterior tenía dos errores:
     *   1. take(moveIndex) excluía la jugada analizada (off-by-one).
     *   2. La condición seq.startsWith(primerMovimiento) marcaba como
     *      "teórica" CUALQUIER jugada de una partida que empezara con
     *      e4/d4/c4, incluso disparates como g2g4 en la jugada 5.
     */
    private fun isTheoreticalMove(moveIndex: Int): Boolean {
        if (moveIndex < 0) return false
        val seq = moves.take(moveIndex + 1).joinToString(" ") { moveToUci(it) }
        return OPENING_BOOK.keys.any { key -> key == seq || key.startsWith("$seq ") }
    }

    private fun analyzeCurrentPosition() {
        val sf = stockfish
        if (sf == null || !sf.isAvailable()) {
            showPanelMessage(getString(R.string.replay_no_stockfish))
            return
        }

        // Si estamos en la posición inicial no hay jugada que analizar
        if (currentStep == 0) {
            showPanelMessage(getString(R.string.replay_initial_pos))
            return
        }

        // La jugada que se analiza es moves[currentStep-1]
        // El tablero ANTES de esa jugada es boards[currentStep-1]
        // El color que la jugó es boards[currentStep-1].currentTurn
        val boardBefore = boards.getOrNull(currentStep - 1) ?: return
        val boardAfter  = boards.getOrNull(currentStep)     ?: return
        val movedColor  = boardBefore.currentTurn   // quien ACABA DE mover
        val playedMove  = moves.getOrNull(currentStep - 1)

        showPanelMessage(getString(R.string.replay_analyzing))
        btnAnalyze.isEnabled = false

        // Un análisis por paso: si ya existe en caché, solo lo mostramos.
        analysisCache[currentStep]?.let {
            showCachedAnalysis(it); btnAnalyze.isEnabled = true; return
        }

        val stepAtRequest = currentStep   // por si el usuario navega mientras analiza

        lifecycleScope.launch {
            try {
                val moveNum = ((stepAtRequest - 1) / 2) + 1

                val (before, after) = withContext(Dispatchers.IO) {
                    if (!analysisSessionStarted) {
                        sf.startAnalysisSession()
                        analysisSessionStarted = true
                    }

                    // ── PASADA 1 (rápida, profundidad 10) ────────────
                    // analyzePosition devuelve puntuación + mejor jugada
                    // en UNA búsqueda, normalizada a perspectiva blancas.
                    // El caché evita repetir posiciones ya evaluadas.
                    val fenBefore = FenConverter.toFen(boardBefore, moveNum)
                    val fenAfter  = FenConverter.toFen(boardAfter,  moveNum)

                    var b = evalCache[stepAtRequest - 1]
                        ?: sf.analyzePosition(fenBefore, depth = 10)
                            .also { evalCache[stepAtRequest - 1] = it }
                    var a = evalCache[stepAtRequest]
                        ?: sf.analyzePosition(fenAfter, depth = 10)
                            .also { evalCache[stepAtRequest] = it }

                    // ── PASADA 2 (profunda, solo jugadas críticas) ───
                    // Si la jugada parece error grave o hay mate en el
                    // horizonte, re-analizamos con profundidad 15 para
                    // no acusar un "blunder" por ruido de baja profundidad.
                    val moverSign  = if (movedColor == PieceColor.WHITE) 1 else -1
                    val quickLoss  = (b.scoreCp - a.scoreCp) * moverSign
                    val isCritical = quickLoss > 90 || b.mateIn != null || a.mateIn != null

                    if (isCritical) {
                        b = sf.analyzePosition(fenBefore, depth = 15)
                            .also { evalCache[stepAtRequest - 1] = it }
                        a = sf.analyzePosition(fenAfter, depth = 15)
                            .also { evalCache[stepAtRequest] = it }
                    }
                    Pair(b, a)
                }

                // ── Pérdida en centipeones desde la perspectiva del que movió ──
                // scoreCp viene normalizado a blancas; lo pasamos al punto
                // de vista del jugador que hizo la jugada.
                val moverSign    = if (movedColor == PieceColor.WHITE) 1 else -1
                val beforeMover  = before.scoreCp * moverSign
                val afterMover   = after.scoreCp  * moverSign
                val cpLoss       = (beforeMover - afterMover).coerceAtLeast(0)

                val isTheoretical = isTheoreticalMove(stepAtRequest - 1)
                val quality       = cpLossToQuality(cpLoss, moveNum, isTheoretical)

                val bestMove = before.bestMove?.let { parseUciDirect(it, boardBefore) }
                val isBestMove = bestMove != null && playedMove != null &&
                    moveToUci(bestMove) == moveToUci(playedMove)

                val openingName = detectOpening(moves.take(stepAtRequest))
                val explanation = buildAnalysisText(
                    quality, cpLoss, movedColor, playedMove, bestMove, isBestMove,
                    boardBefore, before, after, openingName, isTheoretical, moveNum
                )

                val colorName = getString(
                    if (movedColor == PieceColor.WHITE) R.string.color_white else R.string.color_black)
                val moveText  = playedMove?.let {
                    "${squareName(it.fromRow, it.fromCol)} → ${squareName(it.toRow, it.toCol)}"
                } ?: "—"
                val subText = buildString {
                    append("$colorName · ${getString(R.string.an_move_n, moveNum)}")
                    if (openingName != null && moveNum <= 10) append(" · $openingName")
                }
                val statsText = buildString {
                    append(getString(R.string.an_eval_label, formatEval(after.scoreCp)))
                    after.mateIn?.let { append(" " + getString(R.string.an_mate_in_short, Math.abs(it))) }
                    if (isBestMove) {
                        append("   ·   " + getString(R.string.an_optimal_check))
                    } else if (bestMove != null) {
                        append("   ·   " + getString(R.string.an_best_label,
                            "${squareName(bestMove.fromRow, bestMove.fromCol)}→" +
                            squareName(bestMove.toRow, bestMove.toCol)))
                    }
                }

                val result = MoveAnalysisResult(
                    quality     = quality,
                    pieceType   = playedMove?.let { boardBefore[it.fromRow, it.fromCol]?.type },
                    pieceColor  = movedColor,
                    moveText    = moveText,
                    subText     = subText,
                    statsText   = statsText,
                    explanation = explanation
                )
                analysisCache[stepAtRequest] = result

                // Solo actualizamos la UI si el usuario sigue en el mismo paso
                if (currentStep == stepAtRequest) {
                    renderAnalysis(result)
                }
            } catch (e: Exception) {
                showPanelMessage(getString(R.string.replay_analysis_error, e.message ?: ""))
            } finally {
                btnAnalyze.isEnabled = true
            }
        }
    }

    private fun buildAnalysisText(
        quality:       Int,
        cpLoss:        Int,
        movedColor:    PieceColor,
        playedMove:    Move?,
        bestMove:      Move?,
        isBestMove:    Boolean,
        boardBefore:   Board,
        before:        EngineAnalysis,
        after:         EngineAnalysis,
        openingName:   String?,
        isTheoretical: Boolean,
        moveNum:       Int
    ): String {
        val colorName = getString(if (movedColor == PieceColor.WHITE) R.string.color_white else R.string.color_black)
        val rivalName = getString(if (movedColor == PieceColor.WHITE) R.string.color_black else R.string.color_white)
        val pieceType = playedMove?.let { boardBefore[it.fromRow, it.fromCol]?.type }
        val pieceName = getString(when (pieceType) {
            PieceType.KING   -> R.string.piece_king
            PieceType.QUEEN  -> R.string.piece_queen
            PieceType.ROOK   -> R.string.piece_rook
            PieceType.BISHOP -> R.string.piece_bishop
            PieceType.KNIGHT -> R.string.piece_knight
            PieceType.PAWN   -> R.string.piece_pawn
            null             -> R.string.piece_generic
        })
        val isCapture    = playedMove?.let { boardBefore[it.toRow, it.toCol] } != null
        val isPromotion  = playedMove?.promotion != null
        val isCastling   = playedMove?.isCastling == true
        val isOpening    = moveNum <= 10
        val isEndgame    = boardBefore.squares.sumOf { row ->
            row.count { it != null && it.type != PieceType.PAWN && it.type != PieceType.KING }
        } <= 6

        // ── Señales tácticas concretas (el PORQUÉ del comentario) ─────
        val moverSign   = if (movedColor == PieceColor.WHITE) 1 else -1
        val beforeMover = before.scoreCp * moverSign   // eval antes, ojos del que movió
        val afterMover  = after.scoreCp  * moverSign   // eval después

        // ¿Tenía mate forzado a favor y lo dejó escapar?
        val hadMate    = before.mateIn?.let { it * moverSign > 0 } == true
        val keptMate   = after.mateIn?.let  { it * moverSign > 0 } == true
        val missedMate = hadMate && !keptMate
        // ¿Su jugada le regala un mate forzado al rival?
        val allowsMate = after.mateIn?.let { it * moverSign < 0 } == true
        // ¿Dejó la pieza colgada? (la mejor respuesta del rival es capturar
        // exactamente en la casilla a la que acaba de mover, y cuesta caro)
        val destSquare = playedMove?.let { squareName(it.toRow, it.toCol) }
        val hangsPiece = cpLoss >= 140 && playedMove != null &&
            after.bestMove?.let { it.length >= 4 && it.substring(2, 4) == destSquare } == true
        // ¿La ventaja cambió de manos con esta jugada?
        val advantageFlipped = beforeMover > 100 && afterMover < -60
        // ¿Cedió una ventaja clara sin llegar a quedar peor?
        val advantageDrained = beforeMover > 130 && afterMover in -60..50 && !advantageFlipped

        // ── Explicación: primero el PORQUÉ táctico, luego el contexto ──
        // Las señales concretas (mate, pieza colgada, cambio de ventaja)
        // tienen prioridad sobre los comentarios genéricos por fase.
        val bestStr = bestMove?.let { moveToUci(it) }

        val explanation = when {
            allowsMate -> {
                val n = after.mateIn?.let { Math.abs(it) } ?: 0
                getString(R.string.an_allows_mate, rivalName, n)
            }

            missedMate -> {
                val n = before.mateIn?.let { Math.abs(it) } ?: 0
                getString(R.string.an_missed_mate, colorName, n,
                    bestStr ?: getString(R.string.an_another_move))
            }

            hangsPiece ->
                getString(R.string.an_hangs_piece, pieceName, destSquare ?: "", rivalName)

            isBestMove && keptMate -> {
                val n = after.mateIn?.let { Math.abs(it) } ?: 0
                getString(R.string.an_optimal_mate, colorName, n)
            }

            isBestMove ->
                resources.getStringArray(R.array.an_optimal_pool).random()
                    .format(colorName)

            advantageFlipped ->
                getString(R.string.an_advantage_flipped,
                    colorName, formatEval(beforeMover), rivalName, formatEval(afterMover)) + " " +
                (if (isCapture) getString(R.string.an_flip_capture)
                 else getString(R.string.an_flip_reply, after.bestMove ?: ""))

            advantageDrained ->
                getString(R.string.an_advantage_drained,
                    colorName, formatEval(beforeMover),
                    bestStr ?: getString(R.string.an_another_line))

            isTheoretical && isOpening && openingName != null ->
                resources.getStringArray(R.array.an_theory_pool).random()
                    .format(openingName, colorName)

            isCastling && quality >= 65 ->
                resources.getStringArray(R.array.an_castle_pool).random()

            isPromotion ->
                if (quality >= 65) getString(R.string.an_promo_good)
                else getString(R.string.an_promo_bad)

            isCapture && quality >= 78 ->
                resources.getStringArray(R.array.an_capture_good_pool).random()
                    .format(pieceName, colorName)

            isCapture && quality < 38 ->
                getString(R.string.an_capture_bad,
                    after.bestMove ?: getString(R.string.an_rival_reply), rivalName)

            isOpening && quality >= 65 ->
                resources.getStringArray(R.array.an_opening_good_pool).random()
                    .format(pieceName, colorName)

            isOpening && quality < 38 ->
                getString(R.string.an_opening_bad, formatCpLoss(cpLoss),
                    bestStr ?: getString(R.string.an_develop_minor))

            isOpening ->
                getString(R.string.an_opening_mid,
                    bestStr ?: getString(R.string.an_another_piece))

            isEndgame && quality >= 65 ->
                resources.getStringArray(R.array.an_endgame_good_pool).random()
                    .format(pieceName)

            isEndgame && quality < 38 ->
                getString(R.string.an_endgame_bad, formatCpLoss(cpLoss),
                    bestStr ?: getString(R.string.an_activate_king))

            quality >= 78 ->
                resources.getStringArray(R.array.an_good_pool).random()
                    .format(colorName, formatEval(afterMover), pieceName)

            quality >= 52 ->
                getString(R.string.an_acceptable,
                    bestStr ?: getString(R.string.an_another_line), formatCpLoss(cpLoss))

            quality >= 38 ->
                getString(R.string.an_inaccuracy, pieceName, formatCpLoss(cpLoss),
                    bestStr ?: getString(R.string.an_another_move))

            quality >= 22 ->
                getString(R.string.an_bad, colorName, formatCpLoss(cpLoss), rivalName)

            else ->
                getString(R.string.an_blunder, formatCpLoss(cpLoss))
        }

        return explanation
    }

    // ── Render del panel (Opción A) ──────────────────────────────────

    /** Muestra un mensaje simple en el panel (analizando, error, etc.). */
    private fun showPanelMessage(msg: String) {
        layoutAnalysis.visibility  = View.VISIBLE
        pieceIconBox.removeAllViews()
        tvMoveBig.text             = ""
        tvMoveSub.text             = ""
        tvQualityChip.text         = ""
        tvQualityChip.background   = null
        tvStats.text               = ""
        tvAnalysis.text            = msg
    }

    private fun showCachedAnalysis(result: MoveAnalysisResult) = renderAnalysis(result)

    private fun renderAnalysis(r: MoveAnalysisResult) {
        layoutAnalysis.visibility = View.VISIBLE
        val d = resources.displayMetrics.density

        // Pieza: misma imagen/estilo que el tablero del juego
        pieceIconBox.removeAllViews()
        pieceIconBox.addView(makePieceView(r.pieceType, r.pieceColor),
            FrameLayout.LayoutParams((30*d).toInt(), (30*d).toInt(), Gravity.CENTER))

        tvMoveBig.text = r.moveText
        tvMoveSub.text = r.subText

        tvQualityChip.text = "${r.quality}% ${qualityLabelFor(r.quality)}"
        tvQualityChip.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setColor(qualityColorFor(r.quality))
        }

        tvStats.text    = r.statsText
        tvAnalysis.text = r.explanation
    }

    /**
     * Crea la vista de la pieza usando EXACTAMENTE el mismo recurso que
     * dibuja el tablero: drawable piece_/piece3_ según el estilo activo
     * en preferencias, o el símbolo Unicode si el estilo es 2 (o si el
     * drawable no existe).
     */
    private fun makePieceView(type: PieceType?, color: PieceColor): View {
        val stylePref = com.marsan.chess.util.PreferencesManager
            .getInstance(this).pieceStyle
        if (type != null && stylePref != 2) {
            val prefix = if (stylePref == 3) "piece3" else "piece"
            val c      = if (color == PieceColor.WHITE) "w" else "b"
            val resId  = resources.getIdentifier(
                "${prefix}_${c}_${type.name.lowercase()}", "drawable", packageName)
            if (resId != 0) {
                return ImageView(this).apply {
                    setImageResource(resId)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            }
        }
        // Estilo Unicode (o drawable inexistente): mismo símbolo del tablero
        return TextView(this).apply {
            text = when (color) {
                PieceColor.WHITE -> when (type) {
                    PieceType.KING -> "♔"; PieceType.QUEEN -> "♕"; PieceType.ROOK -> "♖"
                    PieceType.BISHOP -> "♗"; PieceType.KNIGHT -> "♘"; else -> "♙"
                }
                PieceColor.BLACK -> when (type) {
                    PieceType.KING -> "♚"; PieceType.QUEEN -> "♛"; PieceType.ROOK -> "♜"
                    PieceType.BISHOP -> "♝"; PieceType.KNIGHT -> "♞"; else -> "♟"
                }
            }
            textSize = 20f
            gravity  = Gravity.CENTER
            setTextColor(palette.textPrimary)
        }
    }

    private fun qualityLabelFor(quality: Int) = getString(when {
        quality >= 90 -> R.string.quality_excellent
        quality >= 78 -> R.string.quality_very_good
        quality >= 65 -> R.string.quality_good
        quality >= 52 -> R.string.quality_acceptable
        quality >= 38 -> R.string.quality_inaccuracy
        quality >= 22 -> R.string.quality_bad
        else          -> R.string.quality_blunder
    })

    /**
     * Color del sello de calidad. Colores fijos semánticos (verde/azul/
     * ámbar/rojo) con texto blanco: legibles sobre cualquiera de los
     * 21 temas de la app.
     */
    private fun qualityColorFor(quality: Int) = when {
        quality >= 78 -> Color.parseColor("#2E7D32")   // verde
        quality >= 52 -> Color.parseColor("#1565C0")   // azul
        quality >= 38 -> Color.parseColor("#EF6C00")   // ámbar
        quality >= 22 -> Color.parseColor("#C62828")   // rojo
        else          -> Color.parseColor("#7B1414")   // rojo blunder
    }

    /** Nombre algebraico de una casilla: (fila,columna) internas → "e4". */
    private fun squareName(row: Int, col: Int) = "${'a' + col}${8 - row}"

    /**
     * Formatea centipeones (perspectiva blancas o del jugador, según se
     * le pase) al estilo clásico de los motores: "+1.3", "-0.4", "0.0".
     */
    private fun formatEval(cp: Int): String {
        if (cp >=  25000) return getString(R.string.eval_mate_for)
        if (cp <= -25000) return getString(R.string.eval_mate_against)
        return String.format("%+.1f", cp / 100.0)
    }

    /** Pérdida en formato legible: "0.8 puntos de peón". */
    private fun formatCpLoss(cpLoss: Int): String =
        getString(R.string.an_pawn_points, cpLoss / 100.0)

    /**
     * Convierte la PÉRDIDA en centipeones (cpLoss, siempre >= 0) a una
     * calidad 0-100, con tolerancia según la fase de la partida.
     *
     * Escala de referencia (estándar en análisis de ajedrez):
     *   0-20 cp   → jugada excelente (diferencia despreciable)
     *   20-50 cp  → buena
     *   50-100 cp → imprecisión (medio peón)
     *   100-300 cp→ error (un peón a una pieza menor)
     *   300+ cp   → blunder (material grave o posición perdida)
     *
     * En apertura la tolerancia es algo mayor (líneas teóricas alternativas
     * difieren en 20-40cp sin ser errores), pero NUNCA tanto como para
     * perdonar un g4 en la jugada 1: eso pierde ~130cp y debe notarse.
     */
    private fun cpLossToQuality(cpLoss: Int, moveNum: Int, isTheoretical: Boolean): Int {
        // Jugada del libro de aperturas: es teoría establecida por definición.
        if (isTheoretical && moveNum <= 12) return 90

        // Factor de tolerancia por fase: multiplica los umbrales base.
        val tolerance = when {
            moveNum <= 6  -> 1.5   // apertura temprana
            moveNum <= 12 -> 1.3   // apertura
            moveNum <= 25 -> 1.0   // medio juego
            else          -> 0.8   // final: cada centipeón cuenta
        }
        val loss = cpLoss / tolerance

        return when {
            loss <= 10  -> 95
            loss <= 25  -> 88
            loss <= 50  -> 76
            loss <= 80  -> 62
            loss <= 120 -> 48
            loss <= 200 -> 32
            loss <= 350 -> 18
            else        -> 6
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Mezcla dos colores de la paleta. ratio 0.0 = solo base,
     * 1.0 = solo tint. Se usa para derivar tonos intermedios que
     * combinen con cualquier tema sin definirlos uno a uno.
     */
    private fun blendColors(base: Int, tint: Int, ratio: Float): Int =
        androidx.core.graphics.ColorUtils.blendARGB(base, tint, ratio)

    private fun moveToUci(move: Move): String {
        val from  = "${'a' + move.fromCol}${8 - move.fromRow}"
        val to    = "${'a' + move.toCol}${8 - move.toRow}"
        val promo = move.promotion?.name?.lowercase()?.firstOrNull()?.toString() ?: ""
        return "$from$to$promo"
    }

    private fun movesFromJson(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAuto()
        lifecycleScope.launch(Dispatchers.IO) { stockfish?.quit() }
    }
}
