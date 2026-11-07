package com.bettercallsoul.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch

/** True when the app may animate: the user's switch in Settings, and the system's own. */
val LocalMotion = staticCompositionLocalOf { true }

/**
 * "Remove animations" in accessibility and the animator scale in developer options both set
 * this to 0; the app then stays still whatever its own switch says.
 */
fun systemAnimationsOn(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/** A spring when motion is on, an instant jump when it isn't. */
@Composable
fun <T> motionSpec(spec: AnimationSpec<T> = spring()): AnimationSpec<T> = if (LocalMotion.current) spec else snap()

/**
 * The elastic touch of glass-like controls: the shape squashes under the finger, leans and stretches
 * a little as the finger moves, and springs back with a wobble when let go. It only watches the
 * touch, so clicks, long presses and swipes behave as before. [follow] off keeps it to the squash,
 * for rows that swipe sideways.
 */
fun Modifier.stretchy(strength: Float = 1f, follow: Boolean = true): Modifier = composed {
    if (!LocalMotion.current) return@composed this
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    val pull = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    this
        .onSizeChanged { size = it }
        .pointerInput(follow) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { press.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) }
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (follow) {
                            val moved = change.position - down.position
                            scope.launch { pull.snapTo(moved) }
                        }
                    }
                } finally {
                    scope.launch { press.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow)) }
                    scope.launch { pull.animateTo(Offset.Zero, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow)) }
                }
            }
        }
        .graphicsLayer {
            val width = size.width.coerceAtLeast(1).toFloat()
            val height = size.height.coerceAtLeast(1).toFloat()
            val squash = press.value * strength
            val dx = rubber(pull.value.x, width)
            val dy = rubber(pull.value.y, height)
            translationX = dx * 0.3f * strength
            translationY = dy * 0.3f * strength
            scaleX = 1f - 0.05f * squash + abs(dx) / width * 0.18f * strength
            scaleY = 1f - 0.07f * squash + abs(dy) / height * 0.18f * strength
        }
}

/** Rubber band: the further the finger goes, the less the shape follows, never more than half its size. */
private fun rubber(distance: Float, size: Float): Float =
    sign(distance) * size * 0.5f * (1f - 1f / (abs(distance) / size * 0.9f + 1f))

/**
 * A translucent "liquid glass" surface: a tint, a soft specular highlight across the top, and a rim
 * that is bright above and fades below, as light catches a glass edge. [onDark] is for surfaces that
 * always sit on a dark backdrop, such as the call screen; otherwise it follows the theme.
 */
@Composable
fun Modifier.liquidGlass(shape: Shape, onDark: Boolean = isSystemInDarkTheme()): Modifier {
    val tint = if (onDark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.72f)
    val shine = if (onDark) 0.16f else 0.55f
    val rimTop = if (onDark) Color.White.copy(alpha = 0.34f) else Color.White
    val rimBottom = if (onDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.07f)
    return this
        .clip(shape)
        .background(tint)
        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = shine), 0.45f to Color.Transparent))
        .border(1.dp, Brush.verticalGradient(listOf(rimTop, rimBottom)), shape)
}
