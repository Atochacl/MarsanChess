package com.marsan.chess.util

import android.content.Context
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.content.ContextCompat

// ===================== VIEW EXTENSIONS =====================

fun View.fadeIn() {
    if (visibility == View.VISIBLE) return
    visibility = View.VISIBLE
    alpha = 0f
    animate().alpha(1f).setDuration(200).start()
}

fun View.fadeOut(gone: Boolean = true) {
    animate().alpha(0f).setDuration(150).withEndAction {
        visibility = if (gone) View.GONE else View.INVISIBLE
        alpha = 1f
    }.start()
}

fun View.scalePress() {
    animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
        animate().scaleX(1f).scaleY(1f).setDuration(80).start()
    }.start()
}

fun View.slideInFromBottom() {
    translationY = 60f
    alpha = 0f
    animate()
        .translationY(0f)
        .alpha(1f)
        .setDuration(250)
        .setInterpolator(android.view.animation.DecelerateInterpolator())
        .start()
}

// ===================== CONTEXT EXTENSIONS =====================

fun Context.dpToPx(dp: Float): Int =
    (dp * resources.displayMetrics.density).toInt()

fun Context.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).toInt()

// ===================== FORMAT EXTENSIONS =====================

fun Long.toTimeString(): String {
    val m = this / 60
    val s = this % 60
    return "%02d:%02d".format(m, s)
}

fun Int.toPointsString(): String = when {
    this == 0 -> "0 pts"
    this == 1 -> "1 pt"
    else      -> "$this pts"
}

// ===================== STRING EXTENSIONS =====================

fun String.capitalizeFirst(): String =
    if (isEmpty()) this else this[0].uppercaseChar() + substring(1)
