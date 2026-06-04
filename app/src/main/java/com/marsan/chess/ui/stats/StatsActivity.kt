package com.marsan.chess.ui.stats

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.marsan.chess.data.repository.GameRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StatsActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context, playerName: String = "") {
            context.startActivity(Intent(context, StatsActivity::class.java).apply {
                putExtra("player_name", playerName)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = GameRepository.getInstance(this)
        val playerName = intent.getStringExtra("player_name") ?: ""
        val d = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        val toolbar = Toolbar(this).apply {
            title = "Mi historial"
            setTitleTextColor(Color.parseColor("#111111"))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        root.addView(toolbar)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20*d).toInt(); setPadding(p, p, p, p)
        }

        container.addView(TextView(this).apply {
            text = if (playerName.isEmpty()) "Estadisticas" else playerName
            textSize = 22f; setTextColor(Color.parseColor("#111111"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (4*d).toInt() }
        })
        container.addView(TextView(this).apply {
            text = if (playerName.isEmpty()) "Ingresa tu nombre en una partida" else "Historial personal"
            textSize = 13f; setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16*d).toInt() }
        })

        val statsCard = makeCard(d)
        val statsGrid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((8*d).toInt(), (12*d).toInt(), (8*d).toInt(), (12*d).toInt())
        }
        val tvPlayed    = makeStatCol(statsGrid, "0", "Jugadas",    Color.parseColor("#111111"), d)
        val tvWins      = makeStatCol(statsGrid, "0", "Ganadas",    Color.parseColor("#111111"), d)
        val tvLosses    = makeStatCol(statsGrid, "0", "Perdidas",   Color.parseColor("#888888"), d)
        val tvDraws     = makeStatCol(statsGrid, "0", "Tablas",     Color.parseColor("#888888"), d)
        val tvAbandoned = makeStatCol(statsGrid, "0", "Abandon.",   Color.parseColor("#C04040"), d)
        statsCard.addView(statsGrid)
        statsCard.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16*d).toInt() }
        container.addView(statsCard)

        val pointsCard = makeCard(d)
        val pointsRow  = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((16*d).toInt(), (14*d).toInt(), (16*d).toInt(), (14*d).toInt())
        }
        val tvPointsLabel = TextView(this).apply {
            text = "Puntos totales"; textSize = 14f; setTextColor(Color.parseColor("#555555"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvPoints = TextView(this).apply {
            text = "0 pts"; textSize = 20f; typeface = Typeface.MONOSPACE; setTextColor(Color.parseColor("#111111"))
        }
        pointsRow.addView(tvPointsLabel); pointsRow.addView(tvPoints)
        pointsCard.addView(pointsRow)
        pointsCard.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        container.addView(pointsCard)

        scroll.addView(container); root.addView(scroll)
        setContentView(root)

        if (playerName.isNotEmpty()) {
            lifecycleScope.launch {
                repository.observePlayer(playerName).collectLatest { player ->
                    player ?: return@collectLatest
                    tvPlayed.text    = player.gamesPlayed.toString()
                    tvWins.text      = player.gamesWon.toString()
                    tvLosses.text    = player.gamesLost.toString()
                    tvDraws.text     = player.gamesDraw.toString()
                    val abandoned    = (player.gamesPlayed - player.gamesWon - player.gamesLost - player.gamesDraw).coerceAtLeast(0)
                    tvAbandoned.text = abandoned.toString()
                    tvPoints.text    = "${player.totalPoints} pts"
                }
            }
        }
    }

    private fun makeCard(d: Float) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background  = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 12*d
            setColor(Color.WHITE); setStroke((1*d).toInt(), Color.parseColor("#D0D0D0"))
        }
    }

    private fun makeStatCol(parent: LinearLayout, value: String, label: String, valueColor: Int, d: Float): TextView {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding((2*d).toInt(), (8*d).toInt(), (2*d).toInt(), (8*d).toInt())
        }
        val tvVal = TextView(this).apply {
            text = value; textSize = 17f; setTextColor(valueColor); gravity = android.view.Gravity.CENTER
        }
        val tvLbl = TextView(this).apply {
            text = label; textSize = 10f; setTextColor(Color.parseColor("#888888")); gravity = android.view.Gravity.CENTER
        }
        col.addView(tvVal); col.addView(tvLbl); parent.addView(col)
        return tvVal
    }
}
