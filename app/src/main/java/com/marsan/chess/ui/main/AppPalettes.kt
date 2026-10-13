package com.marsan.chess.ui.main

import android.graphics.Color

data class AppPalette(
    val background: Int,
    val surface: Int,
    val accent: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val border: Int,
    val buttonBg: Int,
    val buttonText: Int,
    val boardLight: Int,
    val boardDark: Int
)

object AppPalettes {

    // ── ORIGINALES (6) ────────────────────────────────────────────────

    private val VANILLA = AppPalette(
        background    = Color.parseColor("#FDFAE8"),
        surface       = Color.parseColor("#FFFDF5"),
        accent        = Color.parseColor("#7A6B1A"),
        textPrimary   = Color.parseColor("#3D3510"),
        textSecondary = Color.parseColor("#7A6B1A"),
        border        = Color.parseColor("#D4C96A"),
        buttonBg      = Color.parseColor("#EDE8C0"),
        buttonText    = Color.parseColor("#5A4E12"),
        boardLight    = Color.parseColor("#F7F0C8"),
        boardDark     = Color.parseColor("#A89840")
    )

    private val SKY = AppPalette(
        background    = Color.parseColor("#E4EDF8"),
        surface       = Color.parseColor("#F2F7FD"),
        accent        = Color.parseColor("#2A5AA0"),
        textPrimary   = Color.parseColor("#152D50"),
        textSecondary = Color.parseColor("#3A6DB5"),
        border        = Color.parseColor("#8AADD6"),
        buttonBg      = Color.parseColor("#C0D4EE"),
        buttonText    = Color.parseColor("#1A3D70"),
        boardLight    = Color.parseColor("#D4E8F8"),
        boardDark     = Color.parseColor("#4A80B8")
    )

    private val LILAC = AppPalette(
        background    = Color.parseColor("#EFE8F2"),
        surface       = Color.parseColor("#FAF5FC"),
        accent        = Color.parseColor("#7A3D96"),
        textPrimary   = Color.parseColor("#3D1E4B"),
        textSecondary = Color.parseColor("#8A5AA8"),
        border        = Color.parseColor("#C4A0D8"),
        buttonBg      = Color.parseColor("#DCC8E8"),
        buttonText    = Color.parseColor("#561A6E"),
        boardLight    = Color.parseColor("#EAD8F5"),
        boardDark     = Color.parseColor("#9060B0")
    )

    private val MIST = AppPalette(
        background    = Color.parseColor("#E6EAF0"),
        surface       = Color.parseColor("#F4F6FA"),
        accent        = Color.parseColor("#3A5070"),
        textPrimary   = Color.parseColor("#1C2B3A"),
        textSecondary = Color.parseColor("#4A6080"),
        border        = Color.parseColor("#96AABF"),
        buttonBg      = Color.parseColor("#C8D4E0"),
        buttonText    = Color.parseColor("#243850"),
        boardLight    = Color.parseColor("#D8E2EC"),
        boardDark     = Color.parseColor("#607890")
    )

    private val PEACH = AppPalette(
        background    = Color.parseColor("#FAE8E0"),
        surface       = Color.parseColor("#FDF5F2"),
        accent        = Color.parseColor("#A04028"),
        textPrimary   = Color.parseColor("#502014"),
        textSecondary = Color.parseColor("#B05040"),
        border        = Color.parseColor("#D89A84"),
        buttonBg      = Color.parseColor("#EEC8B8"),
        buttonText    = Color.parseColor("#702C18"),
        boardLight    = Color.parseColor("#F5DDD5"),
        boardDark     = Color.parseColor("#C07060")
    )

    private val MINT = AppPalette(
        background    = Color.parseColor("#DFF2E4"),
        surface       = Color.parseColor("#F2FAF4"),
        accent        = Color.parseColor("#1E7A42"),
        textPrimary   = Color.parseColor("#0E3D21"),
        textSecondary = Color.parseColor("#2E8A52"),
        border        = Color.parseColor("#80C89A"),
        buttonBg      = Color.parseColor("#B8E0C4"),
        buttonText    = Color.parseColor("#0E5530"),
        boardLight    = Color.parseColor("#C8EDD4"),
        boardDark     = Color.parseColor("#4A9862")
    )

    // ── NUEVAS (15) ───────────────────────────────────────────────────

    // Barro Dorado
    private val GOLDEN_MUD = AppPalette(
        background    = Color.parseColor("#F2E8C8"),
        surface       = Color.parseColor("#FBF5E0"),
        accent        = Color.parseColor("#886020"),
        textPrimary   = Color.parseColor("#443010"),
        textSecondary = Color.parseColor("#987030"),
        border        = Color.parseColor("#D0B870"),
        buttonBg      = Color.parseColor("#E8D090"),
        buttonText    = Color.parseColor("#583810"),
        boardLight    = Color.parseColor("#EADEA8"),
        boardDark     = Color.parseColor("#A08838")
    )

