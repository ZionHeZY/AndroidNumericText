package tech.hezy.numerictext

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.abs

/**
 * 逐位滚动的整数视图，支持负数和完整 Int 范围；不补前导零。
 * 首次赋值立即显示。连续赋值从上次目标值切换，隐藏、移除或关闭系统动画时立即显示最新值。
 * XML 的 textSize 使用尺寸单位，animationDuration 使用毫秒（0 表示无动画）。
 */
class AnimatedNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 36f, resources.displayMetrics)
    }
    private val fontMetrics = Paint.FontMetrics()
    private val glyphBounds = Rect()
    private var oldCharacters = CharArray(11)
    private var targetCharacters = CharArray(11)
    private var oldLength = 1
    private var targetLength = 1
    private var targetNumber = 0
    private var targetText = "0"
    private var hasNumber = false
    private var digitWidth = 0f
    private var lineHeight = 0f
    private var progress = 1f
    private var direction = 1f
    private var initialized = false
    private var observingMotion = false

    /** 最新目标整数，可直接通过 Java 的 getNumber() 读取。 */
    val number: Int get() = targetNumber

    var textSizePixels: Float
        get() = paint.textSize
        set(value) {
            require(value.isFinite() && value > 0f) { "textSizePixels must be positive and finite" }
            if (paint.textSize == value) return
            settle()
            paint.textSize = value
            updateMeasurements()
            requestLayout()
            invalidate()
        }

    /** 支持系统字体或宿主加载的自定义字体；每个数字仍占据稳定的等宽单元。 */
    var typeface: Typeface
        get() = paint.typeface ?: Typeface.DEFAULT
        set(value) {
            if (paint.typeface == value) return
            settle()
            paint.typeface = value
            updateMeasurements()
            requestLayout()
            invalidate()
        }

    /** 与 Android TextView 相同的尺寸单位，例如 COMPLEX_UNIT_SP；不必手动换算像素。 */
    fun setTextSize(unit: Int, size: Float) {
        textSizePixels = TypedValue.applyDimension(unit, size, resources.displayMetrics)
    }

    var textColor: Int
        get() = paint.color
        set(value) {
            if (paint.color == value) return
            paint.color = value
            invalidate()
        }

    var animationDurationMillis: Int = NumericTextTransition.ANIMATION_DURATION
        set(value) {
            require(value >= 0) { "animationDurationMillis must not be negative" }
            if (field == value) return
            field = value
            if (value == 0) settle()
        }

    // 一个视图只复用一个动画器，绘制期间不构造字符串、数组或逐位动画器。
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = NumericTextTransition.SnappyInterpolator
        addUpdateListener {
            progress = it.animatedFraction
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                progress = 1f
                oldLength = targetLength
                requestLayout()
                invalidate()
            }
        })
    }
    private val motionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!NumericTextTransition.animationsEnabled(context)) settle()
        }
    }

    init {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.AnimatedNumberView, defStyleAttr, 0)
        try {
            paint.textSize = attributes.getDimension(R.styleable.AnimatedNumberView_textSize, paint.textSize)
            require(paint.textSize.isFinite() && paint.textSize > 0f) { "textSize must be positive and finite" }
            paint.color = attributes.getColor(R.styleable.AnimatedNumberView_textColor, 0xFF000000.toInt())
            animationDurationMillis = attributes.getInt(
                R.styleable.AnimatedNumberView_animationDuration,
                NumericTextTransition.ANIMATION_DURATION
            )
        } finally {
            attributes.recycle()
        }
        targetCharacters[0] = '0'
        oldCharacters[0] = '0'
        updateMeasurements()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        contentDescription = targetText
        initialized = true
    }

    /** 首次赋值和重复赋值不播放动画；必须在 UI 线程调用。 */
    fun setNumber(number: Int) {
        if (hasNumber && targetNumber == number) return
        settle()
        oldLength = targetLength
        val reusableCharacters = oldCharacters
        oldCharacters = targetCharacters
        targetCharacters = reusableCharacters
        direction = if (number > targetNumber) 1f else -1f
        targetNumber = number
        targetText = number.toString()
        targetLength = targetText.length
        for (index in 0 until targetLength) targetCharacters[index] = targetText[index]
        contentDescription = targetText
        val animate = hasNumber && animationDurationMillis > 0 && isAttachedToWindow &&
            isShown && windowVisibility == VISIBLE && NumericTextTransition.animationsEnabled(context)
        hasNumber = true
        if (animate) {
            progress = 0f
            animator.duration = animationDurationMillis.toLong()
            animator.start()
        } else {
            progress = 1f
            oldLength = targetLength
        }
        requestLayout()
        invalidate()
    }

    private fun updateMeasurements() {
        digitWidth = 0f
        val glyphs = "0123456789-"
        paint.textAlign = Paint.Align.LEFT
        for (index in glyphs.indices) {
            val advance = paint.measureText(glyphs, index, index + 1)
            paint.getTextBounds(glyphs, index, index + 1, glyphBounds)
            val inkWidth = 2f * max(abs(glyphBounds.left - advance / 2f), abs(glyphBounds.right - advance / 2f))
            digitWidth = max(digitWidth, max(advance, inkWidth))
        }
        paint.textAlign = Paint.Align.CENTER
        paint.getFontMetrics(fontMetrics)
        lineHeight = fontMetrics.bottom - fontMetrics.top
    }

    private fun settle() {
        animator.cancel()
        progress = 1f
        oldLength = targetLength
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val columns = if (progress < 1f) max(oldLength, targetLength) else targetLength
        val desiredWidth = max(suggestedMinimumWidth, ceil(columns * digitWidth).toInt() + paddingLeft + paddingRight)
        val desiredHeight = max(suggestedMinimumHeight, ceil(lineHeight).toInt() + paddingTop + paddingBottom)
        setMeasuredDimension(
            resolveSizeAndState(desiredWidth, widthMeasureSpec, 0),
            resolveSizeAndState(desiredHeight, heightMeasureSpec, 0)
        )
    }

    override fun getBaseline(): Int = (
        paddingTop + (measuredHeight - paddingTop - paddingBottom - lineHeight) / 2f - fontMetrics.top
    ).toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val baseline = paddingTop + (height - paddingTop - paddingBottom - lineHeight) / 2f - fontMetrics.top
        val columns = if (progress < 1f) max(oldLength, targetLength) else targetLength
        val startX = paddingLeft + (width - paddingLeft - paddingRight - columns * digitWidth) / 2f
        val saved = canvas.save()
        canvas.clipRect(
            paddingLeft.toFloat(), max(paddingTop.toFloat(), baseline + fontMetrics.top),
            (width - paddingRight).toFloat(), kotlin.math.min((height - paddingBottom).toFloat(), baseline + fontMetrics.bottom)
        )
        var column = 0
        while (column < columns) {
            val oldIndex = column - (columns - oldLength)
            val targetIndex = column - (columns - targetLength)
            val x = startX + (column + 0.5f) * digitWidth
            if (progress == 1f || (oldIndex >= 0 && targetIndex >= 0 && oldCharacters[oldIndex] == targetCharacters[targetIndex])) {
                if (targetIndex >= 0) canvas.drawText(targetCharacters, targetIndex, 1, x, baseline, paint)
            } else {
                if (oldIndex >= 0) canvas.drawText(oldCharacters, oldIndex, 1, x, baseline - direction * progress * lineHeight, paint)
                if (targetIndex >= 0) canvas.drawText(targetCharacters, targetIndex, 1, x, baseline + direction * (1f - progress) * lineHeight, paint)
            }
            column++
        }
        canvas.restoreToCount(saved)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.TextView"
        info.text = targetText
        info.contentDescription = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver
        )
        observingMotion = true
        if (!NumericTextTransition.animationsEnabled(context)) settle()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (initialized && !isShown) settle()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (initialized && visibility != VISIBLE) settle()
    }

    override fun onDetachedFromWindow() {
        settle()
        if (observingMotion) {
            context.contentResolver.unregisterContentObserver(motionObserver)
            observingMotion = false
        }
        super.onDetachedFromWindow()
    }
}
