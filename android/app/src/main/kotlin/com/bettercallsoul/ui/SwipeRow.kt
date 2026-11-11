package com.bettercallsoul.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import com.bettercallsoul.SwipeMessage
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import com.bettercallsoul.container
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How far a row has to travel, as a share of its width, before letting go triggers the action. */
private const val THRESHOLD = 0.35f

/**
 * A list row that follows the finger: right to call, left to message. The colour deepens on the way
 * to the threshold and a tick marks it. A fast flick alone does nothing, because a call shouldn't
 * start by accident. After the action the row eases back, so the colour stays long enough to see.
 *
 * A swipe that starts within the edge distance from Settings is left alone, so Android's back
 * gesture gets it, and a swipe the system takes over halfway (cancelled) never acts and always
 * eases back.
 */
@Composable
fun SwipeRow(onCall: () -> Unit, onMessage: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val edgePx = with(LocalDensity.current) { LocalContext.current.container.swipeEdgeDp.dp.toPx() }
    val windowWidth = LocalWindowInfo.current.containerSize.width
    // Plain state, written in step with each move, so a late update can't fight the settling animation.
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    var rowLeft by remember { mutableFloatStateOf(0f) }
    val armed by remember { derivedStateOf { width > 0 && abs(offset) >= width * THRESHOLD } }
    val settling = remember { arrayOfNulls<Job>(1) }
    // The gesture below is set up once and outlives a change of row: a list that reorders puts another
    // person here, so it must call whoever the row shows now, not whoever it showed first.
    val call by rememberUpdatedState(onCall)
    val message by rememberUpdatedState(onMessage)

    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    fun settle(act: Boolean) {
        if (act && armed) {
            if (offset > 0) call() else message()
        }
        settling[0]?.cancel()
        settling[0] = scope.launch {
            animate(offset, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> offset = value }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .onGloballyPositioned { rowLeft = it.positionInWindow().x }
            .pointerInput(edgePx, windowWidth, enabled) {
                // While rows are being picked, a sideways drag does nothing.
                if (!enabled) return@pointerInput
                var ignored = false
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        val x = rowLeft + start.x
                        ignored = x < edgePx || x > windowWidth - edgePx
                        if (!ignored) settling[0]?.cancel()
                    },
                    onDragEnd = { if (!ignored) settle(act = true) },
                    onDragCancel = { if (!ignored) settle(act = false) },
                    onHorizontalDrag = { change, delta ->
                        if (!ignored) {
                            change.consume()
                            offset = (offset + delta).coerceIn(-width.toFloat(), width.toFloat())
                        }
                    },
                )
            },
    ) {
        if (offset != 0f) {
            val toRight = offset > 0
            val progress = if (width > 0) (abs(offset) / (width * THRESHOLD)).coerceAtMost(1f) else 0f
            val whatsApp = LocalContext.current.container.swipeMessage == SwipeMessage.WhatsApp
            val accent = if (toRight) CallGreen else if (whatsApp) WhatsAppGreen else MessageBlue
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(lerp(MaterialTheme.colorScheme.surfaceContainerHighest, accent, progress))
                    .padding(horizontal = 22.dp),
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = if (toRight) Icons.Filled.Call else Icons.AutoMirrored.Filled.Message,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.graphicsLayer {
                        val scale = if (armed) 1.25f else 0.8f + 0.2f * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = progress
                    },
                )
            }
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }) {
            content()
        }
    }
}
