package com.marsan.chess.ui.main

data class AppLanguage(
    val code: String,
    val nameInLanguage: String,
    val flagEmoji: String,
    val flagResName: String?,
    val acceptLabel: String,
    val locale: String
)

object AppLanguages {
    val all = listOf(
        AppLanguage(
            code           = "es",
            nameInLanguage = "Español",
            flagEmoji      = "🇨🇱",
            flagResName    = null,
            acceptLabel    = "Aceptar",
            locale         = "es"
        ),
        AppLanguage(
            code           = "en",
            nameInLanguage = "English",
            flagEmoji      = "🇺🇸",
            flagResName    = null,
            acceptLabel    = "Accept",
            locale         = "en"
        )
    )

    fun fromCode(code: String) = all.firstOrNull { it.code == code } ?: all[0]
}
