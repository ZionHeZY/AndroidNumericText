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
import java.io.DataOutputStream
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
            if (showcase == "performance-baseline" || showcase == "performance-after") {
                val proof = PerformanceProof(checkNotNull(showcase))
                screen = launch(SampleActivity::class.java, 129)
                verifyPerformanceView(checkNotNull(screen), proof)
                runOnMainSync { checkNotNull(screen).finish() }
                screen = launch(MainActivity::class.java, 129)
                verifyPerformanceCompose(checkNotNull(screen), proof)
                runOnMainSync { checkNotNull(screen).finish() }
                screen = launch(MainActivity::class.java, 129)
                // These exercise the live Compose Canvas, not a reconstruction of its draw loop.
                verifyComposeCarry(checkNotNull(screen))
                verifyComposeStyle(checkNotNull(screen))
                captureGfx(checkNotNull(showcase))
                result.putString("stream", proof.finish())
            } else if (showcase == "interruption") {
                screen = launch(SampleActivity::class.java, 129)
                verifyInterrupted(checkNotNull(screen).findViewById(R.id.animatedNumberView))
                result.putString("stream", "PASS: interrupted View animation preserves the visible frame\n")
            } else if (showcase == "styled") {
                screen = launch(MainActivity::class.java, 129)
                verifyComposeStyle(checkNotNull(screen))
                result.putString("stream", "PASS: interrupted Compose gradient/shadow typography matches immediate text\n")
            } else if (showcase?.endsWith("-rapid") == true) {
                val mode = checkNotNull(showcase)
                screen = launch(if (mode.startsWith("views")) SampleActivity::class.java else MainActivity::class.java, 129)
                awaitValue("129")
                SystemClock.sleep(900)
                val increase = checkNotNull(nodes(uiAutomation.rootInActiveWindow).firstOrNull {
                    it.contentDescription?.toString() == targetContext.getString(R.string.increase_description)
                })
                val decrease = checkNotNull(nodes(uiAutomation.rootInActiveWindow).firstOrNull {
                    it.contentDescription?.toString() == targetContext.getString(R.string.decrease_description)
                })
                val buttons = listOf(increase, decrease).map { node ->
                    var button = node
                    while (!button.isClickable) button = checkNotNull(button.parent)
                    button
                }
                for ((index, button) in buttons.withIndex()) {
                    repeat(12) {
                        check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Rapid click rejected" }
                        SystemClock.sleep(80)
                    }
                    val value = if (index == 0) "141" else "129"
                    awaitValue(value)
                    SystemClock.sleep(300)
                    verifyRapidSettling(value)
                    if (index == 0) capture("$mode-incremented")
                }
                repeat(8) {
                    for (button in buttons) {
                        check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Rapid reversal rejected" }
                        SystemClock.sleep(60)
                    }
                }
                SystemClock.sleep(300)
                awaitValue("129")
                verifyRapidSettling("129")
                capture("$mode-final")
                result.putString("stream", "PASS: $mode handles 12 increments, 12 decrements and 8 rapid reversals\n")
            } else if (showcase?.contains("-reference") == true) {
                val mode = checkNotNull(showcase)
                screen = launch(if (mode.startsWith("views")) SampleActivity::class.java else MainActivity::class.java, 129)
                waitForIdleSync()
                awaitValue("129")
                SystemClock.sleep(1200)
                val values = intArrayOf(130, 139, 140, 99, 100, 98, 0, -1, -10, 42)
                val started = SystemClock.uptimeMillis()
                for (index in values.indices) {
                    val value = values[index]
                    SystemClock.sleep((started + index * 1200L - SystemClock.uptimeMillis()).coerceAtLeast(0))
                    runOnMainSync {
                        val activity = checkNotNull(screen)
                        if (activity is SampleActivity) activity.findViewById<AnimatedNumberView>(R.id.animatedNumberView).setNumber(value)
                        else callActivityOnNewIntent(activity, Intent(targetContext, MainActivity::class.java).putExtra("demo_number", value))
                    }
                }
                SystemClock.sleep((started + values.size * 1200L - SystemClock.uptimeMillis()).coerceAtLeast(0))
                capture("$mode-final")
                captureGfx(mode)
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
                verifyComposeStyle(checkNotNull(screen))
                result.putString("stream", "PASS: signed integer rendering, wrap-content shrink/grow, padding, rapid updates, zero duration, hidden/detached settling, View rotation/replay cancellation, Compose increment/decrement/reset/replay cancellation and interrupted gradient/shadow typography\n")
            }
            code = Activity.RESULT_OK
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
        } finally {
            screen?.let { runOnMainSync { it.finish() } }
        }
        finish(code, result)
    }

    private val proofPhases = floatArrayOf(0f, .1f, .25f, .5f, .75f, 1f)
    private val proofPairs = listOf(
        129 to 130, 99 to 100, 100 to 99, 0 to -1, -1 to 0, -9 to -10,
        Int.MIN_VALUE to Int.MAX_VALUE, Int.MAX_VALUE to Int.MIN_VALUE, 987654321 to 123456780
    )
    private val atlasMeasure by lazy {
        Class.forName("tech.hezy.numerictext.NumberGlyphAtlas").declaredMethods.single { it.name == "measure" }
            .apply { isAccessible = true }
    }

    /** Reflection stays in instrumentation; production exposes no test-only hooks. */
    private fun field(owner: Any, name: String): java.lang.reflect.Field =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun property(owner: Any, name: String): Any = checkNotNull(field(owner, name).get(owner))
    private fun bits(value: Any): String = when (value) {
        is Float -> value.toRawBits().toString()
        is Double -> value.toRawBits().toString()
        else -> value.toString()
    }

    private fun captureGfx(mode: String) {
        val directory = File(checkNotNull(targetContext.getExternalFilesDir(null)), "numeric-proof").apply { mkdirs() }
        val descriptor = uiAutomation.executeShellCommand("dumpsys gfxinfo ${targetContext.packageName} framestats")
        val text = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        File(directory, "$mode-gfx.txt").writeText(text)
    }

    private inner class PerformanceProof(val mode: String) {
        val directory = File(checkNotNull(targetContext.getExternalFilesDir(null)), "numeric-proof/$mode").apply {
            check(mkdirs() || isDirectory)
        }
        private val baseline = File(directory.parentFile, "performance-baseline")
        private val records = ArrayList<String>()
        private val timings = StringBuilder()
        private var pixels = 0L
        private var rasters = 0
        private var frames = 0

        fun record(name: String, bytes: ByteArray) {
            check(name !in records) { "Duplicate fidelity artifact $name" }
            records.add(name)
            File(directory, name).writeBytes(bytes)
            if (mode == "performance-after") {
                val expected = File(baseline, name)
                check(expected.isFile) { "Missing baseline $expected; run performance-baseline first" }
                val original = expected.readBytes()
                check(bytes.contentEquals(original)) {
                    val first = (0 until minOf(bytes.size, original.size)).firstOrNull { bytes[it] != original[it] }
                    "Exact fidelity mismatch $name: bytes ${original.size} -> ${bytes.size}, first difference=$first"
                }
            }
        }

        fun metadata(name: String, text: String) = record("$name.txt", text.toByteArray(Charsets.UTF_8))

        fun bitmap(name: String, bitmap: Bitmap, frame: Boolean = false) {
            val argb = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val bytes = java.io.ByteArrayOutputStream(8 + argb.size * 4)
            DataOutputStream(bytes).use { output ->
                output.writeInt(bitmap.width)
                output.writeInt(bitmap.height)
                for (pixel in argb) output.writeInt(pixel)
            }
            record("$name.argb", bytes.toByteArray())
            pixels += argb.size
            rasters++
            if (frame) {
                frames++
                File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }

        fun timing(label: String, samples: LongArray) {
            samples.sort()
            fun percentile(p: Double) = samples[(kotlin.math.ceil(samples.size * p).toInt() - 1).coerceAtLeast(0)] / 1000.0
            val line = "$label n=${samples.size} p50_us=${percentile(.5)} p95_us=${percentile(.95)}\n"
            timings.append(line)
            android.util.Log.i("NumericProof", line.trim())
        }

        fun finish(): String {
            record("manifest.txt", records.sorted().joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
            val summary = "PASS: $mode exact dimensions/ARGB and float-bit metadata; " +
                "view_frames=$frames rasters=$rasters pixels=$pixels artifacts=${records.size}\n" +
                "Compose fidelity is atlas rasters + real transition snapshots, NOT deterministic live Compose frame capture. " +
                "Live Compose carry and interrupted gradient/shadow smoke also passed.\n" +
                timings + "Artifacts: ${directory.absolutePath}\n"
            File(directory, "timings.txt").writeText(summary)
            return summary
        }
    }

    private fun configureProofView(view: AnimatedNumberView, styled: Boolean) {
        view.textSizePixels = 64f
        view.typeface = if (styled) android.graphics.Typeface.create("serif", android.graphics.Typeface.ITALIC)
            else android.graphics.Typeface.MONOSPACE
        view.textColor = if (styled) 0x992046A0.toInt() else android.graphics.Color.BLACK
        view.setPadding(7, 5, 11, 9)
    }

    private fun layoutProofView(view: AnimatedNumberView, sizing: String = "wrap") {
        val width = when (sizing) {
            "exact" -> View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)
            "at-most" -> View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.AT_MOST)
            else -> View.MeasureSpec.UNSPECIFIED
        }
        // Reflection changes progress without an animator callback/requestLayout.
        view.forceLayout()
        view.measure(width, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun resetProofView(view: AnimatedNumberView, value: Int) {
        view.animationDurationMillis = 0
        view.setNumber(value)
        view.animationDurationMillis = 500
        layoutProofView(view)
    }

    private fun updateProofView(view: AnimatedNumberView, value: Int) {
        // Neutralize wall-clock typing cadence, while keeping the real retarget/prepare/animator path.
        field(view, "lastUpdateMillis").setLong(view, 0L)
        field(view, "previousIntervalMillis").setLong(view, Long.MAX_VALUE)
        view.setNumber(value)
        val animator = property(view, "animator") as android.animation.ValueAnimator
        check(animator.isStarted) { "View did not enter the actual animation path for $value" }
        animator.pause()
    }

    private fun viewFrame(view: AnimatedNumberView, phase: Float, sizing: String = "wrap"): Bitmap {
        field(view, "progress").setFloat(view, phase)
        layoutProofView(view, sizing)
        return image(view)
    }

    private fun verifyPerformanceView(screen: Activity, proof: PerformanceProof) {
        check(android.animation.ValueAnimator.areAnimatorsEnabled()) { "Enable system animations for performance proof" }
        for (styled in listOf(false, true)) {
            val label = if (styled) "view-italic-alpha" else "view-monospace"
            val container = onMain { android.widget.FrameLayout(screen).also { screen.setContentView(it) } }
            val view = onMain {
                AnimatedNumberView(screen).also { configureProofView(it, styled); container.addView(it) }
            }
            await("$label attached") { onMain { view.isShown && view.isAttachedToWindow } }
            for ((index, pair) in proofPairs.withIndex()) {
                onMain {
                    resetProofView(view, pair.first)
                    updateProofView(view, pair.second)
                    for (sizing in listOf("wrap", "at-most", "exact")) for (phase in proofPhases) {
                        val bitmap = viewFrame(view, phase, sizing)
                        try { proof.bitmap("$label-$index-$sizing-${bits(phase)}", bitmap, frame = true) }
                        finally { bitmap.recycle() }
                    }
                }
            }
            onMain {
                resetProofView(view, 129)
                updateProofView(view, 130)
                field(view, "progress").setFloat(view, .2f)
                updateProofView(view, 128)
                for (sizing in listOf("wrap", "at-most", "exact")) for (phase in proofPhases) {
                    val bitmap = viewFrame(view, phase, sizing)
                    try { proof.bitmap("$label-reversal-$sizing-${bits(phase)}", bitmap, frame = true) }
                    finally { bitmap.recycle() }
                }
                // Warm every digit before measuring cache-hit preparation.
                resetProofView(view, Int.MIN_VALUE)
                updateProofView(view, Int.MAX_VALUE)
                resetProofView(view, 129)
                repeat(12) { updateProofView(view, if (it % 2 == 0) 130 else 129) }
                val lastUpdate = field(view, "lastUpdateMillis")
                val previousInterval = field(view, "previousIntervalMillis")
                val animator = property(view, "animator") as android.animation.ValueAnimator
                val hot = LongArray(160) { index ->
                    lastUpdate.setLong(view, 0L)
                    previousInterval.setLong(view, Long.MAX_VALUE)
                    val start = System.nanoTime()
                    view.setNumber(if (index % 2 == 0) 130 else 129)
                    val elapsed = System.nanoTime() - start
                    animator.pause()
                    elapsed
                }
                proof.timing("$label real-setNumber-hot", hot)
                field(view, "progress").setFloat(view, .5f)
                layoutProofView(view)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    repeat(30) { bitmap.eraseColor(0); view.draw(canvas) }
                    val draw = LongArray(400) {
                        bitmap.eraseColor(0)
                        val start = System.nanoTime()
                        view.draw(canvas)
                        System.nanoTime() - start
                    }
                    proof.timing("$label real-View.draw-software", draw)
                } finally { bitmap.recycle() }
                val cold = LongArray(40) {
                    val start = System.nanoTime()
                    val fresh = AnimatedNumberView(screen)
                    configureProofView(fresh, styled)
                    fresh.setNumber(129)
                    container.addView(fresh)
                    layoutProofView(fresh)
                    updateProofView(fresh, 130)
                    val elapsed = System.nanoTime() - start
                    container.removeView(fresh)
                    elapsed
                }
                proof.timing("$label construct-style-attach-prepare-cold", cold)
            }
        }
    }

    private suspend fun measureProofAtlas(atlas: Any, request: Any): Any {
        val layouts = ArrayList<Any>(1)
        val callback: (Any) -> Unit = { layouts.add(it) }
        suspendCoroutine<Any?> { continuation ->
            try {
                val result = atlasMeasure.invoke(atlas, request, callback, continuation)
                if (result !== COROUTINE_SUSPENDED) continuation.resume(result)
            } catch (failure: Throwable) {
                continuation.resumeWithException(if (failure is InvocationTargetException) failure.targetException else failure)
            }
        }
        check(layouts.size == 1) { "Expected one real prepared layout, got ${layouts.size}" }
        val layout = layouts.single()
        check(property(layout, "number") == property(request, "number"))
        check(property(layout, "requestedAtMillis") == property(request, "requestedAtMillis")) { "Cached layout reused an old request timestamp" }
        check(property(layout, "durationMillis") == property(request, "durationMillis")) { "Cached layout reused an old request duration" }
        return layout
    }

    private fun proofRequest(number: Int, ordinal: Int = 0, duration: Int = 500): Any =
        Class.forName("tech.hezy.numerictext.NumberRequest").declaredConstructors.single().apply { isAccessible = true }
            .newInstance(number, SystemClock.elapsedRealtime() + ordinal, duration)

    private fun captureProofLayout(proof: PerformanceProof, name: String, layout: Any) {
        val textLayout = property(layout, "textLayout") as androidx.compose.ui.text.TextLayoutResult
        val metadata = StringBuilder()
        for (key in listOf("number", "width", "height", "baseline")) metadata.append("$key=${bits(property(layout, key))}\n")
        val style = textLayout.layoutInput.style
        val family = when (style.fontFamily) {
            androidx.compose.ui.text.font.FontFamily.Monospace -> "monospace"
            androidx.compose.ui.text.font.FontFamily.Serif -> "serif"
            else -> style.fontFamily?.javaClass?.name
        }
        metadata.append("text=${textLayout.layoutInput.text.text}\n")
        metadata.append("font=$family,${style.fontStyle},${style.fontWeight?.weight},${bits(style.fontSize.value)},${style.fontSize.type}\n")
        metadata.append("color=${style.color.value},background=${style.background.value},brush=${style.brush?.javaClass?.name}\n")
        metadata.append("direction=${textLayout.layoutInput.layoutDirection},align=${style.textAlign}\n")
        style.shadow?.let {
            metadata.append("shadow=${it.color.value},${bits(it.offset.x)},${bits(it.offset.y)},${bits(it.blurRadius)}\n")
        }
        metadata.append("size=${textLayout.size.width},${textLayout.size.height}\n")
        metadata.append("baselines=${bits(textLayout.firstBaseline)},${bits(textLayout.lastBaseline)}\n")
        for (index in textLayout.layoutInput.text.indices) {
            val box = textLayout.getBoundingBox(index)
            metadata.append("box$index=${bits(box.left)},${bits(box.top)},${bits(box.right)},${bits(box.bottom)}\n")
        }
        val digits = property(layout, "digits") as List<*>
        for ((index, raw) in digits.withIndex()) {
            val digit = checkNotNull(raw)
            for (key in listOf("place", "char", "x", "y", "alpha", "blur", "sourceLeft", "sourceRight"))
                metadata.append("digit$index.$key=${bits(property(digit, key))}\n")
            val glyph = property(digit, "glyph")
            metadata.append("digit$index.center=${bits(property(glyph, "center"))}\n")
            metadata.append("digit$index.baseline=${bits(property(glyph, "baseline"))}\n")
            for ((level, rawRaster) in (property(glyph, "rasters") as Array<*>).withIndex()) {
                val raster = checkNotNull(rawRaster)
                metadata.append("digit$index.raster$level=${bits(property(raster, "x"))},${bits(property(raster, "y"))}\n")
                proof.bitmap("$name-digit$index-blur$level", property(raster, "bitmap") as Bitmap)
            }
        }
        proof.metadata(name, metadata.toString())
    }

    private fun captureProofTransition(proof: PerformanceProof, name: String, transition: Any) {
        val width = transition.javaClass.getDeclaredMethod("widthFraction", Float::class.javaPrimitiveType).apply { isAccessible = true }
        val layers = property(transition, "layers") as List<*>
        for (phase in proofPhases) {
            val metadata = StringBuilder("startWidth=${bits(property(transition, "startWidth"))}\nwidthFraction=${bits(checkNotNull(width.invoke(transition, phase)))}\n")
            for ((index, raw) in layers.withIndex()) {
                val layer = checkNotNull(raw)
                val snapshot = layer.javaClass.getDeclaredMethod("snapshot", Float::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(layer, phase)
                for (key in listOf("place", "char", "x", "y", "alpha", "blur", "sourceLeft", "sourceRight"))
                    metadata.append("layer$index.$key=${bits(property(snapshot, key))}\n")
                for (key in listOf("delay", "stagger")) metadata.append("layer$index.$key=${bits(property(layer, key))}\n")
            }
            proof.metadata("$name-phase-${bits(phase)}", metadata.toString())
        }
    }

    private fun verifyPerformanceCompose(screen: Activity, proof: PerformanceProof) {
        val ready = java.util.concurrent.CountDownLatch(1)
        var measurer: androidx.compose.ui.text.TextMeasurer? = null
        var density: androidx.compose.ui.unit.Density? = null
        onMain {
            screen.setContentView(androidx.compose.ui.platform.ComposeView(screen).apply {
                setContent {
                    val currentMeasurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 16)
                    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
                    androidx.compose.runtime.SideEffect {
                        measurer = currentMeasurer
                        density = currentDensity
                        ready.countDown()
                    }
                    tech.hezy.numerictext.AnimatedNumber(129)
                }
            })
        }
        check(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)) { "Compose TextMeasurer was not ready" }
        val constructor = Class.forName("tech.hezy.numerictext.NumberGlyphAtlas").declaredConstructors.single().apply { isAccessible = true }
        val transitionType = Class.forName("tech.hezy.numerictext.NumberDigitTransition")
        val companion = transitionType.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
        val settled = companion.javaClass.declaredMethods.single { it.name == "settled" }.apply { isAccessible = true }
        val retarget = transitionType.declaredMethods.single { it.name == "retarget" }.apply { isAccessible = true }
        val styles = listOf(
            androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = androidx.compose.ui.unit.TextUnit(32f, androidx.compose.ui.unit.TextUnitType.Sp),
                color = androidx.compose.ui.graphics.Color.Black),
            androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontSize = androidx.compose.ui.unit.TextUnit(32f, androidx.compose.ui.unit.TextUnitType.Sp),
                color = androidx.compose.ui.graphics.Color(0x992046A0)),
            androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontSize = androidx.compose.ui.unit.TextUnit(32f, androidx.compose.ui.unit.TextUnitType.Sp),
                brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                    androidx.compose.ui.graphics.Color(0x996020A0), androidx.compose.ui.graphics.Color(0x992070B0))),
                shadow = androidx.compose.ui.graphics.Shadow(
                    androidx.compose.ui.graphics.Color(0x40202020), androidx.compose.ui.geometry.Offset(3f, 5f), 2f))
        )
        runBlocking {
            withContext(Dispatchers.Main) {
                for (styleIndex in 0..3) {
                    val style = styles[minOf(styleIndex, 2)]
                    val direction = if (styleIndex == 3) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
                    fun newAtlas() = constructor.newInstance(style, checkNotNull(measurer), checkNotNull(density), direction)
                    val atlas = newAtlas()
                    val label = "compose-style$styleIndex"
                    var ordinal = 0
                    suspend fun measure(number: Int) = measureProofAtlas(atlas, proofRequest(number, ordinal++))
                    for ((index, pair) in proofPairs.withIndex()) {
                        val old = measure(pair.first)
                        val next = measure(pair.second)
                        captureProofLayout(proof, "$label-$index-from", old)
                        captureProofLayout(proof, "$label-$index-to", next)
                        val transition = retarget.invoke(settled.invoke(companion, old), next, 1f,
                            property(atlas, "travel"), property(atlas, "maxBlur"), 500)
                        captureProofTransition(proof, "$label-$index", transition)
                    }
                    val old = measure(129)
                    val next = measure(130)
                    val transition = retarget.invoke(settled.invoke(companion, old), next, 1f,
                        property(atlas, "travel"), property(atlas, "maxBlur"), 500)
                    val reversed = measure(128)
                    captureProofLayout(proof, "$label-reversal", reversed)
                    captureProofTransition(proof, "$label-reversal", retarget.invoke(transition, reversed, .2f,
                        property(atlas, "travel"), property(atlas, "maxBlur"), 500))
                    // Compare warmed exact-number hits too, with deliberately fresh request metadata.
                    for (index in 0..3) {
                        val request = proofRequest(if (index % 2 == 0) 129 else 130, ordinal++,
                            if (index < 2) 500 else 120)
                        captureProofLayout(proof, "$label-reused$index", measureProofAtlas(atlas, request))
                    }
                    repeat(12) { measure(if (it % 2 == 0) 129 else 130) }
                    val hot = LongArray(160)
                    for (index in hot.indices) {
                        val request = proofRequest(if (index % 2 == 0) 129 else 130, ordinal++)
                        val start = System.nanoTime()
                        measureProofAtlas(atlas, request)
                        hot[index] = System.nanoTime() - start
                    }
                    proof.timing("$label real-atlas-measure-hot-wall", hot)
                    val cold = LongArray(40)
                    for (index in cold.indices) {
                        val request = proofRequest(129, ordinal++)
                        val start = System.nanoTime()
                        measureProofAtlas(newAtlas(), request)
                        cold[index] = System.nanoTime() - start
                    }
                    proof.timing("$label construct-and-real-atlas-measure-cold-wall", cold)
                }
            }
        }
    }

    private fun verifyComposeCarry(screen: Activity) {
        fun update(value: Int) = runOnMainSync {
            callActivityOnNewIntent(screen, Intent(targetContext, MainActivity::class.java).putExtra("demo_number", value))
        }
        update(129)
        awaitValue("129")
        SystemClock.sleep(850)
        val bounds = android.graphics.Rect()
        val numeric = checkNotNull(nodes(uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString() == "129" })
        check(numeric.refresh()) { "Numeric accessibility node became stale" }
        numeric.getBoundsInScreen(bounds)
        fun render() = numericScreenshot(bounds)
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
                    check(moving.getPixel(x, y) == pixel) {
                        val directory = File(checkNotNull(targetContext.getExternalFilesDir(null)), "numeric-proof").apply { mkdirs() }
                        File(directory, "carry-before.png").outputStream().use { before.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        File(directory, "carry-moving.png").outputStream().use { moving.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        "Compose unchanged leading digit changed at ($x,$y): $pixel -> ${moving.getPixel(x, y)}; bounds=$bounds"
                    }
                }
            }
        } finally { before.recycle(); moving.recycle() }
        update(8)
        awaitValue("8")
    }

    private fun verifyComposeStyle(screen: Activity) {
        val number = androidx.compose.runtime.mutableStateOf(42)
        val immediate = androidx.compose.runtime.mutableStateOf(true)
        val style = androidx.compose.ui.text.TextStyle(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            fontSize = androidx.compose.ui.unit.TextUnit(48f, androidx.compose.ui.unit.TextUnitType.Sp),
            brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                androidx.compose.ui.graphics.Color(0x996020A0), androidx.compose.ui.graphics.Color(0x992070B0))),
            shadow = androidx.compose.ui.graphics.Shadow(
                androidx.compose.ui.graphics.Color(0x40202020), androidx.compose.ui.geometry.Offset(3f, 5f), 2f)
        )
        val content = onMain {
            android.widget.FrameLayout(targetContext).apply {
                setBackgroundColor(android.graphics.Color.WHITE)
                addView(androidx.compose.ui.platform.ComposeView(screen).apply {
                    setContent {
                        if (immediate.value) androidx.compose.foundation.text.BasicText(number.value.toString(), style = style)
                        else tech.hezy.numerictext.AnimatedNumber(number.value, style = style)
                    }
                }, android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
                screen.setContentView(this)
            }
        }
        awaitValue("42")
        SystemClock.sleep(250)
        val bounds = onMain {
            val location = IntArray(2)
            content.getLocationOnScreen(location)
            android.graphics.Rect(location[0], location[1] + content.height / 4,
                location[0] + content.width, location[1] + content.height * 3 / 4)
        }
        val reference = numericScreenshot(bounds)
        try {
            runOnMainSync { number.value = 129; immediate.value = false }
            for (value in intArrayOf(130, 99, 100, -1, -10, Int.MIN_VALUE, Int.MAX_VALUE, 42)) {
                runOnMainSync { number.value = value }
                SystemClock.sleep(80)
            }
            awaitValue("42")
            await("Interrupted Compose gradient/shadow typography converges to BasicText") {
                val actual = numericScreenshot(bounds)
                try { actual.sameAs(reference) } finally { actual.recycle() }
            }
        } finally { reference.recycle() }
        capture("compose-styled-verified")
    }

    private fun numericScreenshot(bounds: android.graphics.Rect): Bitmap {
        val screenshot = checkNotNull(uiAutomation.takeScreenshot())
        return try {
            Bitmap.createBitmap(screenshot, bounds.left, bounds.top, bounds.width(), bounds.height())
        } finally { screenshot.recycle() }
    }

    private fun verifyRapidSettling(value: String) {
        val bounds = android.graphics.Rect()
        val numeric = checkNotNull(nodes(uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString() == value })
        check(numeric.refresh()) { "Numeric accessibility node became stale" }
        numeric.getBoundsInScreen(bounds)
        val before = numericScreenshot(bounds)
        SystemClock.sleep(500)
        val after = numericScreenshot(bounds)
        try {
            check(before.sameAs(after)) { "Rapid input still changed visible digits after 300 ms; target=$value" }
        } finally { before.recycle(); after.recycle() }
    }

    private fun verifyInterrupted(view: AnimatedNumberView) {
        check(android.animation.ValueAnimator.areAnimatorsEnabled()) { "Enable animations for interruption verification" }
        runOnMainSync { view.setNumber(129) }
        SystemClock.sleep(850)
        runOnMainSync { view.setNumber(130) }
        for (value in intArrayOf(131, 130, 99, 100, -1, 0, -10, 10, Int.MIN_VALUE, Int.MAX_VALUE)) {
            SystemClock.sleep(80)
            onMain {
                val before = image(view)
                view.setNumber(value)
                val after = image(view)
                try {
                    if (!before.sameAs(after)) {
                        val a = IntArray(before.width * before.height)
                        val b = IntArray(a.size)
                        before.getPixels(a, 0, before.width, 0, 0, before.width, before.height)
                        after.getPixels(b, 0, after.width, 0, 0, after.width, after.height)
                        // 允许亚像素重算的微小栅格差异，不允许整位跳回上次目标。
                        val ink = a.sumOf { android.graphics.Color.alpha(it).toLong() }
                        val delta = a.indices.sumOf { index ->
                            kotlin.math.abs(android.graphics.Color.alpha(a[index]) - android.graphics.Color.alpha(b[index])).toLong()
                        }
                        check(delta <= ink / 100) { "Rapid update to $value changed $delta/$ink alpha coverage without an animation frame" }
                    }
                } finally { before.recycle(); after.recycle() }
            }
        }
        SystemClock.sleep(300)
        check(view.number == Int.MAX_VALUE)
    }

    private fun launch(type: Class<out Activity>, number: Int = 0): Activity {
        val activity = startActivitySync(
            Intent(targetContext, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_number", number)
        )
        await("Numeric demo window focus") { onMain { activity.window.decorView.hasWindowFocus() } }
        return activity
    }

    private fun verifyNative(view: AnimatedNumberView) {
        fun expect(value: Int, label: String) {
            val expected = onMain {
                val reference = AnimatedNumberView(targetContext).apply {
                    textSizePixels = view.textSizePixels
                    textColor = view.textColor
                    typeface = view.typeface
                    setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
                    setNumber(value)
                    measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                    layout(0, 0, measuredWidth, measuredHeight)
                }
                image(reference)
            }
            try {
                await("$label renders $value with the correct digits and dimensions") {
                    onMain {
                        if (view.width != expected.width || view.height != expected.height) false else {
                            val actual = image(view)
                            try { actual.sameAs(expected) } finally { actual.recycle() }
                        }
                    }
                }
            } finally { expected.recycle() }
        }
        // 初始零也必须完整绘制，不能依赖首次动画才出现。
        expect(0, "Initial value")
        for (value in intArrayOf(9, 10, 100, 99, -1, Int.MIN_VALUE, Int.MAX_VALUE)) {
            runOnMainSync { view.setNumber(value) }
            expect(value, "Rollover")
        }
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            verifyInterrupted(view)
            expect(Int.MAX_VALUE, "Rapid reversal and signed boundaries")
            runOnMainSync { view.setNumber(129) }
            expect(129, "Carry start")
            val before = onMain { image(view) }
            runOnMainSync { view.setNumber(130) }
            SystemClock.sleep(120)
            val moving = onMain { image(view) }
            try {
                check(!before.sameAs(moving)) { "Changed digits did not animate: shown=${onMain { view.isShown }}, focus=${onMain { view.hasWindowFocus() }}, window=${onMain { view.windowVisibility }}, target=${view.number}" }
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
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            runOnMainSync { view.setNumber(-11) }
            SystemClock.sleep(120)
            val moving = onMain { image(view) }
            try {
                val pixels = IntArray(moving.width * moving.height)
                moving.getPixels(pixels, 0, moving.width, 0, 0, moving.width, moving.height)
                check(pixels.all { android.graphics.Color.alpha(it) <= 128 }) { "Blur interpolation exceeded text color alpha" }
            } finally { moving.recycle() }
            expect(-11, "Translucent blur transition")
        }
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
