package com.marsan.chess.ui.replay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.marsan.chess.R
import com.marsan.chess.data.db.GameRecordEntity
import com.marsan.chess.data.repository.GameRecordRepository
import com.marsan.chess.ui.BaseActivity
import com.marsan.chess.ui.main.AppPalette
import com.marsan.chess.ui.main.AppPalettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class GameRecordingsActivity : BaseActivity() {

    companion object {
        private const val EXTRA_PALETTE = "palette_idx"
        fun start(context: Context, paletteIdx: Int = 0) {
            context.startActivity(Intent(context, GameRecordingsActivity::class.java).apply {
                putExtra(EXTRA_PALETTE, paletteIdx)
            })
        }
    }

    private lateinit var listContainer:  LinearLayout
    private lateinit var tvEmpty:        TextView
    private lateinit var tvSelectionInfo: TextView
    private lateinit var btnDeleteSelected: androidx.appcompat.widget.AppCompatButton
    private lateinit var btnDeleteAll:   androidx.appcompat.widget.AppCompatButton
    private lateinit var repo:           GameRecordRepository
    private lateinit var palette:        AppPalette
    private var paletteIdx = 0
    private var d = 1f

    // Selección múltiple
    private val selectedIds = mutableSetOf<Int>()
    private var allRecords  = listOf<GameRecordEntity>()
    private var selectionMode = false

    // ── Lifecycle ─────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paletteIdx = intent.getIntExtra(EXTRA_PALETTE, 0).coerceIn(0, AppPalettes.all.size - 1)
        palette    = AppPalettes.all[paletteIdx]
        d          = resources.displayMetrics.density
        repo       = GameRecordRepository.getInstance(this)

        buildLayout()

        lifecycleScope.launch {
            repo.observeAll().collectLatest { records ->
                withContext(Dispatchers.Main) {
                    if (!isDestroyed && !isFinishing) {
                        allRecords = records
                        renderList(records)
                    }
                }
            }
        }
    }

    // ── Layout ────────────────────────────────────────────────────────

    private fun buildLayout() {
        val root = RelativeLayout(this).apply { setBackgroundColor(palette.background) }

        // Toolbar
        val toolbarId = View.generateViewId()
        val toolbar = Toolbar(this).apply {
            id = toolbarId
            title = getString(R.string.recordings_title)
            setTitleTextColor(palette.textPrimary)
            setBackgroundColor(palette.background)
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) }
        }
        root.addView(toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            if (selectionMode) exitSelectionMode() else finish()
        }
        toolbar.navigationIcon?.setTint(palette.accent)

        // Panel inferior con botones
        val bottomPanelId = View.generateViewId()
        val bottomPanel = LinearLayout(this).apply {
            id = bottomPanelId
            orientation = LinearLayout.VERTICAL
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
            setPadding((16*d).toInt(), (8*d).toInt(), (16*d).toInt(), (16*d).toInt())
            setBackgroundColor(palette.background)
        }

        // Info de selección
        tvSelectionInfo = TextView(this).apply {
            textSize = 13f
            setTextColor(palette.accent)
            gravity  = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 0, 0, (6*d).toInt())
        }
        bottomPanel.addView(tvSelectionInfo)

        // Botón eliminar seleccionadas
        btnDeleteSelected = makeAccentBtn(
            text      = getString(R.string.recordings_delete_selected),
            bgColor   = Color.parseColor("#FFF5F5"),
            txtColor  = Color.parseColor("#C04040"),
            border    = Color.parseColor("#C04040")
        ).apply { visibility = View.GONE }
        btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }
        bottomPanel.addView(btnDeleteSelected, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (6*d).toInt() })

        // Botón eliminar todas
        btnDeleteAll = makeAccentBtn(
            text     = getString(R.string.recordings_delete_all),
            bgColor  = Color.parseColor("#FFF5F5"),
            txtColor = Color.parseColor("#C04040"),
            border   = Color.parseColor("#C04040")
        )
        btnDeleteAll.setOnClickListener { confirmDeleteAll() }
        bottomPanel.addView(btnDeleteAll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(bottomPanel)

        // ScrollView con la lista
        val scroll = ScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.BELOW, toolbarId)
                addRule(RelativeLayout.ABOVE, bottomPanelId)
            }
        }
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16*d).toInt(), (12*d).toInt(), (16*d).toInt(), (8*d).toInt())
        }
        tvEmpty = TextView(this).apply {
            text = getString(R.string.recordings_empty)
            textSize = 15f; setTextColor(palette.textSecondary)
            gravity = Gravity.CENTER; visibility = View.GONE
            setPadding(0, (60*d).toInt(), 0, 0)
        }
        listContainer.addView(tvEmpty)
        scroll.addView(listContainer)
        root.addView(scroll)

        setContentView(root)
    }

    private fun makeAccentBtn(text: String, bgColor: Int, txtColor: Int, border: Int) =
        androidx.appcompat.widget.AppCompatButton(this).apply {
            this.text = text; textSize = 13f
            setTextColor(txtColor)
            backgroundTintList = null; stateListAnimator = null
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 12 * d
                setColor(bgColor); setStroke((1.5f*d).toInt(), border)
            }
            setPadding((16*d).toInt(), (10*d).toInt(), (16*d).toInt(), (10*d).toInt())
        }

    // ── Lista ─────────────────────────────────────────────────────────

    private fun renderList(records: List<GameRecordEntity>) {
        if (isDestroyed || isFinishing) return

        val toRemove = mutableListOf<View>()
        for (i in 1 until listContainer.childCount) toRemove.add(listContainer.getChildAt(i))
        toRemove.forEach { listContainer.removeView(it) }

        tvEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        btnDeleteAll.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        records.forEach { record -> listContainer.addView(buildCard(record, sdf)) }

        updateSelectionUI()
    }

    private fun buildCard(record: GameRecordEntity, sdf: SimpleDateFormat): View {
        val isSelected = record.id in selectedIds

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = 12 * d
                setColor(if (isSelected) palette.buttonBg else palette.surface)
                setStroke(
                    if (isSelected) (2.5f*d).toInt() else (1*d).toInt(),
                    if (isSelected) palette.accent else palette.border
                )
            }
            setPadding((16*d).toInt(), (12*d).toInt(), (16*d).toInt(), (12*d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10*d).toInt() }
            isClickable = true; isFocusable = true

            setOnClickListener {
                if (selectionMode) toggleSelection(record.id)
                else GameReplayActivity.start(this@GameRecordingsActivity, record.id, paletteIdx)
            }
            setOnLongClickListener {
                if (!selectionMode) enterSelectionMode()
                toggleSelection(record.id)
                true
            }
        }

        val dateStr = try { sdf.format(Date(record.startTimestamp)) } catch (_: Exception) { "???" }

        val resultStr = when (record.result) {
            "WIN"    -> getString(R.string.recordings_result_win)
            "LOSS"   -> getString(R.string.recordings_result_loss)
            "DRAW"   -> getString(R.string.recordings_result_draw)
            "RESIGN" -> getString(R.string.recordings_result_resign)
            else     -> getString(R.string.recordings_result_ongoing)
        }
        val resultColor = when (record.result) {
            "WIN"    -> Color.parseColor("#2E7D32")
            "LOSS"   -> Color.parseColor("#C62828")
            "RESIGN" -> Color.parseColor("#8B4513")
            else     -> palette.textSecondary
        }
        val modeStr = when (record.mode) {
            "TWO_PLAYER" -> getString(R.string.recordings_mode_two)
            "TIMED"      -> getString(R.string.recordings_mode_timed)
            else         -> getString(R.string.recordings_mode_normal)
        }

        // Fila 1: checkbox indicador + fecha + resultado
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        if (selectionMode) {
            row1.addView(TextView(this).apply {
                text = if (isSelected) "✓ " else "○ "
                textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isSelected) palette.accent else palette.border)
            })
        }
        row1.addView(TextView(this).apply {
            text = dateStr; textSize = 13f; typeface = Typeface.MONOSPACE
            setTextColor(palette.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row1.addView(TextView(this).apply {
            text = resultStr; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(resultColor)
        })

        // Fila 2: modo + movimientos + botón eliminar individual
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4*d).toInt() }
        }
        row2.addView(TextView(this).apply {
            text = modeStr; textSize = 12f; setTextColor(palette.accent)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row2.addView(TextView(this).apply {
            text = getString(R.string.recordings_moves, record.totalMoves)
            textSize = 12f; setTextColor(palette.textSecondary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8*d).toInt() }
        })
        // Botón eliminar individual (solo fuera del modo selección)
        if (!selectionMode) {
            row2.addView(TextView(this).apply {
                text = "🗑"; textSize = 16f
                setTextColor(Color.parseColor("#C04040"))
                setPadding((8*d).toInt(), 0, 0, 0)
                setOnClickListener { confirmDeleteOne(record) }
            })
        }

        card.addView(row1); card.addView(row2)
        return card
    }

    // ── Selección múltiple ────────────────────────────────────────────

    private fun enterSelectionMode() {
        selectionMode = true
        supportActionBar?.title = getString(R.string.recordings_select_mode_title)
        updateSelectionUI()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        supportActionBar?.title = getString(R.string.recordings_title)
        toolbar?.navigationIcon?.setTint(palette.accent)
        renderList(allRecords)
    }

    private fun toggleSelection(id: Int) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
        updateSelectionUI()
        renderList(allRecords)
    }

    private fun updateSelectionUI() {
        if (selectionMode) {
            val count = selectedIds.size
            tvSelectionInfo.visibility = View.VISIBLE
            tvSelectionInfo.text = getString(R.string.recordings_selected_count, count)
            btnDeleteSelected.visibility = if (count > 0) View.VISIBLE else View.GONE
        } else {
            tvSelectionInfo.visibility    = View.GONE
            btnDeleteSelected.visibility  = View.GONE
        }
    }

    private var toolbar: Toolbar? = null
    override fun setSupportActionBar(toolbar: androidx.appcompat.widget.Toolbar?) {
        this.toolbar = toolbar
        super.setSupportActionBar(toolbar)
    }

    // ── Eliminar ──────────────────────────────────────────────────────

    private fun confirmDeleteOne(record: GameRecordEntity) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.recordings_delete_one_title))
            .setMessage(getString(R.string.recordings_delete_one_msg))
            .setPositiveButton(getString(R.string.recordings_delete_one_yes)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) { repo.deleteById(record.id) }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDeleteSelected() {
        val count = selectedIds.size
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.recordings_delete_selected))
            .setMessage(getString(R.string.recordings_delete_selected_msg, count))
            .setPositiveButton(getString(R.string.recordings_delete_one_yes)) { _, _ ->
                val toDelete = selectedIds.toSet()
                lifecycleScope.launch(Dispatchers.IO) {
                    toDelete.forEach { repo.deleteById(it) }
                    withContext(Dispatchers.Main) { exitSelectionMode() }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.recordings_delete_all))
            .setMessage(getString(R.string.recordings_delete_confirm))
            .setPositiveButton(getString(R.string.recordings_delete_yes)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) { repo.deleteAll() }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onBackPressed() {
        if (selectionMode) exitSelectionMode() else super.onBackPressed()
    }
}
