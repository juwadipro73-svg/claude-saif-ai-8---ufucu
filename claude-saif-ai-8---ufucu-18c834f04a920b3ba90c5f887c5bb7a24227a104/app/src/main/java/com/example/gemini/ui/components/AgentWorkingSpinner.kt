package com.example.gemini.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// The "working" row of the Claude Code VS Code extension: a pulsing star glyph and a playful verb that is "decoded"
// in with a running cursor. Same glyphs, verbs and timings as the extension.

private val GLYPHS = listOf("·", "✢", "*", "✶", "✻", "✽").let { it + it.reversed() }

private val VERBS = listOf(
    "Accomplishing", "Actioning", "Actualizing", "Baking", "Booping", "Brewing", "Calculating", "Cerebrating",
    "Channeling", "Churning", "Clauding", "Coalescing", "Cogitating", "Computing", "Combobulating", "Concocting",
    "Considering", "Contemplating", "Cooking", "Crafting", "Creating", "Crunching", "Deciphering", "Deliberating",
    "Determining", "Discombobulating", "Doing", "Effecting", "Elucidating", "Enchanting", "Envisioning", "Finagling",
    "Flibbertigibbeting", "Forging", "Forming", "Frolicking", "Generating", "Germinating", "Hatching", "Herding",
    "Honking", "Ideating", "Imagining", "Incubating", "Inferring", "Manifesting", "Marinating", "Meandering",
    "Moseying", "Mulling", "Mustering", "Musing", "Noodling", "Percolating", "Perusing", "Philosophizing",
    "Pontificating", "Pondering", "Processing", "Puttering", "Puzzling", "Reticulating", "Ruminating", "Schlepping",
    "Shimmying", "Simmering", "Smooshing", "Spelunking", "Spinning", "Stewing", "Sussing", "Synthesizing", "Thinking",
    "Tinkering", "Transmuting", "Unfurling", "Unraveling", "Vibing", "Wandering", "Whirring", "Wibbling", "Working",
    "Wrangling"
)

/** The thinking mark is a slowly turning sphere of dots (same footprint the old star glyph had). */
private const val SPHERE_POINTS = 110
private val SPHERE_DIAMETER = 22.dp
private val SPHERE_XYZ: FloatArray = FloatArray(SPHERE_POINTS * 3).also { a ->
    val golden = Math.PI * (3.0 - Math.sqrt(5.0))
    for (i in 0 until SPHERE_POINTS) {
        val y = 1.0 - (i / (SPHERE_POINTS - 1).toDouble()) * 2.0
        val r = Math.sqrt(1.0 - y * y)
        val t = golden * i
        a[i * 3] = (Math.cos(t) * r).toFloat()
        a[i * 3 + 1] = y.toFloat()
        a[i * 3 + 2] = (Math.sin(t) * r).toFloat()
    }
}

private fun DrawScope.drawDotSphere(center: Offset, radius: Float, spin: Float, color: Color) {
    val cosA = Math.cos(spin.toDouble()).toFloat()
    val sinA = Math.sin(spin.toDouble()).toFloat()
    val tilt = 0.35f
    val cosT = Math.cos(tilt.toDouble()).toFloat()
    val sinT = Math.sin(tilt.toDouble()).toFloat()
    val dotBase = radius * 0.075f
    for (i in 0 until SPHERE_POINTS) {
        val x = SPHERE_XYZ[i * 3]
        val y = SPHERE_XYZ[i * 3 + 1]
        val z = SPHERE_XYZ[i * 3 + 2]
        val x1 = x * cosA + z * sinA
        val z1 = -x * sinA + z * cosA
        val y2 = y * cosT - z1 * sinT
        val z2 = y * sinT + z1 * cosT
        val depth = (z2 + 1f) / 2f
        val dotColor = lerp(color, Color.White, depth * 0.45f).copy(alpha = 0.22f + 0.78f * depth)
        drawCircle(
            color = dotColor,
            radius = dotBase * (0.55f + 0.65f * depth),
            center = Offset(center.x + x1 * radius, center.y + y2 * radius)
        )
    }
}

/** Room for the longest verb plus "...": the row never changes width. */
private val TEXT_WIDTH = VERBS.maxOf { it.length } + 3

private const val GLYPH_STEP_MS = 120L
private const val DECODE_FRAME_MS = 40L
/** The verb changes after 2 s, then 3 s, then every 5 s. */
private val WORD_DELAYS_MS = longArrayOf(2_000, 3_000)
private const val WORD_DELAY_MS = 5_000L

