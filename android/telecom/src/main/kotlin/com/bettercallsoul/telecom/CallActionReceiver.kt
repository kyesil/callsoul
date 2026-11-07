package com.bettercallsoul.telecom

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager

/** Answer and hang-up buttons on the call notification. */
class CallActionReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission") // cancelMissedCallsNotification is open to the default phone app.
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> CallManager.answer()
            ACTION_HANG_UP -> CallManager.hangUp()
            ACTION_CALL_BACK -> {
                val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
                context.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
                runCatching { context.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification() }
                CallManager.place(context, number)
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.bettercallsoul.action.ANSWER"
        const val ACTION_HANG_UP = "com.bettercallsoul.action.HANG_UP"
        const val ACTION_CALL_BACK = "com.bettercallsoul.action.CALL_BACK"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_NOTIFICATION = "notification"

        fun callBack(context: Context, number: String, notificationId: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            number.hashCode(),
            Intent(context, CallActionReceiver::class.java)
                .setAction(ACTION_CALL_BACK)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_NOTIFICATION, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun pendingIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