    // Azul Ártico
    private val ARCTIC = AppPalette(
        background    = Color.parseColor("#DCF0F8"),
        surface       = Color.parseColor("#EEF8FD"),
        accent        = Color.parseColor("#186898"),
        textPrimary   = Color.parseColor("#0C3450"),
        textSecondary = Color.parseColor("#287888"),
        border        = Color.parseColor("#80C0D8"),
        buttonBg      = Color.parseColor("#A8D8EE"),
        buttonText    = Color.parseColor("#105078"),
        boardLight    = Color.parseColor("#C8E8F5"),
        boardDark     = Color.parseColor("#4098C0")
    )

    // Cian Glaciar
    private val GLACIER = AppPalette(
        background    = Color.parseColor("#D8F0F0"),
        surface       = Color.parseColor("#EEF8F8"),
        accent        = Color.parseColor("#207888"),
        textPrimary   = Color.parseColor("#103C44"),
        textSecondary = Color.parseColor("#308898"),
        border        = Color.parseColor("#80C8C8"),
        buttonBg      = Color.parseColor("#A8DCDC"),
        buttonText    = Color.parseColor("#186068"),
        boardLight    = Color.parseColor("#C0ECE8"),
        boardDark     = Color.parseColor("#40A0A0")
    )

    // Verde Agua
    private val SEA_GREEN = AppPalette(
        background    = Color.parseColor("#D8F0E8"),
        surface       = Color.parseColor("#EEF9F4"),
        accent        = Color.parseColor("#207860"),
        textPrimary   = Color.parseColor("#103C30"),
        textSecondary = Color.parseColor("#308870"),
        border        = Color.parseColor("#80C8A8"),
        buttonBg      = Color.parseColor("#A8DCC4"),
        buttonText    = Color.parseColor("#186050"),
        boardLight    = Color.parseColor("#C0ECD8"),
        boardDark     = Color.parseColor("#40A080")
    )

    // Oliva Pastel
    private val OLIVE = AppPalette(
        background    = Color.parseColor("#E8ECD8"),
        surface       = Color.parseColor("#F4F7EC"),
        accent        = Color.parseColor("#506830"),
        textPrimary   = Color.parseColor("#283418"),
        textSecondary = Color.parseColor("#607840"),
        border        = Color.parseColor("#A8C888"),
        buttonBg      = Color.parseColor("#C4D8A8"),
        buttonText    = Color.parseColor("#385028"),
        boardLight    = Color.parseColor("#D8EAC0"),
        boardDark     = Color.parseColor("#709858")
    )

    // Pino Claro
    private val LIGHT_PINE = AppPalette(
        background    = Color.parseColor("#D8EAE0"),
        surface       = Color.parseColor("#EEF7F2"),
        accent        = Color.parseColor("#2C6848"),
        textPrimary   = Color.parseColor("#163424"),
        textSecondary = Color.parseColor("#3C7858"),
        border        = Color.parseColor("#88C0A0"),
        buttonBg      = Color.parseColor("#A8D4B8"),
        buttonText    = Color.parseColor("#205038"),
        boardLight    = Color.parseColor("#C0E4D0"),
        boardDark     = Color.parseColor("#489868")
    )

    // Chicle
    private val BUBBLEGUM = AppPalette(
        background    = Color.parseColor("#FDE0EC"),
        surface       = Color.parseColor("#FEF0F6"),
        accent        = Color.parseColor("#C03870"),
        textPrimary   = Color.parseColor("#601838"),
        textSecondary = Color.parseColor("#D04880"),
        border        = Color.parseColor("#F0A0C0"),
        buttonBg      = Color.parseColor("#F8C4D8"),
        buttonText    = Color.parseColor("#902858"),
        boardLight    = Color.parseColor("#FDD0E4"),
        boardDark     = Color.parseColor("#D86898")
    )

    // Menta Helada
    private val MINT_ICE = AppPalette(
        background    = Color.parseColor("#D8F5EC"),
        surface       = Color.parseColor("#EEFCf6"),
        accent        = Color.parseColor("#208868"),
        textPrimary   = Color.parseColor("#104438"),
        textSecondary = Color.parseColor("#309878"),
        border        = Color.parseColor("#80D0B0"),
        buttonBg      = Color.parseColor("#A8E4CC"),
        buttonText    = Color.parseColor("#186850"),
        boardLight    = Color.parseColor("#C0ECD8"),
        boardDark     = Color.parseColor("#40B888")
    )

