package com.bettercallsoul

import com.bettercallsoul.diag.DebugLog
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import com.bettercallsoul.telecom.CallActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Telecom asks the default phone app to show missed calls itself. Tapping the notification opens
 * that number in the app; the "Call back" button calls it straight away.
 */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        DebugLog.i("Missed", "count=$count")
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)?.takeIf { it.isNotBlank() }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (count <= 0) {
            manager.cancel(ID)
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                manager.notify(ID, build(context, count, number))
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun build(context: Context, count: Int, number: String?): Notification {
        val caller = number?.let { context.container.directory.local(it).title }
        val title = if (count == 1) {
            context.getString(R.string.missed_call)
        } else {
            context.resources.getQuantityString(R.plurals.missed_calls, count, count)
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_MISSED)
                .putExtra(EXTRA_NUMBER, number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setContentTitle(title)
            .setContentText(caller)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setShowWhen(true)
        if (number != null) {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.missed_call_back),
                    CallActionReceiver.callBack(context, number, ID),
                ).build(),
            )
        }
        return builder.build()
    }

    companion object {
        const val ID = 2001
        const val CHANNEL = "missed_calls"
        const val ACTION_OPEN_MISSED = "com.bettercallsoul.action.OPEN_MISSED"
        const val EXTRA_NUMBER = "number"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        /** Marks the missed calls as seen, which also tells Telecom to drop its count. */
        @SuppressLint("MissingPermission") // Granted to the default phone app instead of MODIFY_PHONE_STATE.
        fun clear(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(ID)
            runCatching { context.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification() }
        }
    }
}
