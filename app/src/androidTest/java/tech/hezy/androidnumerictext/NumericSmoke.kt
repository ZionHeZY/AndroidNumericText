package tech.hezy.androidnumerictext

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import tech.hezy.numerictext.AnimatedNumberView

/** 原生设备验证：绘制结果、位数/符号边界、生命周期，以及两种真实演示界面的交互。 */
class NumericSmoke : Instrumentation() {
    private var showcase: String? = null
    override fun onCreate(arguments: Bundle?) {
        showcase = arguments?.getString("showcase")
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        var screen: Activity? = null
        val result = Bundle()
        var code = Activity.RESULT_CANCELED
        try {
            if (showcase?.contains("-reference") == true) {
                val mode = checkNotNull(showcase)
                screen = launch(if (mode.startsWith("views")) SampleActivity::class.java else MainActivity::class.java, 129)
                waitForIdleSync()
                SystemClock.sleep(1000)
                val frames = mode.endsWith("-frames")
                val directory = File(checkNotNull(targetContext.getExternalFilesDir(null)), "numeric-proof/$mode").apply { mkdirs() }
                val bitmap = if (frames) Bitmap.createBitmap(540, 260, Bitmap.Config.ARGB_8888) else null
                val frameCanvas = bitmap?.let(::Canvas)
                var frameIndex = 0
                val timestamps = StringBuilder("frame,target,elapsed_ms\n")
                for (value in intArrayOf(130, 139, 140, 99, 100, 98, 0, -1, -10, 42)) {
                    runOnMainSync {
                        val activity = checkNotNull(screen)
                        if (activity is SampleActivity) activity.findViewById<AnimatedNumberView>(R.id.animatedNumberView).setNumber(value)
                        else callActivityOnNewIntent(activity, Intent(targetContext, MainActivity::class.java).putExtra("demo_number", value))
                    }
                    val started = SystemClock.uptimeMillis()
                    if (bitmap == null) SystemClock.sleep(1000) else repeat(15) {
                        runOnMainSync {
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            val canvas = checkNotNull(frameCanvas)
                            val saved = canvas.save()
                            canvas.scale(0.5f, 0.5f)
                            canvas.translate(0f, -650f)
                            checkNotNull(screen).window.decorView.draw(canvas)
                            canvas.restoreToCount(saved)
                        }
                        timestamps.append(frameIndex).append(',').append(value).append(',').append(SystemClock.uptimeMillis() - started).append('\n')
                        File(directory, "%04d.png".format(frameIndex++)).outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        SystemClock.sleep(66)
                    }
                }
                bitmap?.recycle()
                if (frames) File(directory, "timestamps.csv").writeText(timestamps.toString())
                capture("$mode-final")
                result.putString("stream", "PASS: $mode native comparison sequence reaches 42\n")
            } else if (showcase != null) {
                val mode = checkNotNull(showcase)
                screen = launch(if (mode == "views") SampleActivity::class.java else MainActivity::class.java)
                waitForIdleSync()
                SystemClock.sleep(600)
                capture("$mode-idle")
                activate(targetContext.getString(R.string.run_demo))
                await("Replay start") { hasText("8") }
                SystemClock.sleep(9600)
                await("Replay end") { hasText("42") && hasText(targetContext.getString(R.string.run_demo)) }
                capture("$mode-final")
                result.putString("stream", "PASS: $mode showcase replay reaches 42\n")
            } else {
                screen = launch(SampleActivity::class.java)
                val native = checkNotNull(screen).findViewById<AnimatedNumberView>(R.id.animatedNumberView)
                verifyNative(native)
                activate(targetContext.getString(R.string.reset))
                activate(targetContext.getString(R.string.increase_description))
                await("View button") { hasText("1") }
                val monitor = addMonitor(SampleActivity::class.java.name, null, false)
                runOnMainSync { checkNotNull(screen).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                screen = checkNotNull(waitForMonitorWithTimeout(monitor, 5000)) { "View activity did not recreate" }
                removeMonitor(monitor)
                await("View rotation retains value") { hasText("1") }
                activate(targetContext.getString(R.string.run_demo))
                await("View replay starts") { hasText(targetContext.getString(R.string.stop_demo)) }
                activate(targetContext.getString(R.string.increase_description))
                val stopped = onMain { checkNotNull(screen).findViewById<AnimatedNumberView>(R.id.animatedNumberView).number }
                SystemClock.sleep(1150)
                check(onMain { checkNotNull(screen).findViewById<AnimatedNumberView>(R.id.animatedNumberView).number } == stopped) { "Manual input did not cancel View replay" }
                runOnMainSync { checkNotNull(screen).finish() }
                screen = launch(MainActivity::class.java, 8)
                if (android.animation.ValueAnimator.areAnimatorsEnabled()) verifyComposeCarry(checkNotNull(screen))
                activate(targetContext.getString(R.string.increase_description))
                await("Compose increment") { hasText("9") }
                activate(targetContext.getString(R.string.increase_description))
                await("Compose rollover") { hasText("10") }
                activate(targetContext.getString(R.string.decrease_description))
                await("Compose decrement") { hasText("9") }
                activate(targetContext.getString(R.string.reset))
                await("Compose reset") { hasText("0") }
                activate(targetContext.getString(R.string.run_demo))
                await("Compose replay start") { hasText("8") }
                activate(targetContext.getString(R.string.reset))
                SystemClock.sleep(1150)
                awaitValue("0")
                capture("compose-verified")
                result.putString("stream", "PASS: signed integer rendering, wrap-content shrink/grow, padding, rapid updates, zero duration, hidden/detached settling, View rotation/replay cancellation, Compose increment/decrement/reset/replay cancellation\n")
            }
            code = Activity.RESULT_OK
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
        } finally {
            screen?.let { runOnMainSync { it.finish() } }
        }
        finish(code, result)
    }

    private fun verifyComposeCarry(screen: Activity) {
        fun update(value: Int) = runOnMainSync {
            callActivityOnNewIntent(screen, Intent(targetContext, MainActivity::class.java).putExtra("demo_number", value))
        }
        update(129)
        awaitValue("129")
        SystemClock.sleep(850)
        val bounds = android.graphics.Rect()
        checkNotNull(nodes(uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString() == "129" }).getBoundsInScreen(bounds)
        fun render(): Bitmap = onMain {
            Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
                screen.window.decorView.draw(canvas)
            }
        }
        val before = render()
        update(130)
        SystemClock.sleep(200)
        val moving = render()
        try {
            check(!before.sameAs(moving)) { "Compose changed digits did not animate" }
            for (y in 0 until before.height) for (x in 0 until before.width / 3) {
                val pixel = before.getPixel(x, y)
                if (android.graphics.Color.red(pixel) < 90 && android.graphics.Color.green(pixel) < 90 &&
                    android.graphics.Color.blue(pixel) < 90 && android.graphics.Color.alpha(pixel) > 240) {
                    check(moving.getPixel(x, y) == pixel) { "Compose unchanged leading digit moved or changed rasterization" }
                }
            }
        } finally { before.recycle(); moving.recycle() }
        update(8)
        awaitValue("8")
    }

