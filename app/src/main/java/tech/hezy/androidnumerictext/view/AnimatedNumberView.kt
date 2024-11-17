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

    private var currentNumber = 0
    private var targetNumber = 0
    private var animationProgress = 1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 40f
    }
    private val textBounds = Rect()

    private var animator: ValueAnimator? = null

    fun setNumber(number: Int) {
        if (number == targetNumber) return
        
        animator?.cancel()
        
        currentNumber = targetNumber
        targetNumber = number
        
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = NumericTextTransition.ANIMATION_DURATION
            interpolator = NumericTextTransition.SnappyInterpolator
            
            addUpdateListener { animation ->
                animationProgress = animation.animatedValue as Float
                invalidate()
            }
            
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f

        val currentText = currentNumber.toString()
        val targetText = targetNumber.toString()

        paint.getTextBounds(currentText, 0, currentText.length, textBounds)
        val textHeight = textBounds.height()

        canvas.save()
        canvas.translate(centerX, centerY + (textHeight * (1f - animationProgress)))
        paint.alpha = ((1f - animationProgress) * 255).toInt()
        canvas.drawText(currentText, 0f, 0f, paint)
        canvas.restore()

        canvas.save()
        canvas.translate(centerX, centerY + (textHeight * (2f - animationProgress)))
        paint.alpha = (animationProgress * 255).toInt()
        canvas.drawText(targetText, 0f, 0f, paint)
        canvas.restore()
    }
}

