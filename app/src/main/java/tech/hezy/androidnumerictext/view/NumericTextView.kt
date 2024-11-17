package tech.hezy.androidnumerictext.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import android.animation.ValueAnimator
import androidx.appcompat.widget.AppCompatTextView

class NumericTextView @JvmOverloads constructor(
    context: Context, 
    attrs: AttributeSet? = null, 
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var currentValue: Int = 0

    fun setNumber(newValue: Int) {
        val animator = ValueAnimator.ofInt(currentValue, newValue)
        animator.duration = 300
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Int
            text = animatedValue.toString()
        }
        animator.start()
        currentValue = newValue
    }
} 