package tech.hezy.androidnumerictext

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import tech.hezy.numerictext.AnimatedNumberView

class SampleActivity : AppCompatActivity() {
    private var currentNumber = 0
    private lateinit var animatedNumberView: AnimatedNumberView
    private lateinit var increaseButton: Button
    private lateinit var decreaseButton: Button
    private lateinit var replayButton: Button
    private var running = false
    private val replay = DemoReplay(::showNumber) {
        running = it
        replayButton.setText(if (it) R.string.stop_demo else R.string.run_demo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val background = getColor(R.color.demo_background)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(background, background),
            navigationBarStyle = SystemBarStyle.light(background, background)
        )
        setContentView(R.layout.layout_sample_activity)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.demoTitle), true)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.demoScroll)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        animatedNumberView = findViewById(R.id.animatedNumberView)
        increaseButton = findViewById(R.id.buttonIncrease)
        decreaseButton = findViewById(R.id.buttonDecrease)
        replayButton = findViewById(R.id.buttonReplay)
        showNumber(savedInstanceState?.getInt(DEMO_NUMBER_KEY) ?: intent.getIntExtra(DEMO_NUMBER_KEY, 0))

        increaseButton.setOnClickListener {
            changeNumber((currentNumber.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        decreaseButton.setOnClickListener {
            changeNumber((currentNumber.toLong() - 1).coerceAtLeast(Int.MIN_VALUE.toLong()).toInt())
        }
        findViewById<Button>(R.id.buttonReset).setOnClickListener { changeNumber(0) }
        replayButton.setOnClickListener { if (running) replay.cancel() else replay.start() }
        findViewById<Button>(R.id.buttonSwitchMode).setOnClickListener {
            replay.cancel()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(DEMO_NUMBER_KEY, currentNumber)
            )
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(DEMO_NUMBER_KEY, currentNumber)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        replay.cancel()
        super.onStop()
    }

    private fun changeNumber(value: Int) {
        replay.cancel()
        showNumber(value)
    }

    private fun showNumber(value: Int) {
        currentNumber = value
        animatedNumberView.setNumber(value)
        increaseButton.isEnabled = value != Int.MAX_VALUE
        decreaseButton.isEnabled = value != Int.MIN_VALUE
    }
}
