package tech.hezy.numerictext

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.animation.PathInterpolator

internal object NumericTextTransition {
    val SnappyInterpolator = PathInterpolator(0.3f, 0.0f, 0.15f, 1.0f)
    const val ANIMATION_DURATION = 200

    fun animationsEnabled(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
} 