package com.bettercallsoul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.bettercallsoul.R

/** How far the photo is zoomed and moved, for tests to read. */
internal val PhotoZoom = SemanticsPropertyKey<Pair<Float, Offset>>("PhotoZoom")

private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * A contact's photo at full size in a window over the dimmed page: two fingers zoom in and out around
 * where they are, one finger moves a zoomed photo, a double tap zooms in on that spot or back out. The
 * photo stays inside the window; a tap outside it, the close button or Back closes it.
 */
@Composable
fun PhotoViewer(photoUri: String, name: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(IntSize.Zero) }

        // A zoomed photo moves only as far as its edges reach the window's.
        fun clamp(value: Offset, zoom: Float): Offset {
            val maxX = box.width * (zoom - 1) / 2
            val maxY = box.height * (zoom - 1) / 2
            return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
        }

        Box(
            Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black)
                .onSizeChanged { box = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val center = Offset(box.width / 2f, box.height / 2f)
                                scale = DOUBLE_TAP_ZOOM
                                offset = clamp((center - tap) * (DOUBLE_TAP_ZOOM - 1), DOUBLE_TAP_ZOOM)
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                        val change = next / scale
                        // Zoom around the fingers: the point under them stays under them.
                        val fromCenter = centroid - Offset(box.width / 2f, box.height / 2f)
                        offset = clamp((offset - fromCenter) * change + fromCenter + pan, next)
                        scale = next
                    }
                },
        ) {
            AsyncImage(
                // At its own size, so zooming in shows the detail the photo has.
                model = ImageRequest.Builder(LocalContext.current).data(photoUri).size(Size.ORIGINAL).build(),
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    // Read out as a percentage by screen readers.
                    .semantics {
                        stateDescription = "${(scale * 100).roundToInt()}%"
                        set(PhotoZoom, scale to offset)
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
