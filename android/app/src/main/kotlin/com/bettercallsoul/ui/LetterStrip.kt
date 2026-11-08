package com.bettercallsoul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** How long a finger rests on the strip before it takes over from the list. */
private const val HOLD_MILLIS = 220L

/**
 * The letters of the list, faint along the edge. Held for a moment it wakes up; sliding the finger
 * then goes from letter to letter, with the letter large beside the finger and a tick at each one.
 * The hold is what starts it, so a brush of the edge does nothing.
 */
@Composable
fun LetterStrip(letters: List<String>, modifier: Modifier = Modifier, onLetter: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val pick by rememberUpdatedState(onLetter)
    var active by remember { mutableStateOf(false) }
    var current by remember { mutableIntStateOf(-1) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(1) }
    val bubble = with(LocalDensity.current) { 64.dp.roundToPx() }

    fun follow(y: Float) {
        fingerY = y.coerceIn(0f, height.toFloat())
        val index = (fingerY / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (index != current) {
            current = index
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            pick(index)
        }
    }

    Box(modifier.fillMaxHeight().padding(vertical = 12.dp)) {
        if (active && current >= 0) {
            Box(
                Modifier
                    .offset { IntOffset(-bubble - 12, (fingerY - bubble / 2f).roundToInt()) }
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(letters[current], fontSize = 30.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Column(
            Modifier
                .fillMaxHeight()
                .width(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.background.copy(alpha = 0f))
                .alpha(if (active) 1f else 0.35f)
                .onSizeChanged { height = it.height.coerceAtLeast(1) }
                .pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Lifted or moved before the hold is up: a tap or a list swipe, not ours.
                        val early = withTimeoutOrNull(HOLD_MILLIS) {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                if (!change.pressed) return@withTimeoutOrNull true
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                            }
                            @Suppress("UNREACHABLE_CODE")
                            true
                        }
                        if (early != null) return@awaitEachGesture
                        active = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        current = -1
                        follow(down.position.y)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            follow(change.position.y)
                        }
                        active = false
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEachIndexed { index, letter ->
                Text(
                    letter,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = if (active && index == current) FontWeight.Bold else FontWeight.Normal,
                    color = if (active && index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
