package com.marsan.chess.ui.prefs

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.marsan.chess.R
import com.marsan.chess.data.model.PlayerPieceColor
import com.marsan.chess.databinding.ActivityPrefsBinding
import com.marsan.chess.ui.BaseActivity
import com.marsan.chess.ui.main.AppLanguages
import com.marsan.chess.ui.main.AppPalettes
import com.marsan.chess.ui.main.LanguageSelectActivity
import com.marsan.chess.util.PreferencesManager

class PreferencesActivity : BaseActivity() {

    private lateinit var binding: ActivityPrefsBinding
    private lateinit var prefs: PreferencesManager

    companion object {
        private const val EXTRA_PALETTE_IDX = "palette_idx"
        fun start(context: Context, paletteIdx: Int = 0) {
            context.startActivity(Intent(context, PreferencesActivity::class.java).apply {
                putExtra(EXTRA_PALETTE_IDX, paletteIdx)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrefsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferencesManager.getInstance(this)

        val paletteIdx = intent.getIntExtra(EXTRA_PALETTE_IDX, 0)
            .coerceIn(0, AppPalettes.all.size - 1)
        val palette    = AppPalettes.all[paletteIdx]
        val d          = resources.displayMetrics.density

        binding.rootPrefs.setBackgroundColor(palette.background)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.prefs_title)
        binding.toolbar.setBackgroundColor(palette.background)
        binding.toolbar.setTitleTextColor(palette.textPrimary)
        binding.toolbar.navigationIcon?.setTint(palette.accent)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.cardName.setCardBackgroundColor(palette.surface)
        binding.tvCurrentName.setTextColor(palette.textPrimary)
        binding.tvNameSub.setTextColor(palette.accent)
        binding.cardLanguage.setCardBackgroundColor(palette.surface)
        binding.tvCurrentLanguage.setTextColor(palette.textPrimary)

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updateLanguageUI()
    }

    private fun setupUI() {
        updateNameUI()
        updateColorUI()
        updateStyleUI()
        updateLanguageUI()

        setupRecordingToggle()
        binding.cardName.setOnClickListener       { showChangeNameDialog() }
        binding.btnColorWhite.setOnClickListener  { prefs.playerPieceColor = PlayerPieceColor.WHITE;  updateColorUI() }
        binding.btnColorBlack.setOnClickListener  { prefs.playerPieceColor = PlayerPieceColor.BLACK;  updateColorUI() }
        binding.btnColorRandom.setOnClickListener { prefs.playerPieceColor = PlayerPieceColor.RANDOM; updateColorUI() }
        binding.btnStyle1.setOnClickListener      { prefs.pieceStyle = 1; updateStyleUI(); updateColorUI() }
        binding.btnStyle2.setOnClickListener      { prefs.pieceStyle = 2; updateStyleUI(); updateColorUI() }
        binding.btnStyle3.setOnClickListener      { prefs.pieceStyle = 3; updateStyleUI(); updateColorUI() }
        binding.cardLanguage.setOnClickListener   {
            startActivity(Intent(this, LanguageSelectActivity::class.java).apply {
                putExtra(LanguageSelectActivity.EXTRA_FROM_PREFS, true)
            })
        }
    }

    // ── NOMBRE ────────────────────────────────────────────────────────

    private fun updateNameUI() {
        val name = prefs.playerName
        binding.tvCurrentName.text = if (name.isEmpty()) getString(R.string.prefs_name_no_name) else name
        binding.tvNameSub.text     = getString(R.string.prefs_name_tap)
    }

    // ── COLOR DE PIEZAS ───────────────────────────────────────────────
    // Los iconos de cada botón muestran el tipo de pieza actualmente seleccionado

    private fun updateColorUI() {
        val selected = prefs.playerPieceColor
        val style    = prefs.pieceStyle

        // Actualizar ticks
        binding.tvWhiteTick.visibility  = if (selected == PlayerPieceColor.WHITE)  View.VISIBLE else View.INVISIBLE
        binding.tvBlackTick.visibility  = if (selected == PlayerPieceColor.BLACK)  View.VISIBLE else View.INVISIBLE
        binding.tvRandomTick.visibility = if (selected == PlayerPieceColor.RANDOM) View.VISIBLE else View.INVISIBLE

        // Bordes seleccionados
        binding.btnColorWhite.setBackgroundResource(
            if (selected == PlayerPieceColor.WHITE)  R.drawable.bg_color_option_selected else R.drawable.bg_color_option)
        binding.btnColorBlack.setBackgroundResource(
            if (selected == PlayerPieceColor.BLACK)  R.drawable.bg_color_option_selected else R.drawable.bg_color_option)
        binding.btnColorRandom.setBackgroundResource(
            if (selected == PlayerPieceColor.RANDOM) R.drawable.bg_color_option_selected else R.drawable.bg_color_option)

        // Labels de los botones de color
        binding.tvWhiteLabel.text  = getString(R.string.prefs_color_white)
        binding.tvBlackLabel.text  = getString(R.string.prefs_color_black)
        binding.tvRandomLabel.text = getString(R.string.prefs_color_random)

        // Iconos según el tipo de pieza seleccionado
        buildColorIcons(binding.containerWhiteIcons, isWhite = true,  style = style)
        buildColorIcons(binding.containerBlackIcons, isWhite = false, style = style)
        buildRandomIcons(binding.containerRandomIcons, style = style)

        binding.tvColorDesc.text = when (selected) {
            PlayerPieceColor.WHITE  -> getString(R.string.prefs_color_white_desc)
            PlayerPieceColor.BLACK  -> getString(R.string.prefs_color_black_desc)
            PlayerPieceColor.RANDOM -> getString(R.string.prefs_color_random_desc)
        }
    }

    /**
     * Rellena el contenedor de iconos del botón de color.
     * Según el estilo: PNG (tipo 1 o 3) o texto Unicode (tipo 2).
     */
    private fun buildColorIcons(container: LinearLayout, isWhite: Boolean, style: Int) {
        container.removeAllViews()
        val colorPrefix = if (isWhite) "w" else "b"
        val d = resources.displayMetrics.density
        val iconSizePx = (34 * d).toInt()

        when (style) {
            2 -> {
                // Unicode: caballo y torre en el color correspondiente
                val tv = TextView(this).apply {
                    text      = if (isWhite) "♘♖" else "♞♜"
                    textSize  = 30f
                    setTextColor(if (isWhite) 0xFF555555.toInt() else 0xFF111111.toInt())
                }
                container.addView(tv)
            }
            else -> {
                // PNG: tipo 1 → piece_w_knight, tipo 3 → piece3_w_knight
                val prefix = if (style == 3) "piece3" else "piece"
                for (pieceType in listOf("knight", "rook")) {
                    val resName = "${prefix}_${colorPrefix}_$pieceType"
                    val resId   = resources.getIdentifier(resName, "drawable", packageName)
                    if (resId != 0) {
                        val iv = ImageView(this).apply {
                            setImageResource(resId)
                            layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                                if (pieceType == "rook") marginStart = (4 * d).toInt()
                            }
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                        container.addView(iv)
                    }
                }
            }
        }
    }

    private fun buildRandomIcons(container: LinearLayout, style: Int) {
        container.removeAllViews()
        val d = resources.displayMetrics.density
        val iconSizePx = (34 * d).toInt()

        when (style) {
            2 -> {
                val tv = TextView(this).apply {
                    text     = "♙♟"
                    textSize = 30f
                    setTextColor(0xFF333333.toInt())
                }
                container.addView(tv)
            }
            else -> {
                val prefix = if (style == 3) "piece3" else "piece"
                for ((colorPrefix, marginStart) in listOf("w" to 0, "b" to 4)) {
                    val resName = "${prefix}_${colorPrefix}_pawn"
                    val resId   = resources.getIdentifier(resName, "drawable", packageName)
                    if (resId != 0) {
                        val iv = ImageView(this).apply {
                            setImageResource(resId)
                            layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                                if (marginStart > 0) this.marginStart = (marginStart * d).toInt()
                            }
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                        container.addView(iv)
                    }
                }
            }
        }
    }

    // ── TIPO DE PIEZAS ────────────────────────────────────────────────

    private fun updateStyleUI() {
        val style  = prefs.pieceStyle
        val d      = resources.displayMetrics.density
        val iconSizePx = (30 * d).toInt()

        // Ticks
        binding.tvStyle1Tick.visibility = if (style == 1) View.VISIBLE else View.INVISIBLE
        binding.tvStyle2Tick.visibility = if (style == 2) View.VISIBLE else View.INVISIBLE
        binding.tvStyle3Tick.visibility = if (style == 3) View.VISIBLE else View.INVISIBLE

        // Bordes
        binding.btnStyle1.setBackgroundResource(
            if (style == 1) R.drawable.bg_color_option_selected else R.drawable.bg_color_option)
        binding.btnStyle2.setBackgroundResource(
            if (style == 2) R.drawable.bg_color_option_selected else R.drawable.bg_color_option)
        binding.btnStyle3.setBackgroundResource(
            if (style == 3) R.drawable.bg_color_option_selected else R.drawable.bg_color_option)

        // Preview del estilo 1 (PNG pixel art): muestra un rey y un caballo blancos
        buildStylePreview(binding.containerStyle1Icons, prefix = "piece",  iconSizePx = iconSizePx)
        // Preview del estilo 3 (PNG personalizado)
        buildStylePreview(binding.containerStyle3Icons, prefix = "piece3", iconSizePx = iconSizePx)

        // Descripción
        binding.tvStyleDesc.text = when (style) {
            1 -> getString(R.string.prefs_piece_style_1_desc)
            2 -> getString(R.string.prefs_piece_style_2_desc)
            3 -> getString(R.string.prefs_piece_style_3_desc)
            else -> ""
        }
    }

    private fun buildStylePreview(container: LinearLayout, prefix: String, iconSizePx: Int) {
        container.removeAllViews()
        val d = resources.displayMetrics.density
        for (piece in listOf("w_king", "b_knight")) {
            val resName = "${prefix}_$piece"
            val resId   = resources.getIdentifier(resName, "drawable", packageName)
            if (resId != 0) {
                val iv = ImageView(this).apply {
                    setImageResource(resId)
                    layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                        if (piece.startsWith("b")) marginStart = (4 * d).toInt()
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                container.addView(iv)
            }
        }
    }

    // ── IDIOMA ────────────────────────────────────────────────────────

    private fun updateLanguageUI() {
        val lang = AppLanguages.fromCode(prefs.selectedLanguage.ifEmpty { "es" })
        binding.tvCurrentLanguage.text = "${lang.flagEmoji}  ${lang.nameInLanguage}"
    }

    // ── GRABACIÓN ─────────────────────────────────────────────────────

    private fun setupRecordingToggle() {
        // El toggle está en el XML de preferencias como cardRecording
        // Si no existe en el binding, lo creamos programáticamente
        // Como el layout de prefs no tiene este card aún, lo añadimos
        // directamente en el ScrollView vía código
        // (ver activity_prefs.xml — la sección de grabación se añade vía código aquí)
        updateRecordingUI()
        binding.cardRecording?.setOnClickListener {
            prefs.recordingEnabled = !prefs.recordingEnabled
            updateRecordingUI()
        }
    }

    private fun updateRecordingUI() {
        val enabled = prefs.recordingEnabled
        binding.tvRecordingStatus?.text = if (enabled)
            getString(R.string.prefs_recording_on)
        else getString(R.string.prefs_recording_off)
        binding.tvRecordingStatus?.setTextColor(
            if (enabled) com.marsan.chess.ui.main.AppPalettes.all[
                intent.getIntExtra("palette_idx", 0).coerceIn(0, com.marsan.chess.ui.main.AppPalettes.all.size - 1)
            ].accent
            else android.graphics.Color.parseColor("#888888")
        )
    }

    // ── CAMBIAR NOMBRE ────────────────────────────────────────────────

    private fun showChangeNameDialog() {
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint      = getString(R.string.name_hint)
            setText(prefs.playerName); setSelection(text.length)
            maxLines  = 1
            filters   = arrayOf(android.text.InputFilter.LengthFilter(15))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0); addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.prefs_name_change_title))
            .setView(container)
            .setPositiveButton(getString(R.string.prefs_name_save)) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) { prefs.playerName = newName; updateNameUI() }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
