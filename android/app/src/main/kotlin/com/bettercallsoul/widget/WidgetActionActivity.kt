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

/**
 * What a tap on a widget starts: a call, a WhatsApp call or message, or the person's page in the app.
 * It shows nothing of its own and closes as soon as the action is on its way.
 */
class WidgetActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val contactId = intent.getLongExtra(EXTRA_CONTACT, -1)
        val given = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val action = WidgetSettings.load(this, widgetId).action
        lifecycleScope.launch {
            runCatching { perform(action, contactId, given) }.onFailure { openPage(contactId, given) }
            finish()
        }
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
            WidgetAction.Profile -> Unit
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
