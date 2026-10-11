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
 * 首次赋值立即显示。连续赋值保留当前字形位置和相位；隐藏、移除或关闭系统动画时立即显示最新值。
 * animationDuration 为孤立更新的单字符基础时长；错峰最多增加 48%，连续更新自适应缩短且不重复错峰，0 表示无动画。
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
    private val targetCharacters = CharArray(11)
    private var targetLength = 1
    private var targetNumber = 0
    private var targetText = "0"
    private var hasNumber = false
    private var lastUpdateMillis = 0L
    private var previousIntervalMillis = Long.MAX_VALUE
    private var digitWidth = 0f
    private var lineHeight = 0f
    private var progress = 1f
    private var initialized = false
    private var observingMotion = false
    private val advances = FloatArray(11)
    private val blurredGlyphs = arrayOfNulls<Bitmap>(11 * 8)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var blurPadding = 0f
    private var timelineScale = 1f
    private var startWidth = 0f
    private val targetCenters = FloatArray(11)
    private val columnDelays = FloatArray(11)
    // 最多 11 个字符位置 × 11 种字形；复用状态，不为连续点击积累动画队列。
    private val states = arrayOfNulls<DigitLayer>(121)
    private var layers = ArrayList<DigitLayer>(22)
    private var nextLayers = ArrayList<DigitLayer>(22)

    private class DigitLayer(val column: Int, val character: Char) {
        var x = 0f
        var y = 0f
        var alpha = 1f
        var endX = 0f
        var endY = 0f
        var endAlpha = 1f
        var delay = 0f
    }

    private fun layer(column: Int, character: Char, x: Float, y: Float, alpha: Float,
        endX: Float = x, endY: Float = y, endAlpha: Float = alpha, delay: Float = 0f): DigitLayer {
        val index = column * 11 + glyphIndex(character)
        val layer = states[index] ?: DigitLayer(column, character).also { states[index] = it }
        layer.x = x
        layer.y = y
        layer.alpha = alpha
        layer.endX = endX
        layer.endY = endY
        layer.endAlpha = endAlpha
        layer.delay = delay
        return layer
    }

    private fun currentTextWidth(): Float {
        val targetWidth = textWidth(targetCharacters, targetLength)
        if (progress >= 1f) return targetWidth
        val fraction = NumericTextTransition.SnappyInterpolator.getInterpolation(progress * timelineScale)
        return startWidth + (targetWidth - startWidth) * fraction
    }

    private fun glyphIndex(character: Char): Int = if (character == '-') 10 else character - '0'

    private fun textWidth(characters: CharArray, length: Int): Float {
        var width = 0f
        for (index in 0 until length) width += advances[glyphIndex(characters[index])]
        return width
    }

    // 模糊字形仅在目标/字体改变时生成；API 21 也使用真实模糊，不依赖 RenderEffect。
    private fun prepareGlyphs() {
        for (index in layers.indices) prepareGlyph(layers[index].character)
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
            Canvas(bitmap).drawText(characters, 0, 1, blurPadding + digitWidth / 2f, blurPadding - fontMetrics.ascent, rasterPaint)
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
        val blur = ((1f - fade) * 8f).coerceIn(0f, 8f)
        val lower = blur.toInt()
        val blend = blur - lower
        val color = textColor
        val opacity = android.graphics.Color.alpha(color) / 255f * fade
        val lowerOpacity = if (character == '-') opacity else opacity * (1f - blend)
        val bitmap = if (lower == 0 || character == '-') null else blurredGlyphs[glyphIndex(character) * 8 + lower - 1]
        if (bitmap == null) {
            paint.alpha = (lowerOpacity * 255f).toInt()
            val index = glyphIndex(character)
            canvas.drawText("0123456789-", index, index + 1, x, baseline, paint)
        } else {
            bitmapPaint.color = color
            bitmapPaint.alpha = (lowerOpacity * 255f).toInt()
            canvas.drawBitmap(bitmap, x - digitWidth / 2f - blurPadding, baseline + fontMetrics.ascent - blurPadding, bitmapPaint)
        }
        if (character != '-' && blend > 0f && lower < 8) {
            val upper = blurredGlyphs[glyphIndex(character) * 8 + lower]
            if (upper != null) {
                bitmapPaint.color = color
                bitmapPaint.alpha = (opacity * blend / (1f - lowerOpacity) * 255f).toInt()
                canvas.drawBitmap(upper, x - digitWidth / 2f - blurPadding, baseline + fontMetrics.ascent - blurPadding, bitmapPaint)
            }
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
            if (startWidth != textWidth(targetCharacters, targetLength)) requestLayout()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (progress >= 1f) layers.clear()
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
        updateMeasurements()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        contentDescription = targetText
        initialized = true
    }

    /** 首次赋值和重复赋值不播放动画；必须在 UI 线程调用。 */
    fun setNumber(number: Int) {
        if (hasNumber && targetNumber == number) return
        val oldWidth = currentTextWidth()
        val time = progress * timelineScale
        val interrupted = progress < 1f
        val now = android.os.SystemClock.elapsedRealtime()
        val interval = if (lastUpdateMillis == 0L) Long.MAX_VALUE else now - lastUpdateMillis
        val duration = NumericTextTransition.durationForUpdate(animationDurationMillis, interval, previousIntervalMillis)
        previousIntervalMillis = interval
        lastUpdateMillis = if (hasNumber) now else 0L
        if (!interrupted) seedLayers(oldWidth)
        animator.cancel()
        val previousText = targetText
        val direction = if (number > targetNumber) 1f else -1f
        targetNumber = number
        targetText = number.toString()
        targetLength = targetText.length
        for (index in 0 until targetLength) targetCharacters[index] = targetText[index]
        contentDescription = targetText
        val animate = hasNumber && animationDurationMillis > 0 && isAttachedToWindow &&
            isShown && windowVisibility == VISIBLE && NumericTextTransition.animationsEnabled(context)
        hasNumber = true
        startWidth = oldWidth
        configureTargets(previousText, interrupted || duration < animationDurationMillis)
        retargetLayers(time, interrupted, direction * paint.textSize * 0.42f)
        if (animate) {
            progress = 0f
            animator.duration = (duration.toDouble() * timelineScale).toLong()
            prepareGlyphs()
            animator.start()
        } else {
            progress = 1f
            layers.clear()
        }
        requestLayout()
        invalidate()
    }

    private fun seedLayers(width: Float) {
        layers.clear()
        var x = -width / 2f
        for (index in 0 until targetLength) {
            val advance = advances[glyphIndex(targetCharacters[index])]
            layers.add(layer(index, targetCharacters[index], x + advance / 2f, 0f, 1f))
            x += advance
        }
    }

    private fun configureTargets(previousText: String, suppressStagger: Boolean) {
        var changedColumns = 0
        for (index in 0 until max(previousText.length, targetLength)) {
            if (previousText.getOrNull(index) != targetText.getOrNull(index)) changedColumns++
        }
        val stagger = if (!suppressStagger && changedColumns > 1) minOf(0.24f, 0.48f / (changedColumns - 1)) else 0f
        timelineScale = 1f + stagger * (changedColumns - 1).coerceAtLeast(0)
        var changedIndex = 0
        for (index in columnDelays.indices) {
            columnDelays[index] = if (previousText.getOrNull(index) != targetText.getOrNull(index)) {
                changedIndex++ * stagger
            } else 0f
        }
        var x = -textWidth(targetCharacters, targetLength) / 2f
        for (index in 0 until targetLength) {
            val advance = advances[glyphIndex(targetCharacters[index])]
            targetCenters[index] = x + advance / 2f
            x += advance
        }
    }

    private fun retargetLayers(time: Float, interrupted: Boolean, travel: Float) {
        nextLayers.clear()
        for (index in layers.indices) {
            val layer = layers[index]
            if (retargetLayer(layer, time, interrupted, travel)) nextLayers.add(layer)
        }
        for (column in 0 until targetLength) {
            val character = targetCharacters[column]
            if (nextLayers.none { it.column == column && it.character == character }) {
                nextLayers.add(layer(column, character, targetCenters[column],
                    if (character == '-') 0f else travel, 0f,
                    targetCenters[column], 0f, 1f, columnDelays[column]))
            }
        }
        val reusableLayers = layers
        layers = nextLayers
        nextLayers = reusableLayers
    }

    private fun retargetLayer(layer: DigitLayer, time: Float, interrupted: Boolean, travel: Float): Boolean {
        val phase = NumericTextTransition.SnappyInterpolator.getInterpolation(time - layer.delay)
        val alpha = layer.alpha + (layer.endAlpha - layer.alpha) * phase
        val incoming = layer.column < targetLength && layer.character == targetCharacters[layer.column]
        if (alpha <= 0.001f && !incoming) return false
        val endX = if (incoming) targetCenters[layer.column] else layer.endX
        val endY = if (incoming || layer.character == '-') 0f else -travel
        val endAlpha = if (incoming) 1f else 0f
        if (interrupted && layer.endX == endX && layer.endY == endY && layer.endAlpha == endAlpha) {
            layer.delay -= time
        } else {
            layer.x += (layer.endX - layer.x) * phase
            layer.y += (layer.endY - layer.y) * phase
            layer.alpha = alpha
            layer.endX = endX
            layer.endY = endY
            layer.endAlpha = endAlpha
            layer.delay = columnDelays[layer.column]
        }
        return true
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
        lineHeight = fontMetrics.descent - fontMetrics.ascent
    }

    private fun settle() {
        animator.cancel()
        progress = 1f
        layers.clear()
        nextLayers.clear()
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = currentTextWidth()
        val desiredWidth = max(suggestedMinimumWidth, ceil(width + paint.textSize * 0.4f).toInt() + paddingLeft + paddingRight)
        val desiredHeight = max(suggestedMinimumHeight, ceil(lineHeight + paint.textSize * 0.4f).toInt() + paddingTop + paddingBottom)
        setMeasuredDimension(
            resolveSizeAndState(desiredWidth, widthMeasureSpec, 0),
            resolveSizeAndState(desiredHeight, heightMeasureSpec, 0)
        )
    }

    override fun getBaseline(): Int = (
        paddingTop + (measuredHeight - paddingTop - paddingBottom - lineHeight) / 2f - fontMetrics.ascent
    ).toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val baseColor = paint.color
        val baseline = paddingTop + (height - paddingTop - paddingBottom - lineHeight) / 2f - fontMetrics.ascent
        val centerX = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        if (progress >= 1f) {
            var x = centerX - textWidth(targetCharacters, targetLength) / 2f
            for (index in 0 until targetLength) {
                val advance = advances[glyphIndex(targetCharacters[index])]
                canvas.drawText(targetCharacters, index, 1, x + advance / 2f, baseline, paint)
                x += advance
            }
        } else {
            val time = progress * timelineScale
            for (index in layers.indices) {
                val layer = layers[index]
                val phase = NumericTextTransition.SnappyInterpolator.getInterpolation(time - layer.delay)
                paint.color = baseColor
                drawGlyph(canvas, layer.character,
                    centerX + layer.x + (layer.endX - layer.x) * phase,
                    baseline + layer.y + (layer.endY - layer.y) * phase,
                    layer.alpha + (layer.endAlpha - layer.alpha) * phase)
            }
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
