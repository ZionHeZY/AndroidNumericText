package tech.hezy.numerictext

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val NumberEasing = CubicBezierEasing(0.3f, 0f, 0.15f, 1f)
private val DefaultNumberStyle = TextStyle(fontSize = 36.sp)

/**
 * 整个整数滚动切换：增加向上、减少向下，首次显示不播放动画。
 * 支持完整 Int 范围；style 控制文字样式，显式 color 优先于 style.color。
 * animationDurationMillis 为 0 时立即更新；系统动画时长设置由 Compose 动画时钟处理。
 * 无障碍仅读取最新的完整整数，不读取退出中的旧文本。
 */
@Composable
fun AnimatedNumber(
    number: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = DefaultNumberStyle,
    color: Color = Color.Unspecified,
    animationDurationMillis: Int = 200
) {
    require(animationDurationMillis >= 0) { "animationDurationMillis must not be negative" }
    val numberText = remember(number) { AnnotatedString(number.toString()) }
    val textStyle = remember(style, color) {
        if (color == Color.Unspecified) style else style.copy(color = color)
    }
    val accessibleModifier = modifier.clearAndSetSemantics {
        text = numberText
        liveRegion = LiveRegionMode.Polite
    }
    if (animationDurationMillis == 0) {
        BasicText(text = numberText.text, modifier = accessibleModifier, style = textStyle, maxLines = 1, softWrap = false)
        return
    }
    AnimatedContent(
        targetState = number,
        modifier = accessibleModifier,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (slideInVertically(tween(animationDurationMillis, easing = NumberEasing)) { direction * it } +
                fadeIn(tween(animationDurationMillis, easing = NumberEasing))).togetherWith(
                slideOutVertically(tween(animationDurationMillis, easing = NumberEasing)) { -direction * it } +
                    fadeOut(tween(animationDurationMillis, easing = NumberEasing))
            ).using(SizeTransform(clip = true) { _, _ -> tween(animationDurationMillis, easing = NumberEasing) })
        },
        label = "AnimatedNumber"
    ) { targetNumber ->
        val text = remember(targetNumber) { if (targetNumber == number) numberText.text else targetNumber.toString() }
        BasicText(text = text, style = textStyle, maxLines = 1, softWrap = false)
    }
}
