package com.bettercallsoul

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.bettercallsoul.calllog.ContactRow
import com.bettercallsoul.ui.avatarColor
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The favourites in the menu of the app's icon: a tap calls, like a favourite on the home tab. The
 * contact's id is stored, not its number, so the call goes to the number the contact has then.
 */
object FavoriteShortcuts {
    const val ACTION_CALL = "dev.yesil.callsoul.action.CALL_FAVORITE"
    const val EXTRA_CONTACT = "contact_id"
    const val ACTION_ADD_CONTACT = "dev.yesil.callsoul.action.ADD_CONTACT"
    private const val ID_ADD_CONTACT = "add-contact"
    private const val ADD_COLOR = 0xFF3B6FD4.toInt()

    /** An adaptive icon is 108dp, and the launcher's mask shows the middle of it. */
    private const val ICON_DP = 108

    /**
     * "Add contact" comes first, so the launcher puts it nearest the icon; it is there with no
     * favourites too, so the menu is never empty. The favourites follow.
     */
    suspend fun update(context: Context, favorites: List<ContactRow>) = withContext(Dispatchers.IO) {
        val limit = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        val add = ShortcutInfoCompat.Builder(context, ID_ADD_CONTACT)
            .setShortLabel(context.getString(R.string.action_add_contact))
            .setIcon(IconCompat.createWithAdaptiveBitmap(addIcon(context)))
            .setRank(0)
            .setIntent(Intent(context, MainActivity::class.java).setAction(ACTION_ADD_CONTACT))
            .build()
        val people = favorites.take(limit - 1).mapIndexed { rank, contact ->
            val call = Intent(context, MainActivity::class.java)
                .setAction(ACTION_CALL)
                .putExtra(EXTRA_CONTACT, contact.id)
            ShortcutInfoCompat.Builder(context, id(contact.id))
                .setShortLabel(contact.name)
                .setIcon(IconCompat.createWithAdaptiveBitmap(icon(context, contact)))
                .setRank(rank + 1)
                .setIntent(call)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(add) + people)
    }

    /** The person with a plus, white on the app's blue, the size of an adaptive icon. */
    private fun addIcon(context: Context): Bitmap {
        val size = (ICON_DP * context.resources.displayMetrics.density).roundToInt()
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ADD_COLOR)
        val glyph = (size * 0.36f).roundToInt()
        val start = (size - glyph) / 2
        ContextCompat.getDrawable(context, R.drawable.ic_shortcut_add_contact)?.run {
            setBounds(start, start, start + glyph, start + glyph)
            draw(canvas)
        }
        return bitmap
    }

    /** Tells the launcher which ones get used, so it can rank them. */
    fun reportUsed(context: Context, contactId: Long) = ShortcutManagerCompat.reportShortcutUsed(context, id(contactId))

    private fun id(contactId: Long) = "favorite-$contactId"

    /** The contact's photo, or the same coloured initial the app's lists show. */
    private suspend fun icon(context: Context, contact: ContactRow): Bitmap {
        val size = (ICON_DP * context.resources.displayMetrics.density).roundToInt()
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val photo = context.container.editor.photoBytes(contact.id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (photo != null) {
            val side = minOf(photo.width, photo.height)
            val left = (photo.width - side) / 2
            val top = (photo.height - side) / 2
            canvas.drawBitmap(photo, Rect(left, top, left + side, top + side), Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawColor(avatarColor(contact.name).toArgb())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = size * 0.34f
                textAlign = Paint.Align.CENTER
            }
            val initial = contact.name.firstOrNull { it.isLetter() }?.uppercase() ?: "#"
            canvas.drawText(initial, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        }
        return bitmap
    }
}
