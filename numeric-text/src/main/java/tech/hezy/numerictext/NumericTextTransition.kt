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

    // 取最近两次输入中较快的间隔，避免一帧调度抖动重新拉长连点动画；停顿后恢复宿主时长。
    fun durationForUpdate(baseMillis: Int, intervalMillis: Long, previousIntervalMillis: Long): Int =
        if (intervalMillis >= baseMillis) baseMillis else minOf(baseMillis.toLong(),
            maxOf(16L, minOf(intervalMillis, previousIntervalMillis) * 2)).toInt()

    fun animationsEnabled(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
} 