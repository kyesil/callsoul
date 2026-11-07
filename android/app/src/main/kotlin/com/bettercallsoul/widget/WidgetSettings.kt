package com.bettercallsoul.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit

/** What a tap on a person in a widget does. */
enum class WidgetAction { Call, WhatsAppCall, WhatsAppMessage, Profile }

/** Whom the people widget shows. */
enum class WidgetSource { Favorites, MostTalked }

/**
 * Each widget's own choices, by its id: the people widget's source, the person widget's contact,
 * and what a tap does. A widget added without being set up shows the favourites and calls.
 */
data class WidgetConfig(
    val action: WidgetAction = WidgetAction.Call,
    val source: WidgetSource = WidgetSource.Favorites,
    val contactId: Long = -1,
    val name: String = "",
)

object WidgetSettings {
    private const val FILE = "widgets"

    fun load(context: Context, widgetId: Int): WidgetConfig {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return WidgetConfig(
            action = prefs.getString("$widgetId.action", null)?.let { runCatching { WidgetAction.valueOf(it) }.getOrNull() } ?: WidgetAction.Call,
            source = prefs.getString("$widgetId.source", null)?.let { runCatching { WidgetSource.valueOf(it) }.getOrNull() } ?: WidgetSource.Favorites,
            contactId = prefs.getLong("$widgetId.contact", -1),
            name = prefs.getString("$widgetId.name", null).orEmpty(),
        )
    }

    fun save(context: Context, widgetId: Int, config: WidgetConfig) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putString("$widgetId.action", config.action.name)
            putString("$widgetId.source", config.source.name)
            putLong("$widgetId.contact", config.contactId)
            putString("$widgetId.name", config.name)
        }
    }

    fun forget(context: Context, widgetIds: IntArray) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            widgetIds.forEach { id -> listOf("action", "source", "contact", "name").forEach { remove("$id.$it") } }
        }
    }
}

/** Brings every widget of the app up to date: the people in them and their photos. */
object Widgets {
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val people = manager.getAppWidgetIds(ComponentName(context, PeopleWidget::class.java))
        if (people.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(people, com.bettercallsoul.R.id.widget_grid)
        val persons = manager.getAppWidgetIds(ComponentName(context, PersonWidget::class.java))
        if (persons.isNotEmpty()) PersonWidget.update(context, manager, persons)
    }
}
