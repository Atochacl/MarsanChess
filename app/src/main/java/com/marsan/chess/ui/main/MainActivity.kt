package com.marsan.chess.ui.main

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.marsan.chess.R
import com.marsan.chess.data.model.*
import com.marsan.chess.engine.PieceColor
import com.marsan.chess.databinding.ActivityMainBinding
import com.marsan.chess.ui.BaseActivity
import com.marsan.chess.ui.game.GameActivity
import com.marsan.chess.ui.prefs.PreferencesActivity
import com.marsan.chess.ui.stats.StatsActivity
import com.marsan.chess.util.PreferencesManager
import com.marsan.chess.util.slideInFromBottom

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var palette: AppPalette

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs   = PreferencesManager.getInstance(this)
        palette = AppPalettes.all[prefs.activePaletteIndex.coerceIn(0, AppPalettes.all.size - 1)]

        applyPalette()
        setupButtons()
        applyWindowInsets()
        animateEntrance()
        checkFirstLaunch()
    }

    private fun applyPalette() {
        val d = resources.displayMetrics.density

        binding.root.setBackgroundColor(palette.background)
        // tvAppIcon es ahora un ImageView — no tiene setTextColor
        // El logo PNG usa su propio color; si quieres teñirlo con la paleta:
        binding.tvAppIcon.setColorFilter(palette.accent)
        binding.tvAppName.setTextColor(palette.textPrimary)
        binding.tvTagline.setTextColor(palette.textSecondary)

        // JUGAR — acento sólido
        binding.btnNewGame.background = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 14 * d
            setColor(palette.accent)
        }
        binding.btnNewGame.setTextColor(palette.surface)
        binding.btnNewGame.letterSpacing = 0.04f

        // MI HISTORIAL — tono de botón
        binding.btnStats.background = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 14 * d
            setColor(palette.buttonBg)
            setStroke((1.5f * d).toInt(), palette.accent)
        }
        binding.btnStats.setTextColor(palette.buttonText)
        binding.btnStats.letterSpacing = 0.04f

        // PREFERENCIAS — suave
        binding.btnPrefs.background = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 14 * d
            setColor(palette.surface)
            setStroke((1f * d).toInt(), palette.border)
        }
        binding.btnPrefs.setTextColor(palette.textSecondary)
        binding.btnPrefs.letterSpacing = 0.04f
    }

    private fun animateEntrance() {
        binding.layoutTitle.slideInFromBottom()
        binding.layoutCenter.postDelayed({ binding.layoutCenter.slideInFromBottom() }, 80)
        binding.layoutBottom.postDelayed({ binding.layoutBottom.slideInFromBottom() }, 140)
    }

    private fun setupButtons() {
        binding.btnNewGame.setOnClickListener { showGameModeDialog() }
        binding.btnStats.setOnClickListener {
            StatsActivity.start(this, prefs.playerName, prefs.activePaletteIndex)
        }
        binding.btnPrefs.setOnClickListener {
            PreferencesActivity.start(this, prefs.activePaletteIndex)
        }
    }

    private fun checkFirstLaunch() {
        if (prefs.playerName.isEmpty()) {
            binding.root.postDelayed({ showNameDialog() }, 400)
        }
    }

    private fun showNameDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.name_hint); maxLines = 1
            filters   = arrayOf(InputFilter.LengthFilter(15))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0); addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(getString(R.string.name_title))
            .setView(container).setCancelable(false)
            .setPositiveButton(getString(R.string.name_play_button)) { _, _ ->
                val name = editText.text.toString().trim()
                prefs.playerName = if (name.isEmpty()) getString(R.string.default_player_name) else name
                prefs.nameAsked  = true
            }.show()
    }

    // ── DIÁLOGOS ─────────────────────────────────────────────────────

    private fun showGameModeDialog() {
        val d    = resources.displayMetrics.density
        val root = dialogRoot(d)
        root.addView(dialogTitle(getString(R.string.menu_new_game), d))
        val dlg = styledDialog(root)
        // 1. Partida normal (vs CPU, sin tiempo)
        root.addView(makeModeRow(
            getString(R.string.game_mode_normal),
            getString(R.string.game_mode_normal_sub), d
        ) {
            dlg.dismiss()
            showDifficultyDialog(0L)
        })

        // 2. 2 Jugadores (mismo dispositivo)
        root.addView(makeModeRow(
            getString(R.string.game_mode_two_players),
            getString(R.string.game_mode_two_players_sub), d
        ) {
            dlg.dismiss()
            showDifficultyDialogTwoPlayers()
        })

        // 3. Partida con tiempo (vs CPU)
        root.addView(makeModeRow(
            getString(R.string.game_mode_timed),
            getString(R.string.game_mode_timed_sub), d
        ) {
            dlg.dismiss()
            showTimedDialog()
        })
        dlg.show()
        dlg.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showTimedDialog() {
        val d    = resources.displayMetrics.density
        val root = dialogRoot(d)
        root.addView(dialogTitle(getString(R.string.game_time_input_title), d))
        root.addView(TextView(this).apply {
            text = getString(R.string.game_time_desc); textSize = 13f; setTextColor(palette.accent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (18 * d).toInt() }
        })
        val dlg = styledDialog(root)
        listOf(3 to "3'", 5 to "5'", 10 to "10'", 15 to "15'").forEach { (minutes, label) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background  = itemBg(d)
                val pv = (14 * d).toInt(); val ph = (20 * d).toInt(); setPadding(ph, pv, ph, pv)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * d).toInt() }
                isClickable = true; isFocusable = true
                setOnClickListener { dlg.dismiss(); showDifficultyDialog(minutes * 60L) }
            }
            row.addView(TextView(this).apply {
                text = label; textSize = 26f; setTextColor(palette.accent)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (16 * d).toInt() }
            })
            row.addView(TextView(this).apply {
                text = "$minutes ${getString(R.string.game_time_minutes)}"
                textSize = 14f; setTextColor(palette.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply { text = "›"; textSize = 20f; setTextColor(palette.accent) })
            root.addView(row)
        }
        dlg.show()
        dlg.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showDifficultyDialog(timeLimitSeconds: Long) {
        var selected = Difficulty.fromLevel(prefs.lastDifficulty)
        val d = resources.displayMetrics.density
        val diffLabels = listOf(
            getString(R.string.difficulty_1_label), getString(R.string.difficulty_2_label),
            getString(R.string.difficulty_3_label), getString(R.string.difficulty_4_label),
            getString(R.string.difficulty_5_label)
        )
        val diffElos = listOf(
            getString(R.string.difficulty_1_elo), getString(R.string.difficulty_2_elo),
            getString(R.string.difficulty_3_elo), getString(R.string.difficulty_4_elo),
            getString(R.string.difficulty_5_elo)
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 20 * d; setColor(palette.surface)
            }
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.difficulty_title); textSize = 20f; setTextColor(palette.textPrimary)
            val ph = (20 * d).toInt()
            setPadding(ph, (20 * d).toInt(), ph, (14 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val ph = (20 * d).toInt(); setPadding(ph, 0, ph, (4 * d).toInt())
        }
        scroll.addView(listContainer); root.addView(scroll)

        val btnPlay = android.widget.Button(this).apply {
            text = getString(R.string.name_play_button); textSize = 16f
            setTextColor(palette.surface); letterSpacing = 0.04f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 14 * d; setColor(palette.accent)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins((20*d).toInt(), (8*d).toInt(), (20*d).toInt(), (20*d).toInt()) }
            val pv = (16 * d).toInt(); setPadding(0, pv, 0, pv)
        }
        root.addView(btnPlay)

        fun buildRows() {
            listContainer.removeAllViews()
            Difficulty.entries.forEach { diff ->
                val isSel = diff == selected
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    background  = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = 12 * d
                        if (isSel) { setColor(palette.accent); setStroke((2*d).toInt(), palette.accent) }
                        else       { setColor(palette.background); setStroke((1.5f*d).toInt(), palette.border) }
                    }
                    val pv = (13*d).toInt(); val ph = (16*d).toInt(); setPadding(ph, pv, ph, pv)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (8*d).toInt() }
                    isClickable = true; isFocusable = true
                    setOnClickListener { selected = diff; buildRows() }
                }
                val stars = listOf("★☆☆☆☆","★★☆☆☆","★★★☆☆","★★★★☆","★★★★★")[diff.level - 1]
                row.addView(TextView(this).apply {
                    text = stars; textSize = 11f
                    setTextColor(if (isSel) Color.argb(180, 255, 255, 255) else palette.border)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = (12*d).toInt() }
                })
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                col.addView(TextView(this).apply {
                    text = diffLabels[diff.level - 1]; textSize = 15f
                    setTextColor(if (isSel) palette.surface else palette.textPrimary)
                })
                col.addView(TextView(this).apply {
                    text = diffElos[diff.level - 1]; textSize = 12f
                    setTextColor(if (isSel) Color.argb(200, Color.red(palette.surface),
                        Color.green(palette.surface), Color.blue(palette.surface)) else palette.accent)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (2*d).toInt() }
                })
                row.addView(col)
                if (isSel) row.addView(TextView(this).apply {
                    text = "✓"; textSize = 16f; setTextColor(palette.surface)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = (8*d).toInt() }
                })
                listContainer.addView(row)
            }
        }
        buildRows()

        val dlg = styledDialog(root)
        btnPlay.setOnClickListener {
            prefs.lastDifficulty = selected.level; dlg.dismiss()
            GameActivity.start(
                context          = this,
                playerName       = prefs.playerName.ifEmpty { getString(R.string.default_player_name) },
                difficulty       = selected,
                timeLimitSeconds = timeLimitSeconds,
                mode             = if (timeLimitSeconds > 0L) GameMode.TIMED else GameMode.NORMAL,
                paletteIdx       = prefs.activePaletteIndex
            )
        }
        dlg.show()
        dlg.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            (resources.displayMetrics.heightPixels * 0.68).toInt()
        )
    }

    // ── 4. MODO 2 JUGADORES ──────────────────────────────────────────
    // Solo pide dificultad para dar la experiencia completa aunque no la use la CPU.
    // En modo 2 jugadores no hay CPU — ambos jugadores usan el mismo dispositivo.

    private fun showDifficultyDialogTwoPlayers() {
        val d = resources.displayMetrics.density

        // En 2 jugadores el color es aleatorio — se decide al iniciar la partida
        // Solo necesitamos confirmación para empezar
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 20 * d
                setColor(palette.surface)
            }
            val p = (20 * d).toInt(); setPadding(p, p, p, p)
        }

        root.addView(dialogTitle(getString(R.string.game_mode_two_players), d))

        // Info
        root.addView(TextView(this).apply {
            text = getString(R.string.game_mode_two_players_sub)
            textSize = 13f; setTextColor(palette.textSecondary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8*d).toInt() }
        })
        root.addView(TextView(this).apply {
            text = "♟  " + getString(R.string.two_players_random_color)
            textSize = 13f; setTextColor(palette.accent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (24*d).toInt() }
        })

        val btnPlay = android.widget.Button(this).apply {
            text = getString(R.string.name_play_button); textSize = 16f
            setTextColor(palette.surface); letterSpacing = 0.04f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 14 * d; setColor(palette.accent)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins((0*d).toInt(), (4*d).toInt(), (0*d).toInt(), (4*d).toInt()) }
            val pv = (16*d).toInt(); setPadding(0, pv, 0, pv)
        }
        root.addView(btnPlay)

        val dlg = styledDialog(root)
        btnPlay.setOnClickListener {
            dlg.dismiss()
            // Color aleatorio — blancas o negras
            val p1Color = if (kotlin.random.Random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
            GameActivity.startTwoPlayer(
                context    = this,
                paletteIdx = prefs.activePaletteIndex,
                player1Color = p1Color
            )
        }
        dlg.show()
        dlg.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // ── INSETS DE NAVEGACIÓN ─────────────────────────────────────────
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutBottom) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val basePx = (24 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight,
                basePx + navBar.bottom)
            insets
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────

    private fun dialogRoot(d: Float) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background  = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 20 * d; setColor(palette.surface)
        }
        val p = (20 * d).toInt(); setPadding(p, p, p, p)
    }

    private fun dialogTitle(text: String, d: Float) = TextView(this).apply {
        this.text = text; textSize = 20f; setTextColor(palette.textPrimary)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (18 * d).toInt() }
    }

    private fun itemBg(d: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = 12 * d
        setColor(palette.background); setStroke((1.5f * d).toInt(), palette.border)
    }

    private fun styledDialog(root: LinearLayout) =
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(root).setCancelable(true).create()
            .also { it.window?.setBackgroundDrawableResource(android.R.color.transparent) }

    private fun makeModeRow(title: String, subtitle: String, d: Float, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = itemBg(d)
            val pv = (14*d).toInt(); val ph = (16*d).toInt(); setPadding(ph, pv, ph, pv)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10*d).toInt() }
            isClickable = true; isFocusable = true; setOnClickListener { onClick() }
            addView(TextView(context).apply { text = title; textSize = 16f; setTextColor(palette.textPrimary) })
            addView(TextView(context).apply {
                text = subtitle; textSize = 12f; setTextColor(palette.accent)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (3*d).toInt() }
            })
        }
}
