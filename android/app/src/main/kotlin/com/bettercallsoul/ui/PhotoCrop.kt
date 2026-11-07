package com.bettercallsoul.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bettercallsoul.R
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The longest side a picked photo is opened at; plenty for a contact photo, light on memory. */
private const val OPEN_SIDE = 2048

/** The side of the square photo that is saved. */
private const val CROP_SIDE = 720

/** How far in the photo can be zoomed past filling the circle. */
private const val MAX_ZOOM = 6f

/**
 * The picked photo under a round mask: two fingers zoom, one drags, a double tap starts over. What
 * shows in the circle is saved as a square picture and handed to [onCropped] as a file.
 */
@Composable
fun PhotoCropDialog(source: Uri, onCropped: (Uri) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }
    val margin = with(LocalDensity.current) { 24.dp.toPx() }

    LaunchedEffect(source) {
        bitmap = withContext(Dispatchers.IO) { runCatching { open(context, source) }.getOrNull() }
        if (bitmap == null) failed = true
    }
    if (failed) {
        // Not a picture this phone can open: the photo is taken as it is, as before.
        LaunchedEffect(Unit) { onCropped(source) }
        return
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Text(
                stringResource(R.string.photo_crop_hint),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }, contentAlignment = Alignment.Center) {
                val image = bitmap
                if (image == null || box == IntSize.Zero) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    val diameter = minOf(box.width, box.height) - 2 * margin
                    // At zoom 1 the photo just covers the circle.
                    val base = max(diameter / image.width, diameter / image.height)
                    fun clamp(next: Offset, scale: Float): Offset {
                        val maxX = (image.width * base * scale - diameter) / 2
                        val maxY = (image.height * base * scale - diameter) / 2
                        return Offset(next.x.coerceIn(-maxX, maxX), next.y.coerceIn(-maxY, maxY))
                    }
                    val shown = remember(image) { image.asImageBitmap() }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(image, diameter) {
                                detectTransformGestures { _, pan, gesture, _ ->
                                    zoom = (zoom * gesture).coerceIn(1f, MAX_ZOOM)
                                    offset = clamp(offset + pan, zoom)
                                }
                            }
                            .pointerInput(image) {
                                detectTapGestures(onDoubleTap = {
                                    zoom = 1f
                                    offset = Offset.Zero
                                })
                            },
                    ) {
                        val scale = base * zoom
                        withTransform({
                            translate(center.x + offset.x - image.width * scale / 2, center.y + offset.y - image.height * scale / 2)
                            scale(scale, scale, pivot = Offset.Zero)
                        }) {
                            drawImage(shown)
                        }
                        // Dark all round the circle, which keeps the photo's own colours.
                        val circle = Rect(center, diameter / 2)
                        val mask = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(Offset.Zero, size))
                            addOval(circle)
                        }
                        drawPath(mask, Color.Black.copy(alpha = 0.62f))
                        drawCircle(Color.White.copy(alpha = 0.9f), radius = diameter / 2, center = center, style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = Color.White) }
                Button(
                    enabled = bitmap != null && !saving && box != IntSize.Zero,
                    onClick = {
                        val image = bitmap ?: return@Button
                        saving = true
                        val diameter = minOf(box.width, box.height) - 2 * margin
                        val scale = max(diameter / image.width, diameter / image.height) * zoom
                        // The circle's square in the photo's own pixels.
                        val side = diameter / scale
                        val left = image.width / 2f - (diameter / 2 + offset.x) / scale
                        val top = image.height / 2f - (diameter / 2 + offset.y) / scale
                        scope.launch {
                            val file = withContext(Dispatchers.IO) { save(context, image, Rect(Offset(left, top), Size(side, side))) }
                            onCropped(Uri.fromFile(file))
                        }
                    },
                ) { Text(stringResource(R.string.photo_use)) }
            }
        }
    }
}

/** The photo the right way up, no larger than [OPEN_SIDE]. */
private fun open(context: Context, source: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
        val scale = OPEN_SIDE.toFloat() / max(info.size.width, info.size.height)
        if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
        // Software pixels, so the crop can read them.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

/** Cuts [area] out of [image], scales it to [CROP_SIDE] and writes it next to the camera's pictures. */
private fun save(context: Context, image: Bitmap, area: Rect): File {
    val left = area.left.roundToInt().coerceIn(0, image.width - 1)
    val top = area.top.roundToInt().coerceIn(0, image.height - 1)
    val side = area.width.roundToInt().coerceAtLeast(1).coerceAtMost(minOf(image.width - left, image.height - top))
    val square = Bitmap.createBitmap(image, left, top, side, side)
    val out = if (side > CROP_SIDE) Bitmap.createScaledBitmap(square, CROP_SIDE, CROP_SIDE, true) else square
    val folder = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(folder, "crop-${System.currentTimeMillis()}.jpg")
    file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    return file
}
