package tech.hezy.numerictext

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.animation.Interpolator
import kotlin.math.exp

internal object NumericTextTransition {
    private val springSamples = FloatArray(257) { index ->
        val t = index / 256.0
        ((1.0 - (1.0 + 10.6 * t) * exp(-10.6 * t)) / (1.0 - 11.6 * exp(-10.6))).toFloat()
    }
    val SnappyInterpolator = Interpolator { fraction ->
        val position = fraction.coerceIn(0f, 1f) * 256f
        val index = position.toInt().coerceAtMost(255)
        springSamples[index] + (springSamples[index + 1] - springSamples[index]) * (position - index)
    }
    const val ANIMATION_DURATION = 500

    fun animationsEnabled(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
} 