    // Lavanda Dulce
    private val SWEET_LAVENDER = AppPalette(
        background    = Color.parseColor("#EEE0F8"),
        surface       = Color.parseColor("#F7F0FD"),
        accent        = Color.parseColor("#7030A8"),
        textPrimary   = Color.parseColor("#381858"),
        textSecondary = Color.parseColor("#8040B8"),
        border        = Color.parseColor("#C098E8"),
        buttonBg      = Color.parseColor("#D8B8F8"),
        buttonText    = Color.parseColor("#582890"),
        boardLight    = Color.parseColor("#E4D0F8"),
        boardDark     = Color.parseColor("#9858C8")
    )

    // Rojo Frambuesa
    private val RASPBERRY = AppPalette(
        background    = Color.parseColor("#FDD8E4"),
        surface       = Color.parseColor("#FEEDF2"),
        accent        = Color.parseColor("#A81848"),
        textPrimary   = Color.parseColor("#540C24"),
        textSecondary = Color.parseColor("#B82858"),
        border        = Color.parseColor("#EE98B8"),
        buttonBg      = Color.parseColor("#F8BCD0"),
        buttonText    = Color.parseColor("#800C38"),
        boardLight    = Color.parseColor("#FDCCE0"),
        boardDark     = Color.parseColor("#C85888")
    )

    // Fucsia Pastel
    private val SOFT_FUCHSIA = AppPalette(
        background    = Color.parseColor("#F8DCEE"),
        surface       = Color.parseColor("#FCEFF8"),
        accent        = Color.parseColor("#B02890"),
        textPrimary   = Color.parseColor("#581448"),
        textSecondary = Color.parseColor("#C038A0"),
        border        = Color.parseColor("#E898D0"),
        buttonBg      = Color.parseColor("#F4BCE4"),
        buttonText    = Color.parseColor("#881870"),
        boardLight    = Color.parseColor("#F4CCEC"),
        boardDark     = Color.parseColor("#C860B8")
    )

    // Rosa Caramelo
    private val PINK_CARAMEL = AppPalette(
        background    = Color.parseColor("#F8E0E4"),
        surface       = Color.parseColor("#FCF2F4"),
        accent        = Color.parseColor("#A84860"),
        textPrimary   = Color.parseColor("#542430"),
        textSecondary = Color.parseColor("#B85870"),
        border        = Color.parseColor("#E0A8B0"),
        buttonBg      = Color.parseColor("#F0C8CC"),
        buttonText    = Color.parseColor("#783040"),
        boardLight    = Color.parseColor("#F0D5D8"),
        boardDark     = Color.parseColor("#C07888")
    )

    // Naranja Papaya
    private val PAPAYA = AppPalette(
        background    = Color.parseColor("#FEE8D0"),
        surface       = Color.parseColor("#FFF4E8"),
        accent        = Color.parseColor("#C05810"),
        textPrimary   = Color.parseColor("#602C08"),
        textSecondary = Color.parseColor("#D06820"),
        border        = Color.parseColor("#F0B880"),
        buttonBg      = Color.parseColor("#F8D0A8"),
        buttonText    = Color.parseColor("#904008"),
        boardLight    = Color.parseColor("#FDDDC0"),
        boardDark     = Color.parseColor("#D89040")
    )

    // Azafrán Pastel
    private val SAFFRON = AppPalette(
        background    = Color.parseColor("#FDF0D0"),
        surface       = Color.parseColor("#FEF8E8"),
        accent        = Color.parseColor("#A87818"),
        textPrimary   = Color.parseColor("#543C0C"),
        textSecondary = Color.parseColor("#B88828"),
        border        = Color.parseColor("#E8D080"),
        buttonBg      = Color.parseColor("#F4E4A0"),
        buttonText    = Color.parseColor("#806010"),
        boardLight    = Color.parseColor("#F8E8B8"),
        boardDark     = Color.parseColor("#C0A038")
    )

    // Naranja Coral Suave
    private val CORAL_ORANGE = AppPalette(
        background    = Color.parseColor("#FDDDD0"),
        surface       = Color.parseColor("#FEEEEA"),
        accent        = Color.parseColor("#C06040"),
        textPrimary   = Color.parseColor("#603020"),
        textSecondary = Color.parseColor("#D07050"),
        border        = Color.parseColor("#F0B0A0"),
        buttonBg      = Color.parseColor("#F8CCC0"),
        buttonText    = Color.parseColor("#904830"),
        boardLight    = Color.parseColor("#FAD8CC"),
        boardDark     = Color.parseColor("#D09070")
    )

    // ── LISTA COMPLETA (6 originales + 15 nuevas = 21 paletas) ───────

    val all = listOf(
        // Originales
        VANILLA, SKY, LILAC, MIST, PEACH, MINT,
        // Nuevas
        GOLDEN_MUD, ARCTIC, GLACIER, SEA_GREEN,
        OLIVE, LIGHT_PINE,
        BUBBLEGUM, MINT_ICE, SWEET_LAVENDER,
        RASPBERRY, SOFT_FUCHSIA, PINK_CARAMEL,
        PAPAYA, SAFFRON, CORAL_ORANGE
    )
}
