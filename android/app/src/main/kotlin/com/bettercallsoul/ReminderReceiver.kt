package com.bettercallsoul

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.bettercallsoul.diag.DebugLog
import com.bettercallsoul.telecom.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Shows a call reminder when its time comes, and answers its buttons. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val reminders = app.container.reminders
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        DebugLog.i(TAG, "${intent.action?.substringAfterLast('.')} $id")
        when (intent.action) {
            ACTION_DUE -> {
                val due = reminders.take(id) ?: return
                val pending = goAsync()
                scope.launch {
                    try {
                        show(app, due)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_CALL -> {
                manager(app).cancel(NOTE_TAG, id)
                CallManager.place(app, number)
            }
            ACTION_LATER -> {
                manager(app).cancel(NOTE_TAG, id)
                quickReminder(app, listOf(number), intent.getStringExtra(EXTRA_NOTE))
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> reminders.scheduleAll()
        }
    }

    companion object {
        private const val TAG = "Reminders"
        private const val CHANNEL = "call_reminders"
        private const val NOTE_TAG = "reminder"
        private const val ACTION_DUE = "com.bettercallsoul.action.REMINDER_DUE"
        private const val ACTION_CALL = "com.bettercallsoul.action.REMINDER_CALL"
        private const val ACTION_LATER = "com.bettercallsoul.action.REMINDER_LATER"
        private const val EXTRA_ID = "id"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_NOTE = "note"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun createChannel(context: Context) {
            manager(context).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
            )
        }

        fun due(context: Context, id: Int): Intent =
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_DUE).putExtra(EXTRA_ID, id)

        private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

        private suspend fun show(context: Context, reminder: Reminders.Reminder) {
            val info = runCatching { context.container.directory.local(reminder.number) }.getOrNull()
            val text = reminder.note ?: context.getString(R.string.remind_due)
            val builder = Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(info?.title ?: reminder.number)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setContentIntent(open(context, reminder))
                .addAction(action(context, R.string.action_call, ACTION_CALL, reminder))
                .addAction(action(context, R.string.remind_quick, ACTION_LATER, reminder))
            if (reminder.note != null) builder.setSubText(context.getString(R.string.remind_due))
            info?.photoUri?.let { MissedCallReceiver.photo(context, it) }?.let { builder.setLargeIcon(Icon.createWithBitmap(it)) }
            manager(context).notify(NOTE_TAG, reminder.id, builder.build())
        }

        private fun action(context: Context, label: Int, action: String, reminder: Reminders.Reminder): Notification.Action {
            val intent = Intent(context, ReminderReceiver::class.java).setAction(action)
                .putExtra(EXTRA_ID, reminder.id)
                .putExtra(EXTRA_NUMBER, reminder.number)
                .putExtra(EXTRA_NOTE, reminder.note)
            val pending = PendingIntent.getBroadcast(
                context, reminder.id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return Notification.Action.Builder(null, context.getString(label), pending).build()
        }

        /** The person's page, over the recents. */
        private fun open(context: Context, reminder: Reminders.Reminder): PendingIntent = PendingIntent.getActivity(
            context,
            ("reminder:" + reminder.id).hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(MissedCallReceiver.ACTION_OPEN_MISSED)
                .putExtra(MissedCallReceiver.EXTRA_NUMBER, reminder.number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
