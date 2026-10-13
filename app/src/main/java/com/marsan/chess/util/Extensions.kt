package com.marsan.chess.util

import android.content.Context
import android.view.View

// ── VIEW ──────────────────────────────────────────────────────────────

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

// ── CONTEXT ───────────────────────────────────────────────────────────

fun Context.dpToPx(dp: Float): Int =
    (dp * resources.displayMetrics.density).toInt()

// ── FORMAT ────────────────────────────────────────────────────────────

/** Converts seconds to mm:ss string */
fun Long.toTimeString(): String {
    val m = this / 60
    val s = this % 60
    return "%02d:%02d".format(m, s)
}
