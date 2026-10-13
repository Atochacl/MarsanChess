package com.marsan.chess.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.marsan.chess.util.LocaleHelper

/**
 * Todas las Activities extienden esta clase para que el locale
 * se aplique automáticamente antes de inflar cualquier layout.
 */
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val langCode = LocaleHelper.getLanguageCode(newBase)
        val localizedContext = LocaleHelper.applyLocale(newBase, langCode)
        super.attachBaseContext(localizedContext)
    }
}
