package com.marsan.chess.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.marsan.chess.databinding.ActivitySplashBinding
import com.marsan.chess.ui.BaseActivity
import com.marsan.chess.util.PreferencesManager

class SplashActivity : BaseActivity() {
    private lateinit var binding: ActivitySplashBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val prefs = PreferencesManager.getInstance(this)
        val prevIdx = prefs.activePaletteIndex
        var newIdx = (0 until AppPalettes.all.size).random()
        if (AppPalettes.all.size > 1) while (newIdx == prevIdx) newIdx = (0 until AppPalettes.all.size).random()
        prefs.activePaletteIndex = newIdx
        binding.ivSplash.alpha = 0f
        binding.ivSplash.animate().alpha(1f).setDuration(500).start()
        Handler(Looper.getMainLooper()).postDelayed({
            val next = if (prefs.selectedLanguage.isEmpty()) Intent(this, LanguageSelectActivity::class.java)
                       else Intent(this, MainActivity::class.java)
            startActivity(next)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2000)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}
}
