package com.bettercallsoul.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.bettercallsoul.R
import com.bettercallsoul.ui.avatarColor
import kotlin.math.roundToInt

/**
 * A round photo for a widget, or the coloured initial the app's lists show. With [badge], a small
 * mark at the bottom right says what a tap does.
 */
fun widgetAvatar(context: Context, name: String, photoUri: String?, sizeDp: Int, badge: WidgetAction? = null): Bitmap {
    val size = (sizeDp * context.resources.displayMetrics.density).roundToInt()
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val photo = photoUri?.let { uri ->
        runCatching { context.contentResolver.openInputStream(uri.toUri())?.use(BitmapFactory::decodeStream) }.getOrNull()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (photo != null) {
        val side = minOf(photo.width, photo.height).toFloat()
        val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = size / side
        shader.setLocalMatrix(Matrix().apply {
            postTranslate(-(photo.width - side) / 2, -(photo.height - side) / 2)
            postScale(scale, scale)
        })
        paint.shader = shader
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.shader = null
    } else {
        paint.color = avatarColor(name).toArgb()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = Color.WHITE
        paint.textSize = size * 0.42f
        paint.textAlign = Paint.Align.CENTER
        val initial = name.firstOrNull { it.isLetter() }?.uppercase() ?: "#"
        canvas.drawText(initial, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
    }
    if (badge != null) {
        val radius = size * 0.2f
        val cx = size - radius
        val cy = size - radius
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = if (badge == WidgetAction.WhatsAppCall || badge == WidgetAction.WhatsAppMessage) WHATSAPP_GREEN else ACTION_BLUE
        canvas.drawCircle(cx, cy, radius * 0.86f, paint)
        val icon = ContextCompat.getDrawable(context, badgeIcon(badge))?.mutate()
        if (icon != null) {
            val half = (radius * 0.52f).roundToInt()
            icon.setBounds((cx - half).roundToInt(), (cy - half).roundToInt(), (cx + half).roundToInt(), (cy + half).roundToInt())
            icon.setTint(Color.WHITE)
            icon.draw(canvas)
        }
    }
    return bitmap
}

private fun badgeIcon(action: WidgetAction): Int = when (action) {
    WidgetAction.Call, WidgetAction.WhatsAppCall -> R.drawable.ic_widget_call
    WidgetAction.WhatsAppMessage -> R.drawable.ic_widget_message
    WidgetAction.Profile -> R.drawable.ic_widget_person
    WidgetAction.Menu -> R.drawable.ic_widget_menu
}

private val WHATSAPP_GREEN = Color.rgb(0x25, 0xD3, 0x66)
private val ACTION_BLUE = Color.rgb(0x1E, 0x6B, 0xE0)