/**
 * The agent is working.
 *
 * Built so a tick costs as little as possible: the row has one fixed size (tallest glyph, widest verb), so it
 * never re-lays out the chat around it; the animation state is read only while drawing (no recomposition or
 * layout per tick); and it draws in its own graphics layer, so only this row is repainted. Glyphs and text sit
 * on fixed baselines, so nothing moves up and down when a glyph or character comes from another font.
 */
@Composable
fun AgentWorkingSpinner(color: Color, modifier: Modifier = Modifier, compacting: Boolean = false) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    // a quiet status line (like VS Code's softened foreground); the accent-colored star is what draws the eye
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val glyphStyle = remember(color) { TextStyle(color = color, fontSize = 21.sp, fontFamily = FontFamily.Monospace) }
    val wordStyle = remember(textColor) { TextStyle(color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
    val density = LocalDensity.current

    // measured once: every glyph, and the widest text the row can show
    val glyphLayouts = remember(measurer, glyphStyle) { GLYPHS.map { measurer.measure(it, glyphStyle) } }
    val wordRef = remember(measurer, wordStyle) { measurer.measure("Compacting...", wordStyle) }
    val wordWidth = remember(measurer, wordStyle) {
        (VERBS + "Compacting").maxOf { measurer.measure("$it...", wordStyle).size.width } + measurer.measure("▌", wordStyle).size.width
    }
    val glyphBox = with(density) { SPHERE_DIAMETER.roundToPx() }
    val gapPx = with(density) { 4.dp.roundToPx() }
    val heightPx = maxOf(glyphLayouts.maxOf { it.size.height }, wordRef.size.height)
    val glyphBaseline = (heightPx - glyphLayouts[GLYPHS.indexOf("✻")].size.height) / 2f + glyphLayouts[GLYPHS.indexOf("✻")].firstBaseline
    val wordBaseline = (heightPx - wordRef.size.height) / 2f + wordRef.firstBaseline

    val shown = remember { mutableStateOf(" ".repeat(TEXT_WIDTH)) }
    WordDecoder(shown, compacting)
    val spinTransition = rememberInfiniteTransition(label = "thinkingSphere")
    val spin = spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 7000, easing = LinearEasing), RepeatMode.Restart),
        label = "thinkingSphereSpin"
    )

    Canvas(
        modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .size(with(density) { (glyphBox + gapPx + wordWidth).toDp() }, with(density) { heightPx.toDp() })
            .graphicsLayer()
    ) {
        // state is read here only: a tick redraws this layer and nothing else
        drawDotSphere(Offset(glyphBox / 2f, size.height / 2f), glyphBox / 2f - 1f, spin.value, color)
        val word = measurer.measure(shown.value, wordStyle, softWrap = false, maxLines = 1)
        drawText(word, topLeft = Offset((glyphBox + gapPx).toFloat(), wordBaseline - word.firstBaseline))
    }
}

@Composable
private fun GlyphTicker(step: androidx.compose.runtime.MutableIntState) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(GLYPH_STEP_MS)
            step.intValue = (step.intValue + 1) % GLYPHS.size
        }
    }
}

@Composable
private fun WordDecoder(shown: androidx.compose.runtime.MutableState<String>, compacting: Boolean) {
    var verb by remember { mutableStateOf(VERBS.random()) }
    LaunchedEffect(Unit) {
        var changes = 0
        while (true) {
            delay(WORD_DELAYS_MS.getOrElse(changes) { WORD_DELAY_MS })
            changes++
            verb = VERBS.random()
        }
    }
    // A cursor runs over the old text; the three characters behind it flicker through '.' and '_' before settling.
    LaunchedEffect(verb, compacting) {
        val target = ((if (compacting) "Compacting" else verb) + "...").padEnd(TEXT_WIDTH)
        var cursor = 0
        while (cursor - 3 < target.length) {
            val next = StringBuilder(shown.value)
            for (behind in 0..3) {
                val k = cursor - behind
                if (k < 0 || k >= target.length) continue
                val want = target[k]
                next.setCharAt(
                    k,
                    when {
                        want == ' ' -> ' '
                        behind == 3 -> want
                        behind == 0 -> '▌'
                        else -> charArrayOf('.', '_', want).random()
                    }
                )
            }
            shown.value = next.toString()
            cursor++
            delay(DECODE_FRAME_MS)
        }
    }
}
