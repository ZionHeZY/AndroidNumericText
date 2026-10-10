package tech.hezy.androidnumerictext

import android.os.Handler
import android.os.Looper

internal const val DEMO_NUMBER_KEY = "demo_number"
internal const val DEMO_ANIMATION_MILLIS = 500

internal class DemoReplay(
    private val onNumber: (Int) -> Unit,
    private val onRunningChanged: (Boolean) -> Unit
) : Runnable {
    private val handler = Handler(Looper.getMainLooper())
    private var step = 0

    fun start() {
        cancel()
        step = 0
        onRunningChanged(true)
        run()
    }

    fun cancel() {
        handler.removeCallbacks(this)
        onRunningChanged(false)
    }

    override fun run() {
        if (step == numbers.size) {
            onRunningChanged(false)
            return
        }
        onNumber(numbers[step++])
        handler.postDelayed(this, 900L)
    }

    private companion object {
        val numbers = intArrayOf(8, 9, 10, 99, 100, 98, 0, -1, -10, 42)
    }
}
