package com.bettercallsoul.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.net.toUri
import com.bettercallsoul.R
import com.bettercallsoul.calllog.favoritesInOrder
import com.bettercallsoul.container
import com.bettercallsoul.mostTalked
import com.bettercallsoul.telecom.CallerDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The favourites or the most talked to on the home screen, as round photos in a grid that fills the
 * widget; a tap does what the widget was set up for. Android widgets can't slide sideways, so more
 * people than fit scroll up and down.
 */
class PeopleWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) = WidgetSettings.forget(context, widgetIds)

    companion object {
        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_people)
            // Each widget its own list: the address makes the adapter intents differ.
            val adapter = Intent(context, PeopleWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData("widget://people/$widgetId".toUri())
            views.setRemoteAdapter(R.id.widget_grid, adapter)
            views.setEmptyView(R.id.widget_grid, R.id.widget_empty)
            val tap = Intent(context, WidgetActionActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData("widget://tap/$widgetId".toUri())
            views.setPendingIntentTemplate(
                R.id.widget_grid,
                PendingIntent.getActivity(context, widgetId, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE),
            )
            manager.updateAppWidget(widgetId, views)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_grid)
        }
    }
}

class PeopleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        PeopleFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

private class Person(val contactId: Long, val number: String, val name: String, val photoUri: String?)

private class PeopleFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var people = emptyList<Person>()
    private var action = WidgetAction.Call

    override fun onCreate() = Unit

    /** Runs off the main thread, so it may wait for the address book and the call log. */
    override fun onDataSetChanged() {
        val config = WidgetSettings.load(context, widgetId)
        action = config.action
        val app = context.container
        people = runCatching {
            runBlocking {
                when (config.source) {
                    WidgetSource.Favorites -> app.contacts.observe().first().favoritesInOrder()
                        .map { Person(it.id, "", it.name, it.photoUri) }
                    WidgetSource.MostTalked -> {
                        val calls = app.observeCalls(limit = 1000).first()
                        val labels = app.store.observeTags().first().associate { it.number to it.label }
                        mostTalked(calls, labels, MAX_PEOPLE).map { person ->
                            val contact = app.contacts.match(person.number)
                            Person(contact?.id ?: -1, person.number, person.name, person.photoUri)
                        }
                    }
                }
            }
        }.getOrDefault(emptyList()).take(MAX_PEOPLE)
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = people.size

    override fun getViewAt(position: Int): RemoteViews {
        val person = people[position]
        return RemoteViews(context.packageName, R.layout.widget_people_item).apply {
            setImageViewBitmap(R.id.widget_avatar, widgetAvatar(context, person.name, person.photoUri, 52))
            setTextViewText(R.id.widget_name, person.name)
            setContentDescription(R.id.widget_item, person.name)
            setOnClickFillInIntent(
                R.id.widget_item,
                Intent().putExtra(WidgetActionActivity.EXTRA_CONTACT, person.contactId).putExtra(WidgetActionActivity.EXTRA_NUMBER, person.number),
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = people[position].let { if (it.contactId >= 0) it.contactId else CallerDirectory.keyOf(it.number).hashCode().toLong() }

    override fun hasStableIds(): Boolean = true

    companion object {
        const val MAX_PEOPLE = 24
    }
}
