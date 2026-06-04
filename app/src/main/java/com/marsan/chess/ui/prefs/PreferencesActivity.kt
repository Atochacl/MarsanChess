package com.marsan.chess.ui.prefs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.marsan.chess.data.model.PlayerPieceColor
import com.marsan.chess.databinding.ActivityPrefsBinding
import com.marsan.chess.util.PreferencesManager

class PreferencesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrefsBinding
    private lateinit var prefs: PreferencesManager

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PreferencesActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrefsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferencesManager.getInstance(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupUI()
    }

    private fun setupUI() {
        updateNameUI()
        updateColorUI()

        binding.cardName.setOnClickListener      { showChangeNameDialog() }
        binding.btnColorWhite.setOnClickListener  { prefs.playerPieceColor = PlayerPieceColor.WHITE;  updateColorUI() }
        binding.btnColorBlack.setOnClickListener  { prefs.playerPieceColor = PlayerPieceColor.BLACK;  updateColorUI() }
        binding.btnColorRandom.setOnClickListener { prefs.playerPieceColor = PlayerPieceColor.RANDOM; updateColorUI() }
    }

    private fun updateNameUI() {
        val name = prefs.playerName
        binding.tvCurrentName.text = if (name.isEmpty()) "Sin nombre" else name
        binding.tvNameSub.text     = "Toca para cambiar tu nombre"
    }

    private fun updateColorUI() {
        val selected = prefs.playerPieceColor

        // Mostrar tick solo en la opción seleccionada — sin cambiar el fondo
        binding.tvWhiteTick.visibility  = if (selected == PlayerPieceColor.WHITE)  View.VISIBLE else View.INVISIBLE
        binding.tvBlackTick.visibility  = if (selected == PlayerPieceColor.BLACK)  View.VISIBLE else View.INVISIBLE
        binding.tvRandomTick.visibility = if (selected == PlayerPieceColor.RANDOM) View.VISIBLE else View.INVISIBLE

        // Resaltar el borde del seleccionado cambiando el background
        binding.btnColorWhite.setBackgroundResource(
            if (selected == PlayerPieceColor.WHITE) com.marsan.chess.R.drawable.bg_color_option_selected
            else com.marsan.chess.R.drawable.bg_color_option)
        binding.btnColorBlack.setBackgroundResource(
            if (selected == PlayerPieceColor.BLACK) com.marsan.chess.R.drawable.bg_color_option_selected
            else com.marsan.chess.R.drawable.bg_color_option)
        binding.btnColorRandom.setBackgroundResource(
            if (selected == PlayerPieceColor.RANDOM) com.marsan.chess.R.drawable.bg_color_option_selected
            else com.marsan.chess.R.drawable.bg_color_option)

        binding.tvColorDesc.text = when (selected) {
            PlayerPieceColor.WHITE  -> "Juegas con piezas blancas. La CPU usa piezas negras."
            PlayerPieceColor.BLACK  -> "Juegas con piezas negras. La CPU usa piezas blancas."
            PlayerPieceColor.RANDOM -> "Se asignará un color al azar al iniciar cada partida."
        }
    }

    private fun showChangeNameDialog() {
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint      = "Escribe tu nombre"
            setText(prefs.playerName)
            setSelection(text.length)
            maxLines  = 1
            filters   = arrayOf(android.text.InputFilter.LengthFilter(15))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle("Cambiar nombre")
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    prefs.playerName = newName
                    updateNameUI()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
