package com.marsan.chess.ui

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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

    /**
     * Desde Android 15 la app se dibuja de borde a borde: sin esto la
     * barra de estado y la de navegación tapan la toolbar y los botones.
     * Aplica como padding de [root] el espacio de las barras del sistema
     * (y del notch), y elige íconos oscuros o claros en la barra de
     * estado según el color de fondo [backgroundColor].
     * [root] no debe tener padding propio: se reemplaza.
     */
    protected fun applySystemBarsPadding(root: View, backgroundColor: Int) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        val lightBackground = ColorUtils.calculateLuminance(backgroundColor) > 0.5
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars     = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
    }
}
