package com.marsan.chess.ui.main

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import com.marsan.chess.ui.BaseActivity
import com.marsan.chess.util.LocaleHelper
import com.marsan.chess.util.PreferencesManager
import java.util.Locale

class LanguageSelectActivity : BaseActivity() {

    private lateinit var prefs: PreferencesManager
    private var selectedLang: AppLanguage = AppLanguages.all[0]
    private lateinit var palette: AppPalette
    private lateinit var tvTitle: TextView
    private lateinit var btnAccept: android.widget.Button
    private val cellViews = mutableMapOf<String, LinearLayout>()
    private var fromPreferences = false

    companion object { const val EXTRA_FROM_PREFS = "from_prefs" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        prefs           = PreferencesManager.getInstance(this)
        fromPreferences = intent.getBooleanExtra(EXTRA_FROM_PREFS, false)
        palette         = AppPalettes.all[prefs.activePaletteIndex.coerceIn(0, AppPalettes.all.size - 1)]
        val current = prefs.selectedLanguage
        if (current.isNotEmpty()) selectedLang = AppLanguages.fromCode(current)
        setContentView(buildUI())
    }

    private fun buildUI(): android.view.View {
        val d=resources.displayMetrics.density; val bg=palette.background; val acc=palette.accent
        val tp=palette.textPrimary; val ts=palette.textSecondary; val sf=palette.surface; val br=palette.border
        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        val content = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
            val p=(32*d).toInt(); setPadding(p,p,p,p)
            layoutParams=FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        tvTitle = TextView(this).apply {
            text=getTitleForLang(selectedLang.code); textSize=22f; gravity=Gravity.CENTER; setTextColor(tp)
            layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin=(40*d).toInt() }
        }
        content.addView(tvTitle)
        val flagRow = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER
            layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin=(40*d).toInt() }
        }
        AppLanguages.all.forEach { lang ->
            val isSelected = lang.code == selectedLang.code
            val cell = buildCell(lang, isSelected, d, acc, tp, ts, sf, br)
            cellViews[lang.code] = cell
            cell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins((12*d).toInt(),0,(12*d).toInt(),0) }
            flagRow.addView(cell)
        }
        content.addView(flagRow)
        btnAccept = android.widget.Button(this).apply {
            text=selectedLang.acceptLabel; textSize=17f; setTextColor(sf)
            background=GradientDrawable().apply { shape=GradientDrawable.RECTANGLE; cornerRadius=14*d; setColor(acc) }
            layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0,(16*d).toInt(),0,(16*d).toInt())
            setOnClickListener { confirmSelection() }
        }
        content.addView(btnAccept)
        root.addView(content)
        return root
    }

    private fun buildCell(lang: AppLanguage, selected: Boolean, d: Float, acc: Int, tp: Int, ts: Int, sf: Int, br: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
            background=GradientDrawable().apply {
                shape=GradientDrawable.RECTANGLE; cornerRadius=16*d
                if (selected) { setColor(acc); setStroke((3*d).toInt(),acc) }
                else { setColor(Color.argb(40,Color.red(acc),Color.green(acc),Color.blue(acc))); setStroke((1.5f*d).toInt(),br) }
            }
            val p=(20*d).toInt(); setPadding(p,p,p,p); isClickable=true; isFocusable=true
            setOnClickListener { selectLanguage(lang) }
            addView(TextView(context).apply {
                text=lang.flagEmoji; textSize=52f; gravity=Gravity.CENTER
                layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin=(10*d).toInt() }
            })
            addView(TextView(context).apply { text=lang.nameInLanguage; textSize=16f; gravity=Gravity.CENTER; setTextColor(if (selected) sf else tp) })
        }
    }

    private fun selectLanguage(lang: AppLanguage) {
        val d=resources.displayMetrics.density; val acc=palette.accent; val tp=palette.textPrimary; val sf=palette.surface; val br=palette.border
        val prev=selectedLang; selectedLang=lang
        cellViews[prev.code]?.apply {
            background=GradientDrawable().apply { shape=GradientDrawable.RECTANGLE; cornerRadius=16*d; setColor(Color.argb(40,Color.red(acc),Color.green(acc),Color.blue(acc))); setStroke((1.5f*d).toInt(),br) }
            (getChildAt(1) as? TextView)?.setTextColor(tp)
        }
        cellViews[lang.code]?.apply {
            background=GradientDrawable().apply { shape=GradientDrawable.RECTANGLE; cornerRadius=16*d; setColor(acc); setStroke((3*d).toInt(),acc) }
            (getChildAt(1) as? TextView)?.setTextColor(sf)
        }
        tvTitle.text=getTitleForLang(lang.code); btnAccept.text=lang.acceptLabel
    }

    private fun getTitleForLang(code: String) = if (code=="es") "Selecciona tu idioma" else "Select your language"

    private fun confirmSelection() {
        prefs.selectedLanguage = selectedLang.code
        // Aplicar locale en el contexto de la app
        LocaleHelper.applyLocale(applicationContext, selectedLang.locale)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
