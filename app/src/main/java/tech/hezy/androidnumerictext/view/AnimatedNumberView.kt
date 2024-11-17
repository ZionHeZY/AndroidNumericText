package tech.hezy.androidnumerictext.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import tech.hezy.androidnumerictext.animation.NumericTextTransition

class AnimatedNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Digit(
        val oldValue: Char,
        val newValue: Char,
        var progress: Float = 0f,
        var animator: ValueAnimator? = null
    )

    private val digits = mutableListOf<Digit>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 40f
    }
    private val textBounds = Rect()
    private var digitWidth = 0f

    init {
        paint.getTextBounds("0", 0, 1, textBounds)
        digitWidth = paint.measureText("0")
    }

    fun setNumber(number: Int) {
        val newText = number.toString()
        val oldText = if (digits.isEmpty()) {
            "0".padStart(newText.length, '0')
        } else {
            digits.map { it.newValue }.joinToString("")
        }

        digits.forEach { it.animator?.cancel() }
        digits.clear()

        val paddedOldText = oldText.padStart(newText.length, '0')
        val paddedNewText = newText.padStart(oldText.length, '0')

        paddedOldText.zip(paddedNewText).forEach { (old, new) ->
            if (old != new) {
                val digit = Digit(old, new)
                digit.animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = NumericTextTransition.ANIMATION_DURATION
                    interpolator = NumericTextTransition.SnappyInterpolator
                    addUpdateListener { animation ->
                        digit.progress = animation.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
                digits.add(digit)
            } else {
                digits.add(Digit(old, new, 1f))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (digits.isEmpty()) return

        val centerY = height / 2f
        paint.getTextBounds("0", 0, 1, textBounds)
        val textHeight = textBounds.height()

        val totalWidth = digitWidth * digits.size
        var startX = (width - totalWidth) / 2f + digitWidth / 2f

        digits.forEach { digit ->
            canvas.save()
            canvas.translate(startX, centerY + (textHeight * (1f - digit.progress)))
            paint.alpha = ((1f - digit.progress) * 255).toInt()
            canvas.drawText(digit.oldValue.toString(), 0f, 0f, paint)
            canvas.restore()

            canvas.save()
            canvas.translate(startX, centerY + (textHeight * (2f - digit.progress)))
            paint.alpha = (digit.progress * 255).toInt()
            canvas.drawText(digit.newValue.toString(), 0f, 0f, paint)
            canvas.restore()

            startX += digitWidth
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        digits.forEach { it.animator?.cancel() }
    }
}

