package tech.hezy.numerictext

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

private val DefaultNumberStyle = TextStyle(fontSize = 36.sp)
private const val BlurSteps = 8
private const val SignPlace = -1

/**
 * Digits transition independently; unchanged glyphs remain sharp as the width changes.
 * Supports the full Int range and Compose typography. Explicit color overrides style.color.
 * Initial values, zero duration and disabled system animations display immediately.
 * Accessibility exposes only the latest whole integer, never its outgoing glyphs.
 */
@Composable
fun AnimatedNumber(
    number: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = DefaultNumberStyle,
    color: Color = Color.Unspecified,
    animationDurationMillis: Int = 500
) {
    require(animationDurationMillis >= 0) { "animationDurationMillis must not be negative" }
    val numberText = remember(number) { AnnotatedString(number.toString()) }
    val textStyle = remember(style, color) {
        if (color == Color.Unspecified) style else style.copy(color = color)
    }
    val accessibleModifier = modifier.clearAndSetSemantics {
        text = numberText
        liveRegion = LiveRegionMode.Polite
    }
    if (animationDurationMillis == 0 || !NumericTextTransition.animationsEnabled(LocalContext.current)) {
        BasicText(numberText.text, accessibleModifier, textStyle, maxLines = 1, softWrap = false)
        return
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fontResolver = LocalFontFamilyResolver.current
    // Reading this State also refreshes cached rasters when an asynchronous custom font resolves.
    val resolvedFont = fontResolver.resolve(
        textStyle.fontFamily,
        textStyle.fontWeight ?: FontWeight.Normal,
        textStyle.fontStyle ?: FontStyle.Normal,
        textStyle.fontSynthesis ?: FontSynthesis.All
    ).value
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val atlas = remember(textStyle, density, layoutDirection, resolvedFont, measurer) {
        NumberGlyphAtlas(textStyle, measurer, density, layoutDirection)
    }
    val target = remember(number, atlas) { atlas.measure(number) }
    val progress = remember(atlas) { Animatable(1f) }
    var transition by remember(atlas) { mutableStateOf(NumberDigitTransition.settled(target)) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }

    LaunchedEffect(target, animationDurationMillis) {
        if (transition.target.number != target.number) {
            transition = transition.retarget(target, progress.value, atlas.travel, atlas.maxBlur)
            progress.snapTo(0f)
            progress.animateTo(1f, tween(transition.duration(animationDurationMillis), easing = LinearEasing))
            transition = NumberDigitTransition.settled(target)
        } else if (progress.value < 1f) {
            // A duration change must not leave a cancelled transition half-finished.
            progress.animateTo(1f, tween(transition.duration(animationDurationMillis), easing = LinearEasing))
            transition = NumberDigitTransition.settled(target)
        }
    }

    Canvas(accessibleModifier.layout { measurable, constraints ->
        val fraction = if (transition.startWidth == transition.target.width) 1f else transition.widthFraction(progress.value)
        val width = constraints.constrainWidth(lerp(transition.startWidth, transition.target.width, fraction).roundToInt())
        val height = constraints.constrainHeight(transition.target.height)
        val child = measurable.measure(Constraints.fixed(width, height))
        layout(width, height, transition.target.baselines) { child.place(0, 0) }
    }) {
        val clock = progress.value
        val fraction = transition.widthFraction(clock)
        val width = lerp(transition.startWidth, transition.target.width, fraction)
        val spare = size.width - width
        val left = when (textStyle.textAlign) {
            TextAlign.Center -> spare / 2f
            TextAlign.Right -> spare
            TextAlign.Left -> 0f
            TextAlign.End -> if (layoutDirection == LayoutDirection.Ltr) spare else 0f
            else -> if (layoutDirection == LayoutDirection.Rtl) spare else 0f
        }
        val canvas = drawContext.canvas.nativeCanvas
        val anchor = left + width / 2f
        if (clock >= 1f) {
            drawText(transition.target.textLayout, topLeft = Offset(left, transition.target.baseline - transition.target.textLayout.firstBaseline))
            return@Canvas
        }
        paint.color = atlas.tint
        for (index in transition.layers.indices) {
            val layer = transition.layers[index]
            val t = layer.fraction(clock)
            val alpha = lerp(layer.start.alpha, layer.end.alpha, t)
            if (alpha <= 0f) continue
            val glyph = layer.start.glyph
            val centerX = anchor + lerp(layer.start.x, layer.end.x, t)
            val baseline = transition.target.baseline + lerp(layer.start.y, layer.end.y, t)
            if (layer.start.char == layer.end.char && layer.start.alpha == 1f && layer.end.alpha == 1f &&
                layer.start.blur == 0f && layer.end.blur == 0f) {
                val source = layer.start
                val origin = centerX - (source.sourceLeft + source.sourceRight) / 2f
                drawContext.canvas.save()
                drawContext.canvas.clipRect(origin + source.sourceLeft, 0f, origin + source.sourceRight, size.height)
                drawText(source.textLayout, topLeft = Offset(origin, baseline - source.textLayout.firstBaseline))
                drawContext.canvas.restore()
                continue
            }
            val x = centerX - glyph.center
            val y = baseline - glyph.baseline
            val scale = if (layer.start.char == '-') 1f else 0.4f + 0.6f * alpha
            if (scale < 1f) {
                canvas.save()
                canvas.scale(scale, scale, centerX, baseline - atlas.scalePivot)
            }
            val blur = (lerp(layer.start.blur, layer.end.blur, t) / atlas.maxBlur * BlurSteps)
                .coerceIn(0f, BlurSteps.toFloat())
            val lower = blur.toInt()
            val blend = blur - lower
            paint.alpha = (alpha * (1f - blend) * 255f).roundToInt().coerceIn(0, 255)
            val lowerRaster = glyph.rasters[lower]
            canvas.drawBitmap(lowerRaster.bitmap, x + lowerRaster.x, y + lowerRaster.y, paint)
            if (blend > 0f && lower < BlurSteps) {
                // Source-over compensation preserves opacity while interpolating cached blur levels.
                val upperAlpha = alpha * blend / (1f - alpha * (1f - blend))
                paint.alpha = (upperAlpha * 255f).roundToInt().coerceIn(0, 255)
                val upperRaster = glyph.rasters[lower + 1]
                canvas.drawBitmap(upperRaster.bitmap, x + upperRaster.x, y + upperRaster.y, paint)
            }
            if (scale < 1f) canvas.restore()
        }
    }
}

private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction

private data class GlyphRaster(val bitmap: Bitmap, val x: Float = 0f, val y: Float = 0f)
private data class NumberGlyph(val rasters: Array<GlyphRaster>, val center: Float, val baseline: Float) {
    val byteCount = rasters.sumOf { it.bitmap.allocationByteCount.toLong() }
}
private data class NumberDigit(
    val place: Int,
    val char: Char,
    val x: Float,
    val glyph: NumberGlyph,
    val textLayout: TextLayoutResult,
    val sourceLeft: Float,
    val sourceRight: Float,
    val y: Float = 0f,
    val alpha: Float = 1f,
    val blur: Float = 0f
)
private data class NumberLayout(
    val number: Int,
    val width: Float,
    val height: Int,
    val baseline: Float,
    val baselines: Map<androidx.compose.ui.layout.AlignmentLine, Int>,
    val textLayout: TextLayoutResult,
    val digits: List<NumberDigit>
)
private data class NumberDigitLayer(val start: NumberDigit, val end: NumberDigit, val delay: Float = 0f, val stagger: Float = 0f) {
    fun fraction(progress: Float) = NumberDigitTransition.ease((progress * (1f + stagger) - delay).coerceIn(0f, 1f))
    fun snapshot(progress: Float): NumberDigit {
        val t = fraction(progress)
        return start.copy(
            x = lerp(start.x, end.x, t), y = lerp(start.y, end.y, t),
            alpha = lerp(start.alpha, end.alpha, t), blur = lerp(start.blur, end.blur, t)
        )
    }
}

private class NumberDigitTransition(val target: NumberLayout, val startWidth: Float, val layers: List<NumberDigitLayer>) {
    private val stagger = layers.maxOfOrNull { it.stagger } ?: 0f
    fun widthFraction(progress: Float) = ease((progress * (1f + stagger)).coerceIn(0f, 1f))
    fun duration(millis: Int) = (millis.toDouble() * (1f + stagger)).coerceAtMost(Int.MAX_VALUE.toDouble()).roundToInt()
    fun retarget(next: NumberLayout, progress: Float, travel: Float, blur: Float): NumberDigitTransition {
        val direction = if (next.number > target.number) 1f else -1f
        // At most eleven characters per string position: interruptions never accumulate a queue.
        val current = layers.map { it.snapshot(progress) }.filter { it.alpha > 0.001f }
        val changedPlaces = (current.map { it.place } + next.digits.map { it.place }).distinct().filter { place ->
            val old = current.filter { it.place == place }
            val new = next.digits.firstOrNull { it.place == place }
            old.size != 1 || new == null || old[0].char != new.char || old[0].alpha < 1f || old[0].blur > 0f
        }.sorted()
        val stagger = minOf(0.48f, max(0, changedPlaces.size - 1) * 0.24f)
        fun delay(place: Int): Float {
            val index = changedPlaces.indexOf(place)
            return if (index <= 0 || changedPlaces.size < 2) 0f else stagger * index / (changedPlaces.size - 1)
        }
        val result = ArrayList<NumberDigitLayer>(current.size + next.digits.size)
        for (old in current) {
            val new = next.digits.firstOrNull { it.place == old.place && it.char == old.char }
            result += NumberDigitLayer(
                old, new ?: old.copy(y = if (old.char == '-') 0f else old.y - direction * travel, alpha = 0f, blur = if (old.char == '-') 0f else blur),
                delay(old.place), stagger
            )
        }
        for (new in next.digits) {
            if (current.none { it.place == new.place && it.char == new.char }) {
                result += NumberDigitLayer(new.copy(y = if (new.char == '-') 0f else direction * travel, alpha = 0f, blur = if (new.char == '-') 0f else blur), new, delay(new.place), stagger)
            }
        }
        return NumberDigitTransition(next, lerp(startWidth, target.width, widthFraction(progress)), result)
    }

    companion object {
        fun ease(progress: Float) = NumericTextTransition.SnappyInterpolator.getInterpolation(progress)
        fun settled(target: NumberLayout) = NumberDigitTransition(target, target.width, target.digits.map { NumberDigitLayer(it, it) })
    }
}

/** Whole-string shaping preserves contextual fonts and brushes; crops and effects are cached by typography. */
private class NumberGlyphAtlas(
    private val style: TextStyle,
    private val measurer: TextMeasurer,
    private val density: Density,
    private val layoutDirection: LayoutDirection
) {
    private val fontPixels = with(density) { if (style.fontSize.isSpecified) style.fontSize.toPx() else 14.sp.toPx() }
    val travel = fontPixels * 0.42f
    val maxBlur = max(0.01f, fontPixels * 0.12f)
    val scalePivot = fontPixels * 0.35f
    private val verticalPadding = ceil(fontPixels * 0.2f).toInt()
    private val shadow = style.shadow
    private val rasterPadding = ceil(max(fontPixels * 0.4f,
        (shadow?.blurRadius ?: 0f) * 3f + max(abs(shadow?.offset?.x ?: 0f), abs(shadow?.offset?.y ?: 0f)))).toInt()
    private val monochrome = (style.brush == null || style.brush is SolidColor) &&
        style.shadow == null && (style.background == Color.Unspecified || style.background.alpha == 0f)
    val tint = if (monochrome) {
        val color = (style.brush as? SolidColor)?.value ?: style.color
        (if (color == Color.Unspecified) Color.Black else color).copy(alpha = 1f).toArgb()
    } else android.graphics.Color.WHITE
    private val scope = CanvasDrawScope()
    private val kernels = if (monochrome) emptyArray() else Array(BlurSteps) { gaussianKernel(maxBlur * (it + 1) / BlurSteps) }
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cache = LinkedHashMap<GlyphPixels, NumberGlyph>(24, 0.75f, true)
    private var cacheBytes = 0L

    fun measure(number: Int): NumberLayout {
        val text = number.toString()
        val layout = measurer.measure(text, style, softWrap = false, maxLines = 1)
        val width = layout.size.width.toFloat()
        val bitmap = Bitmap.createBitmap(max(1, layout.size.width + rasterPadding * 2),
            max(1, layout.size.height + rasterPadding * 2), Bitmap.Config.ARGB_8888)
        scope.draw(density, layoutDirection, androidx.compose.ui.graphics.Canvas(bitmap.asImageBitmap()), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
            drawText(layout, topLeft = Offset(rasterPadding.toFloat(), rasterPadding.toFloat()))
        }
        // Bounding boxes also handle sign/digit bidi runs without overlapping horizontal-position ranges.
        val boxes = Array(text.length) { layout.getBoundingBox(it) }
        val lefts = IntArray(text.length) { index ->
            (rasterPadding + floor(boxes[index].left).toInt()).coerceIn(0, bitmap.width)
        }
        val rights = IntArray(text.length) { index ->
            (rasterPadding + floor(boxes[index].right).toInt()).coerceIn(0, bitmap.width)
        }
        lefts[lefts.indices.minBy { lefts[it] }] = 0
        rights[rights.indices.maxBy { rights[it] }] = bitmap.width
        val digits = text.mapIndexed { index, char ->
            val left = lefts[index]
            val right = rights[index]
            val cropWidth = max(1, right - left)
            val glyphBitmap = Bitmap.createBitmap(cropWidth + rasterPadding * 2, bitmap.height, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(glyphBitmap).drawBitmap(bitmap, Rect(left, 0, minOf(bitmap.width, left + cropWidth), bitmap.height),
                Rect(rasterPadding, 0, rasterPadding + cropWidth, bitmap.height), null)
            val pixels = IntArray(glyphBitmap.width * glyphBitmap.height)
            glyphBitmap.getPixels(pixels, 0, glyphBitmap.width, 0, 0, glyphBitmap.width, glyphBitmap.height)
            val logicalCenter = (boxes[index].left + boxes[index].right) / 2f
            val center = rasterPadding * 2f + logicalCenter - left
            val baseline = rasterPadding + layout.firstBaseline
            val key = GlyphPixels(glyphBitmap.width, glyphBitmap.height, center, baseline, pixels)
            val glyph = cache[key]?.also { glyphBitmap.recycle() } ?: createGlyph(glyphBitmap, pixels, center, baseline).also {
                cache[key] = it
                cacheBytes += key.byteCount + it.byteCount
                while (cache.size > 24 || cacheBytes > 8L * 1024 * 1024) {
                    val entries = cache.entries.iterator()
                    val oldest = entries.next()
                    cacheBytes -= oldest.key.byteCount + oldest.value.byteCount
                    // A running transition can still own these rasters; only drop the cache reference.
                    entries.remove()
                }
            }
            NumberDigit(if (char == '-') SignPlace else index, char,
                logicalCenter - width / 2f, glyph, layout, boxes[index].left, boxes[index].right)
        }
        bitmap.recycle()
        return NumberLayout(number, width, layout.size.height + verticalPadding * 2,
            layout.firstBaseline + verticalPadding,
            mapOf(FirstBaseline to (layout.firstBaseline + verticalPadding).roundToInt(),
                LastBaseline to (layout.lastBaseline + verticalPadding).roundToInt()), layout, digits)
    }

    private fun createGlyph(bitmap: Bitmap, pixels: IntArray, center: Float, baseline: Float): NumberGlyph {
        val rasters = if (monochrome) {
            val alpha = bitmap.extractAlpha()
            bitmap.recycle()
            Array(BlurSteps + 1) { level ->
                if (level == 0) GlyphRaster(alpha) else {
                    // Android's mask radius maps to sigma approximately as 0.577 * radius.
                    blurPaint.maskFilter = BlurMaskFilter(fontPixels * 0.20f * level / BlurSteps, BlurMaskFilter.Blur.NORMAL)
                    val offset = IntArray(2)
                    GlyphRaster(alpha.extractAlpha(blurPaint, offset), offset[0].toFloat(), offset[1].toFloat())
                }
            }
        } else {
            val premultiplied = pixels.copyOf()
            for (index in premultiplied.indices) {
                val value = premultiplied[index]
                val alpha = value ushr 24
                premultiplied[index] = (alpha shl 24) or (((value ushr 16 and 255) * alpha / 255) shl 16) or
                    (((value ushr 8 and 255) * alpha / 255) shl 8) or ((value and 255) * alpha / 255)
            }
            val scratch = IntArray(pixels.size)
            val output = IntArray(pixels.size)
            Array(BlurSteps + 1) { level ->
                if (level == 0) GlyphRaster(bitmap) else {
                    gaussianBlur(premultiplied, scratch, output, bitmap.width, bitmap.height, kernels[level - 1])
                    GlyphRaster(Bitmap.createBitmap(output, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888))
                }
            }
        }
        return NumberGlyph(rasters, center, baseline)
    }
}

private class GlyphPixels(private val width: Int, private val height: Int, private val center: Float, private val baseline: Float, private val pixels: IntArray) {
    private val hash = 31 * (31 * width + height) + pixels.contentHashCode()
    val byteCount = pixels.size.toLong() * Int.SIZE_BYTES
    override fun hashCode() = hash
    override fun equals(other: Any?) = other is GlyphPixels && width == other.width && height == other.height &&
        center == other.center && baseline == other.baseline && pixels.contentEquals(other.pixels)
}

private fun gaussianKernel(sigma: Float): FloatArray {
    val radius = max(1, ceil(sigma * 3f).toInt())
    val weights = FloatArray(radius * 2 + 1) { index ->
        val distance = index - radius
        exp(-distance * distance / (2f * sigma * sigma))
    }
    val sum = weights.sum()
    for (index in weights.indices) weights[index] /= sum
    return weights
}

/** Software Gaussian convolution works identically on API 21 and newer; never runs in a frame. */
private fun gaussianBlur(source: IntArray, scratch: IntArray, output: IntArray, width: Int, height: Int, kernel: FloatArray) {
    val radius = kernel.size / 2
    for (pass in 0..1) {
        val input = if (pass == 0) source else scratch
        val result = if (pass == 0) scratch else output
        for (y in 0 until height) for (x in 0 until width) {
            var alpha = 0f
            var red = 0f
            var green = 0f
            var blue = 0f
            for (index in kernel.indices) {
                val px = if (pass == 0) x + index - radius else x
                val py = if (pass == 1) y + index - radius else y
                if (px !in 0 until width || py !in 0 until height) continue
                val color = input[py * width + px]
                val weight = kernel[index]
                alpha += (color ushr 24) * weight
                red += (color ushr 16 and 255) * weight
                green += (color ushr 8 and 255) * weight
                blue += (color and 255) * weight
            }
            val a = alpha.roundToInt().coerceIn(0, 255)
            if (pass == 1 && a > 0) {
                red *= 255f / a
                green *= 255f / a
                blue *= 255f / a
            }
            result[y * width + x] = (a shl 24) or (red.roundToInt().coerceIn(0, 255) shl 16) or
                (green.roundToInt().coerceIn(0, 255) shl 8) or blue.roundToInt().coerceIn(0, 255)
        }
    }
}
