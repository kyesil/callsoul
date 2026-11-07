package com.bettercallsoul

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.bettercallsoul.diag.DebugLog
import com.bettercallsoul.whatsapp.CallNotice
import com.bettercallsoul.whatsapp.CallPhase
import com.bettercallsoul.whatsapp.FinishedCall
import com.bettercallsoul.whatsapp.WhatsAppCallTracker
import com.bettercallsoul.whatsapp.WhatsAppNotices
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Notices WhatsApp's call notifications, for the recents. Android hands every app's notifications to
 * a listener; all but WhatsApp's are dropped on arrival, and nothing read here leaves the phone.
 * Works only with Settings' switch on and the notification access Android asks the user for.
 */
class WhatsAppCallListener : NotificationListenerService() {
    private val scope = MainScope()
    private val tracker = WhatsAppCallTracker()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notice = notice(sbn) ?: return
        DebugLog.i(TAG, "notice ${notice.phase} video=${notice.video} category=${sbn.notification.category}")
        tracker.onNotice(notice, System.currentTimeMillis())?.let(::store)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val notice = notice(sbn) ?: return
        if (notice.phase == CallPhase.Missed) return
        // One stage of a call may give way to the next as a separate notification: the call goes on.
        if (callStillShown()) return
        tracker.onRemoved(System.currentTimeMillis())
        scope.launch {
            delay(GRACE_MILLIS + 100)
            tracker.flush(System.currentTimeMillis(), GRACE_MILLIS)?.let(::store)
        }
    }

    private fun callStillShown(): Boolean = runCatching { activeNotifications }.getOrNull().orEmpty().any { shown ->
        notice(shown)?.let { it.phase != CallPhase.Missed } == true
    }

    private fun notice(sbn: StatusBarNotification): CallNotice? {
        if (sbn.packageName !in packages() || !container.whatsAppCallsEnabled) return null
        val notification = sbn.notification
        val extras = notification.extras
        val notice = WhatsAppNotices.read(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            category = notification.category,
            chronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),
        ) ?: return null
        return notice.copy(number = numberIn(sbn))
    }

    /** WhatsApp names the other side as a tel: person or as a "905321234567@s.whatsapp.net" id, when it does. */
    private fun numberIn(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        @Suppress("DEPRECATION")
        val people = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, Person::class.java)
        } else {
            extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)
        }
        val tel = people?.firstNotNullOfOrNull { it.uri?.takeIf { uri -> uri.startsWith("tel:") }?.removePrefix("tel:") }
        if (tel != null) return tel
        val id = listOfNotNull(sbn.notification.shortcutId, sbn.tag).firstOrNull { it.endsWith(WHATSAPP_ID) }
        return id?.removeSuffix(WHATSAPP_ID)?.takeIf { it.all(Char::isDigit) }?.let { "+$it" }.orEmpty()
    }

    private fun packages() = if (BuildConfig.DEBUG) WHATSAPP_PACKAGES + "com.android.shell" else WHATSAPP_PACKAGES

    private fun store(call: FinishedCall) {
        DebugLog.i(TAG, "call ${call.outcome} video=${call.video} seconds=${call.seconds}")
        scope.launch { container.whatsApp.save(call) }
    }

    companion object {
        private const val TAG = "WhatsAppCalls"
        private const val WHATSAPP_ID = "@s.whatsapp.net"
        private const val GRACE_MILLIS = 3_000L
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        /** Whether the user has given this app notification access in Android's settings. */
        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
