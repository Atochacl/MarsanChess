package com.marsan.chess.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode.ifEmpty { "es" })
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getLanguageCode(context: Context): String {
        val prefs = context.getSharedPreferences("marsan_chess_prefs", Context.MODE_PRIVATE)
        return prefs.getString("selected_language", "es") ?: "es"
    }
}
