package tech.hezy.androidnumerictext.animation

import android.view.animation.PathInterpolator

object NumericTextTransition {
    val SnappyInterpolator = PathInterpolator(0.3f, 0.0f, 0.15f, 1.0f)
    const val ANIMATION_DURATION = 200L
} 