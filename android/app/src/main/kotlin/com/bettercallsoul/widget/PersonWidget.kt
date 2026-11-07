package com.bettercallsoul.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.bettercallsoul.R
import com.bettercallsoul.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One person on the home screen, like an app icon: the photo with a small mark for what a tap does
 * (call, WhatsApp call or message, or the profile), and the name under it.
 */
class PersonWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) = update(context, manager, widgetIds)

    override fun onDeleted(context: Context, widgetIds: IntArray) = WidgetSettings.forget(context, widgetIds)

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Reads the contact again, so a new photo or name shows. */
        fun update(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
            val app = context.applicationContext
            scope.launch {
                widgetIds.forEach { id -> runCatching { manager.updateAppWidget(id, views(app, id)) } }
            }
        }

        private suspend fun views(context: Context, widgetId: Int): RemoteViews {
            val config = WidgetSettings.load(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_person)
            if (config.contactId < 0) {
                views.setTextViewText(R.id.widget_name, context.getString(R.string.widget_pick_person))
                views.setImageViewBitmap(R.id.widget_avatar, widgetAvatar(context, "?", null, 60))
                return views
            }
            val contact = runCatching { context.container.contacts.detail(config.contactId) }.getOrNull()
            val name = contact?.name ?: config.name
            views.setImageViewBitmap(R.id.widget_avatar, widgetAvatar(context, name, contact?.photoUri, 60, badge = config.action))
            views.setTextViewText(R.id.widget_name, name)
            views.setContentDescription(R.id.widget_root, name)
            val tap = Intent(context, WidgetActionActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(WidgetActionActivity.EXTRA_CONTACT, config.contactId)
                .setData("widget://person/$widgetId".toUri())
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, widgetId, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            return views
        }
    }
}
