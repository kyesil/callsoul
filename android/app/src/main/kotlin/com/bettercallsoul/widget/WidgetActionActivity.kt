package com.bettercallsoul.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.bettercallsoul.MainActivity
import com.bettercallsoul.R
import com.bettercallsoul.calllog.WhatsAppKind
import com.bettercallsoul.container
import com.bettercallsoul.telecom.CallManager
import com.bettercallsoul.ui.openWhatsApp
import com.bettercallsoul.ui.openWhatsAppChat
import com.bettercallsoul.ui.sendSms
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import com.bettercallsoul.ui.BcsTheme
import com.bettercallsoul.ui.QuickMenu
import com.bettercallsoul.ui.QuickMenuOverlay
import com.bettercallsoul.ui.QuickTarget
import com.bettercallsoul.ui.rememberQuickMenu
import kotlinx.coroutines.flow.first

/**
 * What a tap on a widget starts: a call, a WhatsApp call or message, or the person's page in the app.
 * It shows nothing of its own and closes as soon as the action is on its way, except for the menu,
 * which it shows over the home screen until one is picked.
 */
class WidgetActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val contactId = intent.getLongExtra(EXTRA_CONTACT, -1)
        val given = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val action = WidgetSettings.load(this, widgetId).action
        lifecycleScope.launch {
            if (action == WidgetAction.Menu && runCatching { showMenu(contactId, given) }.getOrDefault(false)) return@launch
            runCatching { perform(action, contactId, given) }.onFailure { openPage(contactId, given) }
            finish()
        }
    }

    /**
     * The actions around the tapped photo, over the home screen, as a swipe up opens them in the app;
     * a tap on the photo opens the person's page. False when there's no number for them.
     */
    private suspend fun showMenu(contactId: Long, given: String): Boolean {
        val number = given.ifBlank { if (contactId >= 0) container.contacts.numbers(contactId).firstOrNull().orEmpty() else "" }
        if (number.isBlank()) return false
        val contact = if (contactId >= 0) container.contacts.detail(contactId) else null
        val name = contact?.name ?: number
        // Read before anything shows, so the whole menu opens at once rather than in two steps.
        val target = QuickTarget(number, container.contacts.whatsAppActions(number))
        val center = photoCenter()
        enableEdgeToEdge()
        setContent {
            BcsTheme {
                val menu = rememberQuickMenu { CallManager.place(this, it) }
                LaunchedEffect(Unit) {
                    menu.open(QuickMenu.Opened(center, name, contact?.photoUri) { target })
                    // Opened, then closed by an action, a tap beside it or back: nothing more to show.
                    snapshotFlow { menu.opened }.first { it == null }
                    finish()
                }
                QuickMenuOverlay(menu, Offset.Zero, onPhoto = { openPage(contactId, number) })
            }
        }
        return true
    }

    /**
     * Where the tapped photo's middle is on the screen: the launcher passes the tapped view's bounds.
     * In the people widget the photo sits at the top of its cell, in the one-person widget above the name.
     */
    private fun photoCenter(): Offset {
        val bounds = intent.sourceBounds
        val metrics = resources.displayMetrics
        if (bounds == null) return Offset(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
        val density = metrics.density
        val y = if (intent.data?.host == "person") bounds.exactCenterY() - 10 * density else bounds.top + (6 + 26) * density
        return Offset(bounds.exactCenterX(), y)
    }

    private suspend fun perform(action: WidgetAction, contactId: Long, given: String) {
        val number = given.ifBlank { if (contactId >= 0) container.contacts.numbers(contactId).firstOrNull().orEmpty() else "" }
        // Without a number there is nothing to call or write to: the page shows what there is.
        if (number.isBlank() || action == WidgetAction.Profile) {
            openPage(contactId, number)
            return
        }
        when (action) {
            WidgetAction.Call -> CallManager.place(this, number)
            WidgetAction.WhatsAppCall -> {
                val voice = container.contacts.whatsAppActions(number).firstOrNull { it.kind == WhatsAppKind.Voice }
                when {
                    voice != null -> openWhatsApp(this, voice)
                    // Not on WhatsApp as a contact: the chat, where the call button is.
                    !openWhatsAppChat(this, number) -> Toast.makeText(this, R.string.whatsapp_missing, Toast.LENGTH_SHORT).show()
                }
            }
            WidgetAction.WhatsAppMessage -> if (!openWhatsAppChat(this, number)) {
                Toast.makeText(this, R.string.whatsapp_missing, Toast.LENGTH_SHORT).show()
                sendSms(this, number)
            }
            WidgetAction.Profile, WidgetAction.Menu -> Unit
        }
    }

    private fun openPage(contactId: Long, number: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_OPEN)
                .putExtra(EXTRA_CONTACT, contactId)
                .putExtra(EXTRA_NUMBER, number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        const val ACTION_OPEN = "dev.yesil.callsoul.action.OPEN_PERSON"
        const val EXTRA_CONTACT = "contact_id"
        const val EXTRA_NUMBER = "number"
    }
}
