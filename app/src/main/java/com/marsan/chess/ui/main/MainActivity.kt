package com.marsan.chess.ui.main

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.marsan.chess.R
import com.marsan.chess.data.model.*
import com.marsan.chess.databinding.ActivityMainBinding
import com.marsan.chess.ui.game.GameActivity
import com.marsan.chess.ui.prefs.PreferencesActivity
import com.marsan.chess.ui.stats.StatsActivity
import com.marsan.chess.util.PreferencesManager
import com.marsan.chess.util.slideInFromBottom

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferencesManager.getInstance(this)
        setupButtons()
        animateEntrance()
        checkFirstLaunch()
    }

    private fun animateEntrance() {
        binding.layoutTitle.slideInFromBottom()
        binding.layoutButtons.postDelayed({ binding.layoutButtons.slideInFromBottom() }, 80)
        binding.layoutSecondary.postDelayed({ binding.layoutSecondary.slideInFromBottom() }, 140)
    }

    private fun setupButtons() {
        binding.btnNewGame.setOnClickListener { startGame() }
        binding.btnStats.setOnClickListener   { StatsActivity.start(this, prefs.playerName) }
        binding.btnPrefs.setOnClickListener   { PreferencesActivity.start(this) }
    }

    // Primer arranque: pide nombre
    private fun checkFirstLaunch() {
        if (prefs.playerName.isEmpty()) {
            binding.root.postDelayed({ showNameDialog() }, 500)
        }
    }

    private fun showNameDialog() {
        val editText = EditText(this).apply {
            hint     = "Escribe tu nombre"
            maxLines = 1
            filters  = arrayOf(android.text.InputFilter.LengthFilter(15))
            inputType= android.text.InputType.TYPE_CLASS_TEXT or
                       android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle("¡Bienvenido a Marsan Chess!")
            .setMessage("Ingresa tu nombre para comenzar:")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("¡Jugar!") { _, _ ->
                val name = editText.text.toString().trim()
                prefs.playerName = if (name.isEmpty()) "Jugador" else name
                prefs.nameAsked  = true
            }
            .show()
    }

    private fun startGame() {
        showDifficultyDialog { difficulty ->
            GameActivity.start(this, prefs.playerName.ifEmpty { "Jugador" }, difficulty)
        }
    }

    private fun showDifficultyDialog(onConfirm: (Difficulty) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_difficulty, null)
        var selected = Difficulty.fromLevel(prefs.lastDifficulty)

        val diffData = listOf(
            R.id.diff1 to Difficulty.LEVEL_1, R.id.diff2 to Difficulty.LEVEL_2,
            R.id.diff3 to Difficulty.LEVEL_3, R.id.diff4 to Difficulty.LEVEL_4,
            R.id.diff5 to Difficulty.LEVEL_5
        )

        fun updateUI() {
            diffData.forEach { (id, diff) ->
                val tv = dialogView.findViewById<TextView>(id)
                tv.text = "${diff.level}\n${diff.elo}"; tv.textSize = 11f
                if (diff == selected) {
                    tv.setBackgroundResource(R.drawable.bg_difficulty_chip_selected)
                    tv.setTextColor(Color.WHITE)
                } else {
                    tv.setBackgroundResource(R.drawable.bg_difficulty_chip)
                    tv.setTextColor(Color.parseColor("#555555"))
                }
            }
        }
        updateUI()
        diffData.forEach { (id, diff) ->
            dialogView.findViewById<TextView>(id).setOnClickListener { selected = diff; updateUI() }
        }

        val dialog = AlertDialog.Builder(this).setTitle("Jugar").setView(dialogView)
            .setCancelable(true).create()
        dialogView.findViewById<View>(R.id.btnPlay).setOnClickListener {
            prefs.lastDifficulty = selected.level; dialog.dismiss(); onConfirm(selected)
        }
        dialog.show()
    }
}
