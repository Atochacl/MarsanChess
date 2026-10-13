package com.marsan.chess.ui.stats

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.marsan.chess.R
import com.marsan.chess.data.repository.GameRepository
import com.marsan.chess.ui.BaseActivity
import com.marsan.chess.ui.replay.GameRecordingsActivity
import com.marsan.chess.ui.main.AppPalettes
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StatsActivity : BaseActivity() {

    companion object {
        private const val EXTRA_PLAYER_NAME = "player_name"
        private const val EXTRA_PALETTE_IDX = "palette_idx"

        fun start(context: Context, playerName: String = "", paletteIdx: Int = 0) {
            context.startActivity(Intent(context, StatsActivity::class.java).apply {
                putExtra(EXTRA_PLAYER_NAME, playerName)
                putExtra(EXTRA_PALETTE_IDX, paletteIdx)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = GameRepository.getInstance(this)
        val playerName = intent.getStringExtra(EXTRA_PLAYER_NAME) ?: ""
        val paletteIdx = intent.getIntExtra(EXTRA_PALETTE_IDX, 0)
            .coerceIn(0, AppPalettes.all.size - 1)
        val palette    = AppPalettes.all[paletteIdx]
        val d          = resources.displayMetrics.density

        val root = RelativeLayout(this).apply { setBackgroundColor(palette.background) }

        // Toolbar
        val toolbar = Toolbar(this).apply {
            id = android.view.View.generateViewId()
            title = getString(R.string.stats_title)
            setTitleTextColor(palette.textPrimary)
            setBackgroundColor(palette.background)
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) }
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.navigationIcon?.setTint(palette.accent)
        root.addView(toolbar)

        // Botón eliminar fijo al fondo
        val btnClearId = android.view.View.generateViewId()
        if (playerName.isNotEmpty()) {
            val btnClear = android.widget.Button(this).apply {
                id       = btnClearId
                text     = getString(R.string.stats_delete_history)
                textSize = 13f
                setTextColor(Color.parseColor("#C04040"))
                background = GradientDrawable().apply {
                    shape        = GradientDrawable.RECTANGLE
                    cornerRadius = 12 * d
                    setColor(Color.parseColor("#FFF5F5"))
                    setStroke((1.5f * d).toInt(), Color.parseColor("#C04040"))
                }
                layoutParams = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                    addRule(RelativeLayout.CENTER_HORIZONTAL)
                    bottomMargin = (24 * d).toInt()
                }
                val ph = (24 * d).toInt(); val pv = (12 * d).toInt()
                setPadding(ph, pv, ph, pv)
                setOnClickListener {
                    AlertDialog.Builder(this@StatsActivity)
                        .setTitle(getString(R.string.stats_delete_history))
                        .setMessage(getString(R.string.stats_delete_confirm))
                        .setPositiveButton(getString(R.string.stats_delete_yes)) { _, _ ->
                            lifecycleScope.launch { repository.clearHistory(playerName) }
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            }
            root.addView(btnClear)
        }

        // Botón "Ver partidas grabadas" fijo arriba del botón eliminar
        val btnRecordingsId = android.view.View.generateViewId()
        val btnRecordings = android.widget.Button(this).apply {
            id = btnRecordingsId
            text = getString(R.string.stats_see_recordings)
            textSize = 13f
            setTextColor(palette.accent)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 12 * d
                setColor(palette.buttonBg)
                setStroke((1.5f * d).toInt(), palette.accent)
            }
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ABOVE, if (playerName.isNotEmpty()) btnClearId else -1)
                addRule(RelativeLayout.CENTER_HORIZONTAL)
                bottomMargin = (8 * d).toInt()
                if (playerName.isEmpty()) {
                    addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                    bottomMargin = (24 * d).toInt()
                }
            }
            val ph = (20 * d).toInt(); val pv = (10 * d).toInt(); setPadding(ph, pv, ph, pv)
            setOnClickListener {
                GameRecordingsActivity.start(this@StatsActivity, paletteIdx)
            }
        }
        root.addView(btnRecordings)

        val scroll = ScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.BELOW, toolbar.id)
                addRule(RelativeLayout.ABOVE, btnRecordingsId)
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * d).toInt(); setPadding(p, (24 * d).toInt(), p, p)
        }

        // Nombre del jugador
        container.addView(TextView(this).apply {
            text     = if (playerName.isEmpty()) getString(R.string.stats_title) else playerName
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.textPrimary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4 * d).toInt() }
        })
        container.addView(TextView(this).apply {
            text     = if (playerName.isEmpty()) getString(R.string.stats_enter_name)
                       else getString(R.string.stats_personal)
            textSize = 13f; setTextColor(palette.accent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (24 * d).toInt() }
        })

        // ── Tarjeta estadísticas ──────────────────────────────────────
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = 16 * d
                setColor(palette.surface)
                setStroke((1 * d).toInt(), palette.border)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * d).toInt() }
        }

        // Fila 1: Jugadas | Ganadas | Perdidas
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((12 * d).toInt(), (20 * d).toInt(), (12 * d).toInt(), (14 * d).toInt())
        }

        // Fila 2: Tablas | Abandonadas (con separador visual)
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background  = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.argb(25,
                    Color.red(palette.border),
                    Color.green(palette.border),
                    Color.blue(palette.border)
                ))
            }
            setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (20 * d).toInt())
        }

        // Colores semánticos por categoría
        val colorPlayed = palette.textPrimary
        val colorWin    = Color.parseColor("#2E7D32")   // verde
        val colorLoss   = Color.parseColor("#C62828")   // rojo
        val colorDraw   = palette.textSecondary
        val colorResign = Color.parseColor("#B71C1C")   // rojo oscuro

        val tvPlayed    = makeStatCell(row1, "0", getString(R.string.stats_games),    colorPlayed, d, weight = 1.1f)
        val tvWins      = makeStatCell(row1, "0", getString(R.string.stats_wins),     colorWin,    d)
        val tvLosses    = makeStatCell(row1, "0", getString(R.string.stats_losses),   colorLoss,   d)

        val tvDraws     = makeStatCell(row2, "0", getString(R.string.stats_draws),    colorDraw,   d)
        val tvAbandoned = makeStatCell(row2, "0", getString(R.string.stats_abandoned),colorResign, d, weight = 1.4f)

        // Celda vacía para equilibrar row2 (mismo peso que las otras)
        row2.addView(LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        statsCard.addView(row1)
        statsCard.addView(row2)
        container.addView(statsCard)

        // ── Tarjeta puntos ────────────────────────────────────────────
        val pointsCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            background  = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = 16 * d
                setColor(palette.accent)
            }
            setPadding((20 * d).toInt(), (18 * d).toInt(), (20 * d).toInt(), (18 * d).toInt())
        }
        val tvPointsLabel = TextView(this).apply {
            text     = getString(R.string.stats_total_points)
            textSize = 15f
            setTextColor(Color.argb(200,
                Color.red(palette.surface),
                Color.green(palette.surface),
                Color.blue(palette.surface)
            ))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvPoints = TextView(this).apply {
            text     = "0 pts"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.surface)
        }
        pointsCard.addView(tvPointsLabel)
        pointsCard.addView(tvPoints)
        container.addView(pointsCard)

        scroll.addView(container)
        root.addView(scroll)
        setContentView(root)

        if (playerName.isNotEmpty()) {
            lifecycleScope.launch {
                repository.observePlayer(playerName).collectLatest { player ->
                    player ?: return@collectLatest
                    tvPlayed.text    = player.gamesPlayed.toString()
                    tvWins.text      = player.gamesWon.toString()
                    tvLosses.text    = player.gamesLost.toString()
                    tvDraws.text     = player.gamesDraw.toString()
                    tvAbandoned.text = player.gamesResigned.toString()
                    tvPoints.text    = "${player.totalPoints} pts"
                }
            }
        }
    }

    /** Celda de estadística: número grande + etiqueta pequeña debajo */
    private fun makeStatCell(
        parent: LinearLayout,
        value: String,
        label: String,
        valueColor: Int,
        d: Float,
        weight: Float = 1f
    ): TextView {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight
            )
            setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
        }
        val tvVal = TextView(this).apply {
            text     = value
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            gravity  = Gravity.CENTER
            setTextColor(valueColor)
        }
        val tvLbl = TextView(this).apply {
            text     = label
            textSize = 11f
            gravity  = Gravity.CENTER
            setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (2 * d).toInt() }
        }
        col.addView(tvVal)
        col.addView(tvLbl)
        parent.addView(col)
        return tvVal
    }
}
