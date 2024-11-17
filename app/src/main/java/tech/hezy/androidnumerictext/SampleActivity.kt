package tech.hezy.androidnumerictext

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import tech.hezy.androidnumerictext.view.AnimatedNumberView

class SampleActivity : AppCompatActivity() {
    
    private var currentNumber = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_sample_activity)
        
        val animatedNumberView = findViewById<AnimatedNumberView>(R.id.animatedNumberView)

        animatedNumberView.setNumber(currentNumber)

        findViewById<View>(R.id.buttonIncrease).setOnClickListener {
            currentNumber += 1
            animatedNumberView.setNumber(currentNumber)
        }

        findViewById<View>(R.id.buttonDecrease).setOnClickListener {
            currentNumber -= 1
            animatedNumberView.setNumber(currentNumber)
        }
    }
}