    private fun launch(type: Class<out Activity>, number: Int = 0): Activity = startActivitySync(
        Intent(targetContext, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_number", number)
    )

    private fun verifyNative(view: AnimatedNumberView) {
        fun expect(value: Int, label: String) {
            SystemClock.sleep((view.animationDurationMillis.toLong() * 1.5).toLong() + 100)
            waitForIdleSync()
            val images = onMain {
                val reference = AnimatedNumberView(targetContext).apply {
                    textSizePixels = view.textSizePixels
                    textColor = view.textColor
                    typeface = view.typeface
                    setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
                    setNumber(value)
                    measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                    layout(0, 0, measuredWidth, measuredHeight)
                }
                image(view) to image(reference)
            }
            try { check(images.first.sameAs(images.second)) { "$label rendered wrong digits or dimensions for $value" } }
            finally { images.first.recycle(); images.second.recycle() }
        }
        // 初始零也必须完整绘制，不能依赖首次动画才出现。
        expect(0, "Initial value")
        for (value in intArrayOf(9, 10, 100, 99, -1, Int.MIN_VALUE, Int.MAX_VALUE)) {
            runOnMainSync { view.setNumber(value) }
            expect(value, "Rollover")
        }
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            runOnMainSync { view.setNumber(129) }
            expect(129, "Carry start")
            val before = onMain { image(view) }
            runOnMainSync { view.setNumber(130) }
            SystemClock.sleep(120)
            val moving = onMain { image(view) }
            try {
                check(!before.sameAs(moving)) { "Changed digits did not animate" }
                for (y in 0 until before.height) for (x in 0 until before.width / 3) {
                    val pixel = before.getPixel(x, y)
                    if (android.graphics.Color.alpha(pixel) > 240) {
                        check(moving.getPixel(x, y) == pixel) { "Unchanged leading digit moved or faded" }
                    }
                }
            } finally { before.recycle(); moving.recycle() }
            expect(130, "Carry end")
        }
        runOnMainSync { view.setPadding(12, 8, 24, 16); view.setNumber(-10) }
        expect(-10, "Padding")
        runOnMainSync { view.setNumber(9); view.setNumber(100); view.setNumber(-10); view.setNumber(42) }
        expect(42, "Interrupted animation")
        runOnMainSync {
            view.typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD_ITALIC)
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 48f)
            view.textColor = android.graphics.Color.argb(128, 20, 70, 160)
            view.setNumber(-10)
        }
        expect(-10, "Custom font, size and translucent color")
        val styled = onMain { image(view) }
        try {
            val pixels = IntArray(styled.width * styled.height)
            styled.getPixels(pixels, 0, styled.width, 0, 0, styled.width, styled.height)
            check(pixels.maxOf { android.graphics.Color.alpha(it) } == 128) { "Text color alpha was overwritten" }
        } finally { styled.recycle() }
        capture("views-styled")
        runOnMainSync { view.animationDurationMillis = 0; view.setNumber(-99) }
        expect(-99, "Zero duration")
        runOnMainSync { view.animationDurationMillis = 240; view.setNumber(100); view.visibility = View.INVISIBLE }
        expect(100, "Hidden animation")
        val parent = onMain { view.parent as ViewGroup }
        val index = onMain { parent.indexOfChild(view) }
        val params = onMain { view.layoutParams }
        runOnMainSync { view.visibility = View.VISIBLE; view.setNumber(-1); parent.removeView(view) }
        runOnMainSync {
            view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        expect(-1, "Detached animation")
        runOnMainSync { parent.addView(view, index, params) }
        waitForIdleSync()
    }

    private fun image(view: View): Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
        view.draw(Canvas(it))
    }

    private fun <T> onMain(action: () -> T): T {
        val result = java.util.concurrent.FutureTask(java.util.concurrent.Callable(action))
        runOnMainSync(result)
        return result.get()
    }

    private fun await(label: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < end) { "Timeout: $label\n${describe(uiAutomation.rootInActiveWindow)}" }
            SystemClock.sleep(30)
        }
    }

    private fun awaitValue(text: String) {
        await("Visible number: $text") {
            val node = nodes(uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString() == text }
            if (node?.isVisibleToUser == true) true else {
                node?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                false
            }
        }
    }

    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (node == null) return emptyList()
        return listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    }

    private fun hasText(text: String): Boolean = nodes(uiAutomation.rootInActiveWindow).any {
        it.isVisibleToUser && it.text?.toString() == text
    }

    private fun activate(label: String) {
        await("Accessible control: $label") {
            val all = nodes(uiAutomation.rootInActiveWindow)
            val target = all.firstOrNull { it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label) }
            if (target == null) {
                all.lastOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                false
            } else {
                var click: AccessibilityNodeInfo? = target
                while (click != null && !click.isClickable) click = click.parent
                click?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
        }
    }

    private fun describe(node: AccessibilityNodeInfo?): String = nodes(node).joinToString("\n") {
        "${it.className}: ${it.text} / ${it.contentDescription}, visible=${it.isVisibleToUser}"
    }

    private fun capture(name: String) {
        val image = checkNotNull(uiAutomation.takeScreenshot())
        val directory = File(checkNotNull(targetContext.getExternalFilesDir(null)), "numeric-proof").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
