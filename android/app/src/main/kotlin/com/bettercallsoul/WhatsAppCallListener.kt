package com.bettercallsoul

import android.app.Notification
import android.app.Person
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.bettercallsoul.diag.DebugLog
import com.bettercallsoul.telecom.WhatsAppTelecomCalls
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
    private val pill by lazy { WhatsAppPill(this) }

    override fun onCreate() {
        super.onCreate()
        // Telecom may learn that a call is a video call after its notification came, or a voice call
        // may turn into one: the pill goes then.
        scope.launch {
            WhatsAppTelecomCalls.video.collect { video -> if (video) pill.hide() }
        }
    }

    override fun onDestroy() {
        connected = false
        pill.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        connected = true
        DebugLog.i(TAG, "listener connected")
    }

    override fun onListenerDisconnected() {
        connected = false
        DebugLog.i(TAG, "listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Any app's notification arriving long after it was posted means the phone stopped this app,
        // which would cost WhatsApp's calls too: Settings then says how to let it run. Only the timing
        // is looked at, never what a notification says.
        if (sbn.packageName != packageName && container.whatsAppCallsEnabled &&
            System.currentTimeMillis() - sbn.postTime > ANY_LATE_MILLIS
        ) {
            container.noteWhatsAppLate()
        }
        val notice = notice(sbn) ?: return
        // Some phones hold an idle app's notifications back and hand them over together, so the time
        // of arrival says little: the time of posting is the one that counts.
        val late = System.currentTimeMillis() - sbn.postTime
        DebugLog.i(TAG, "notice ${notice.phase} video=${notice.video} category=${sbn.notification.category} late=${late}ms")
        // Settings then tells the user how to let the app run in the background.
        if (late > LATE_MILLIS) container.noteWhatsAppLate()
        tracker.onNotice(notice, sbn.postTime)?.let(::store)
        // A notification that arrives late may be of a call that is already over.
        if (container.whatsAppPillEnabled && callStillShown()) {
            // A video call has WhatsApp's own screen in front, and a voice call turned into one does
            // too: the pill would only sit on the picture.
            val video = notice.video || sbn.notification.extras.getBoolean(EXTRA_CALL_IS_VIDEO) || WhatsAppTelecomCalls.video.value
            when {
                video -> pill.hide()
                notice.phase in PILL_PHASES -> {
                    val answered = notice.talkingSince ?: sbn.postTime
                    pill.show(sbn.notification, answered.takeIf { notice.phase == CallPhase.Ongoing })
                }
                notice.phase == CallPhase.Ringing && container.ringInPill -> pill.ring(sbn.notification, notice.name)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Also with the switch turned off in the middle of a call, when no notice comes out of it.
        if (sbn.packageName in packages() && !callStillShown()) pill.hide()
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
        val timer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        return notice.copy(number = numberIn(sbn), talkingSince = notification.`when`.takeIf { timer && it > 0 })
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
        val known = WhatsAppTelecomCalls.covers(call.date)
        DebugLog.i(TAG, "call ${call.outcome} video=${call.video} seconds=${call.seconds} telecomHasIt=$known")
        if (!known) scope.launch { container.whatsApp.save(call) }
    }

    companion object {
        private const val TAG = "WhatsAppCalls"
        private const val WHATSAPP_ID = "@s.whatsapp.net"
        private const val GRACE_MILLIS = 3_000L
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val PILL_PHASES = setOf(CallPhase.Dialing, CallPhase.Ongoing)
        /** Set by a call notification of the CallStyle kind when the call is a video call (Android 12). */
        private const val EXTRA_CALL_IS_VIDEO = "android.callIsVideo"
        private const val LATE_MILLIS = 10_000L
        private const val ANY_LATE_MILLIS = 30_000L

        @Volatile
        private var connected = false

        /** Whether the user has given this app notification access in Android's settings. */
        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /**
         * Some phones, Honor's among them, don't connect a listener again after the app was updated
         * or stopped, and the calls then go unseen with nothing to show for it. Turning the component
         * off and on makes Android look at it anew, now that the app is running.
         */
        fun reconnect(context: Context) {
            if (connected || !hasAccess(context)) return
            DebugLog.i(TAG, "listener not connected, asking again")
            val listener = ComponentName(context, WhatsAppCallListener::class.java)
            val packages = context.packageManager
            packages.setComponentEnabledSetting(listener, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            packages.setComponentEnabledSetting(listener, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
        }
    }
}
