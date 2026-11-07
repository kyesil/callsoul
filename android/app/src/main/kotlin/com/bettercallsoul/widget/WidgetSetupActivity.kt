package com.bettercallsoul.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.R
import com.bettercallsoul.calllog.ContactRow
import com.bettercallsoul.container
import com.bettercallsoul.spam.SearchText
import com.bettercallsoul.ui.Avatar
import com.bettercallsoul.ui.BcsTheme
import com.bettercallsoul.ui.CompactTextField

/**
 * Sets up a widget as it is added, and again when the launcher offers to change it: whom the people
 * widget shows, which person the person widget is, and what a tap does.
 */
class WidgetSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Leaving without saving takes the widget back off the home screen.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        val person = manager.getAppWidgetInfo(widgetId)?.provider?.className == PersonWidget::class.java.name
        val start = WidgetSettings.load(this, widgetId)
        setContent {
            BcsTheme {
                WidgetSetup(person, start) { config ->
                    WidgetSettings.save(this, widgetId, config)
                    if (person) PersonWidget.update(this, manager, intArrayOf(widgetId)) else PeopleWidget.update(this, manager, widgetId)
                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetSetup(person: Boolean, start: WidgetConfig, onSave: (WidgetConfig) -> Unit) {
    val app = LocalContext.current.container
    var config by remember { mutableStateOf(start) }
    var query by remember { mutableStateOf("") }
    val contacts by remember { app.contacts.observe() }.collectAsStateWithLifecycle(emptyList())
    val shown = remember(contacts, query) {
        val text = query.trim()
        if (text.isEmpty()) contacts else contacts.filter { SearchText.find(it.name, text) != null }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Text(
            stringResource(if (person) R.string.widget_person_name else R.string.widget_people_name),
            fontSize = 26.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
        )
        if (!person) {
            Label(R.string.widget_source)
            ChipRow {
                WidgetSource.entries.forEach { source ->
                    Choice(
                        stringResource(if (source == WidgetSource.Favorites) R.string.favorites else R.string.section_most_talked),
                        selected = config.source == source,
                    ) { config = config.copy(source = source) }
                }
            }
        }
        Label(R.string.widget_action)
        ChipRow {
            WidgetAction.entries.forEach { action ->
                Choice(stringResource(actionLabel(action)), selected = config.action == action) { config = config.copy(action = action) }
            }
        }
        if (person) {
            Label(R.string.widget_pick_person)
            CompactTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.search_hint),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { contact ->
                    PersonRow(contact, selected = contact.id == config.contactId) {
                        config = config.copy(contactId = contact.id, name = contact.name)
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Button(
            enabled = !person || config.contactId >= 0,
            onClick = { onSave(config) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(stringResource(R.string.widget_add)) }
    }
}

private fun actionLabel(action: WidgetAction): Int = when (action) {
    WidgetAction.Call -> R.string.action_call
    WidgetAction.WhatsAppCall -> R.string.widget_action_whatsapp_call
    WidgetAction.WhatsAppMessage -> R.string.widget_action_whatsapp_message
    WidgetAction.Profile -> R.string.widget_action_profile
}

@Composable
private fun Label(text: Int) {
    Text(
        stringResource(text),
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else {
            null
        },
    )
}

@Composable
private fun PersonRow(contact: ContactRow, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.name, contact.photoUri, 40.dp)
        Spacer(Modifier.width(14.dp))
        Text(contact.name, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
