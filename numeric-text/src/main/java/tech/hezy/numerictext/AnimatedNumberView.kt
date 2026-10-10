package tech.hezy.numerictext

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
 * 对照 SwiftUI numericText 的逐字符位移、缩放、模糊和淡变；不变字符保持清晰，不补前导零。
 * 首次赋值立即显示。连续赋值从上次目标值切换，隐藏、移除或关闭系统动画时立即显示最新值。
 * animationDuration 为单字符基础时长；逐字符错峰最多额外增加 48%，0 表示无动画。
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
    private val advances = FloatArray(11)
    private val blurredGlyphs = arrayOfNulls<Bitmap>(11 * 8)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var blurPadding = 0f
    private var changedColumns = 0
    private var timelineScale = 1f
    private var characterStagger = 0f

    private fun glyphIndex(character: Char): Int = if (character == '-') 10 else character - '0'

    private fun textWidth(characters: CharArray, length: Int): Float {
        var width = 0f
        for (index in 0 until length) width += advances[glyphIndex(characters[index])]
        return width
    }

    // 模糊字形仅在目标/字体改变时生成；API 21 也使用真实模糊，不依赖 RenderEffect。
    private fun prepareGlyphs() {
        for (index in 0 until max(oldLength, targetLength)) {
            if (index < oldLength) prepareGlyph(oldCharacters[index])
            if (index < targetLength) prepareGlyph(targetCharacters[index])
        }
    }

    private fun prepareGlyph(character: Char) {
        if (character == '-') return
        val glyph = glyphIndex(character)
        if (blurredGlyphs[glyph * 8] != null) return
        val rasterPaint = Paint(paint).apply { color = android.graphics.Color.WHITE }
        val characters = charArrayOf(character)
        for (level in 1..8) {
            val bitmap = Bitmap.createBitmap(
                ceil(digitWidth + 2f * blurPadding).toInt(),
                ceil(lineHeight + 2f * blurPadding).toInt(), Bitmap.Config.ALPHA_8
            )
            rasterPaint.maskFilter = BlurMaskFilter(paint.textSize * 0.2f * level / 8f, BlurMaskFilter.Blur.NORMAL)
            Canvas(bitmap).drawText(characters, 0, 1, blurPadding + digitWidth / 2f, blurPadding - fontMetrics.top, rasterPaint)
            blurredGlyphs[glyph * 8 + level - 1] = bitmap
        }
    }

    private fun drawGlyph(canvas: Canvas, character: Char, x: Float, baseline: Float, visibility: Float) {
        val fade = visibility.coerceIn(0f, 1f)
        if (fade <= 0f) return
        val saved = canvas.save()
        if (character != '-') {
            val scale = 0.4f + 0.6f * fade
            canvas.scale(scale, scale, x, baseline + (fontMetrics.ascent + fontMetrics.descent) / 2f)
        }
        val level = ceil((1f - fade) * 8f).toInt().coerceIn(0, 8)
        val bitmap = if (level == 0 || character == '-') null else blurredGlyphs[glyphIndex(character) * 8 + level - 1]
        if (bitmap == null) {
            paint.alpha = (android.graphics.Color.alpha(textColor) * fade).toInt()
            val index = glyphIndex(character)
            canvas.drawText("0123456789-", index, index + 1, x, baseline, paint)
        } else {
            bitmapPaint.color = textColor
            bitmapPaint.alpha = (android.graphics.Color.alpha(textColor) * fade).toInt()
            canvas.drawBitmap(bitmap, x - digitWidth / 2f - blurPadding, baseline + fontMetrics.top - blurPadding, bitmapPaint)
        }
        canvas.restoreToCount(saved)
    }

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

    /** 支持系统字体或宿主加载的自定义字体；采用字体真实字形宽度。 */
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
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
            if (textWidth(oldCharacters, oldLength) != textWidth(targetCharacters, targetLength)) requestLayout()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                progress = 1f
                oldLength = 0
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
        changedColumns = 0
        for (index in 0 until max(oldLength, targetLength)) {
            if (index >= oldLength || index >= targetLength || oldCharacters[index] != targetCharacters[index]) changedColumns++
        }
        characterStagger = if (changedColumns > 1) minOf(0.24f, 0.48f / (changedColumns - 1)) else 0f
        timelineScale = 1f + characterStagger * (changedColumns - 1).coerceAtLeast(0)
        if (animate) {
            progress = 0f
            animator.duration = (animationDurationMillis.toDouble() * timelineScale).toLong()
            prepareGlyphs()
            animator.start()
        } else {
            progress = 1f
            oldLength = 0
        }
        requestLayout()
        invalidate()
    }

    private fun updateMeasurements() {
        digitWidth = 0f
        for (index in blurredGlyphs.indices) {
            blurredGlyphs[index]?.recycle()
            blurredGlyphs[index] = null
        }
        blurPadding = ceil(paint.textSize * 0.4f)
        val glyphs = "0123456789-"
        paint.textAlign = Paint.Align.LEFT
        for (index in glyphs.indices) {
            val advance = paint.measureText(glyphs, index, index + 1)
            advances[index] = advance
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
        oldLength = 0
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val oldWidth = textWidth(oldCharacters, oldLength)
        val targetWidth = textWidth(targetCharacters, targetLength)
        val time = progress * timelineScale
        val width = oldWidth + (targetWidth - oldWidth) * NumericTextTransition.SnappyInterpolator.getInterpolation(time)
        val desiredWidth = max(suggestedMinimumWidth, ceil(width + paint.textSize * 0.4f).toInt() + paddingLeft + paddingRight)
        val desiredHeight = max(suggestedMinimumHeight, ceil(lineHeight + paint.textSize * 0.4f).toInt() + paddingTop + paddingBottom)
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
        val baseColor = paint.color
        val baseline = paddingTop + (height - paddingTop - paddingBottom - lineHeight) / 2f - fontMetrics.top
        val oldWidth = textWidth(oldCharacters, oldLength)
        val targetWidth = textWidth(targetCharacters, targetLength)
        val time = progress * timelineScale
        val spatialProgress = NumericTextTransition.SnappyInterpolator.getInterpolation(time)
        val centerX = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val columns = max(oldLength, targetLength)
        var oldX = -oldWidth / 2f
        var targetX = -targetWidth / 2f
        var changedIndex = 0
        for (column in 0 until columns) {
            val oldIndex = if (column < oldLength) column else -1
            val targetIndex = if (column < targetLength) column else -1
            val oldAdvance = if (oldIndex >= 0) advances[glyphIndex(oldCharacters[oldIndex])] else 0f
            val targetAdvance = if (targetIndex >= 0) advances[glyphIndex(targetCharacters[targetIndex])] else 0f
            val oldCenter = centerX + oldX + oldAdvance / 2f
            val targetCenter = centerX + targetX + targetAdvance / 2f
            paint.color = baseColor
            if (progress == 1f || (oldIndex >= 0 && targetIndex >= 0 && oldCharacters[oldIndex] == targetCharacters[targetIndex])) {
                val x = oldCenter + (targetCenter - oldCenter) * spatialProgress
                if (targetIndex >= 0) canvas.drawText(targetCharacters, targetIndex, 1, x, baseline, paint)
            } else {
                val delay = changedIndex++ * characterStagger
                val phase = NumericTextTransition.SnappyInterpolator.getInterpolation((time - delay).coerceIn(0f, 1f))
                val travel = paint.textSize * 0.42f
                if (oldIndex >= 0) {
                    val sign = oldCharacters[oldIndex] == '-'
                    drawGlyph(canvas, oldCharacters[oldIndex], oldCenter, baseline - (if (sign) 0f else direction * phase * travel), 1f - phase)
                }
                paint.color = baseColor
                if (targetIndex >= 0) {
                    val sign = targetCharacters[targetIndex] == '-'
                    drawGlyph(canvas, targetCharacters[targetIndex], targetCenter, baseline + (if (sign) 0f else direction * (1f - phase) * travel), phase)
                }
            }
            oldX += oldAdvance
            targetX += targetAdvance
        }
        paint.color = baseColor
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
