package tech.hezy.androidnumerictext.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class AnimatedNumberView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var number: Int = 0
    private var animatedValues: MutableList<Float> = mutableListOf()
    private var direction: Int = 1
    private val paint = Paint().apply {
        textSize = 100f
        isAntiAlias = true
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val text = number.toString()
        val textWidth = paint.measureText(text)
        val x = (width - textWidth) / 2
        val y = (height / 2 - (paint.descent() + paint.ascent()) / 2)

        for (i in text.indices) {
            val char = text[i]
            val offset = i * paint.measureText("0")
            val animatedValue = if (i < animatedValues.size) animatedValues[i] else 0f
            canvas.drawText(char.toString(), x + offset, y - animatedValue * height * direction, paint)
        }

        val nextNumber = number + direction
        val nextText = nextNumber.toString()
        for (i in nextText.indices) {
            val char = nextText[i]
            val offset = i * paint.measureText("0")
            val animatedValue = if (i < animatedValues.size) animatedValues[i] else 0f
            canvas.drawText(char.toString(), x + offset, y + (1 - animatedValue) * height * direction, paint)
        }
    }

    fun incrementNumber() {
        direction = 1
        animateNumberChange()
    }

    fun decrementNumber() {
        direction = -1
        animateNumberChange()
    }

    private fun animateNumberChange() {
        val text = number.toString()
        animatedValues = MutableList(text.length) { 0f }
        val animators = text.indices.map { index ->
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 500
                startDelay = index * 100L
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animation ->
                    animatedValues[index] = animation.animatedValue as Float
                    invalidate()
                }
            }
        }

        animators.forEach { animator ->
            animator.start()
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator == animators.last()) {
                        number += direction
                        animatedValues = mutableListOf()
                        invalidate()
                    }
                }
            })
        }
    }
}

