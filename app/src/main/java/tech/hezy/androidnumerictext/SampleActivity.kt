package tech.hezy.androidnumerictext

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import tech.hezy.androidnumerictext.view.AnimatedNumberView

class SampleActivity : AppCompatActivity() {
    private lateinit var animatedNumberTextView: AnimatedNumberView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_sample_activity)

        animatedNumberTextView = findViewById(R.id.animatedNumberTextView)

        findViewById<Button>(R.id.increaseButton).setOnClickListener {
            animatedNumberTextView.incrementNumber()
        }

        findViewById<Button>(R.id.decreaseButton).setOnClickListener {
            animatedNumberTextView.decrementNumber()
        }
    }
}