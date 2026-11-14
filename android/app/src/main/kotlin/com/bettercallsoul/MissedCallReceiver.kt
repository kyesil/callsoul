package com.bettercallsoul

import com.bettercallsoul.diag.DebugLog
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.CallLog.Calls
import android.telecom.TelecomManager
import androidx.core.database.getStringOrNull
import com.bettercallsoul.calllog.CallEntry
import com.bettercallsoul.calllog.CallType
import com.bettercallsoul.calllog.withRingTimes
import com.bettercallsoul.telecom.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Telecom asks the default phone app to show missed calls itself, but only says how many there are
 * and, for a single one, the number. So the unseen missed calls are read from the call log instead,
 * and each caller gets a notification of their own: the name as the title (with how many times
 * they called, when more than once) and how long the last call rang below it. With more than one
 * caller they are bundled under an "N missed calls" summary.
 */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        DebugLog.i("Missed", "count=$count")
        if (count <= 0) {
            cancelAll(context)
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                show(context.applicationContext, count, alert = true)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** The summary keeps the id the single notification had, so an update replaces an old one. */
        const val ID = 2001
        const val CHANNEL = "missed_calls"
        const val ACTION_OPEN_MISSED = "com.bettercallsoul.action.OPEN_MISSED"
        const val EXTRA_NUMBER = "number"
        private const val GROUP = "missed_calls"
        private const val TAG_PREFIX = "missed:"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val lock = Mutex()

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        /** Marks every missed call as seen, which also tells Telecom to drop its count. */
        @SuppressLint("MissingPermission") // Granted to the default phone app instead of MODIFY_PHONE_STATE.
        fun clear(context: Context) {
            cancelAll(context)
            runCatching { context.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification() }
            // Telecom doesn't always mark them in the log itself, and the notifications are read from there.
            val app = context.applicationContext
            scope.launch {
                lock.withLock {
                    markRead(app, "${Calls.TYPE} = ${Calls.MISSED_TYPE} AND ${Calls.NEW} = 1")
                }
            }
        }

        private fun markRead(context: Context, where: String) {
            runCatching {
                val values = ContentValues().apply {
                    put(Calls.NEW, 0)
                    put(Calls.IS_READ, 1)
                }
                context.contentResolver.update(Calls.CONTENT_URI, values, where, null)
            }.onFailure { DebugLog.e("Missed", "couldn't mark seen", it) }
        }

        /**
         * Only [number]'s missed calls are seen: its notification goes and the others stay. With the
         * last one gone Telecom is told too, so its count starts again from nothing.
         */
        fun markSeen(context: Context, number: String) {
            val app = context.applicationContext
            app.getSystemService(NotificationManager::class.java).cancel(TAG_PREFIX + keyOf(number), ID)
            scope.launch {
                lock.withLock {
                    val tail = keyOf(number)
                    val ids = unseen(app).filter { keyOf(it.number) == tail }.map { it.id }
                    if (ids.isNotEmpty()) markRead(app, "${Calls._ID} IN (${ids.joinToString()})")
                }
                if (unseen(app).isEmpty()) clear(app) else show(app, count = 0, alert = false)
            }
        }

        private fun cancelAll(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter { it.tag?.startsWith(TAG_PREFIX) == true }.forEach { manager.cancel(it.tag, it.id) }
            manager.cancel(ID)
        }

        /** The missed calls nobody has looked at yet, newest first. */
        private fun unseen(context: Context): List<CallEntry> = runCatching {
            context.contentResolver.query(
                Calls.CONTENT_URI,
                arrayOf(Calls._ID, Calls.NUMBER, Calls.DATE),
                "${Calls.TYPE} = ${Calls.MISSED_TYPE} AND ${Calls.NEW} = 1",
                null,
                "${Calls.DATE} DESC",
            )?.use {
                buildList {
                    while (it.moveToNext()) {
                        add(CallEntry(it.getLong(0), it.getStringOrNull(1).orEmpty(), null, null, CallType.Missed, it.getLong(2), 0))
                    }
                }
            }.orEmpty()
        }.onFailure { DebugLog.e("Missed", "couldn't read the log", it) }.getOrDefault(emptyList())

        /** The log writes 0532..., +90532... and so on for one line. */
        private fun keyOf(number: String): String = number.filter(Char::isDigit).takeLast(10)

        private suspend fun show(context: Context, count: Int, alert: Boolean) = lock.withLock {
            val app = context.container
            suspend fun read() = unseen(context).withRingTimes(runCatching { app.store.ringTimes() }.getOrDefault(emptyList()))
            var calls = read()
            // The log entry and the ring time are written as the call ends, just before this.
            if (alert && (calls.isEmpty() || calls.first().ringSeconds == null)) {
                delay(800)
                calls = read()
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            if (calls.isEmpty()) {
                // Nothing readable in the log: fall back to Telecom's own count.
                if (count > 0) manager.notify(ID, summary(context, count, emptyList(), single = true))
                return@withLock
            }
            val people = calls.groupBy { keyOf(it.number) }.values.toList()
            // Taking the summary away takes its group with it, so with one caller left it goes first
            // and that caller's notification is put back after.
            if (people.size <= 1) manager.cancel(ID)
            val newest = calls.first().id
            val shownTags = mutableSetOf<String>()
            val lines = mutableListOf<String>()
            for (personCalls in people) {
                val last = personCalls.first()
                val number = last.number
                val info = number.takeIf(String::isNotBlank)?.let { runCatching { app.directory.local(it) }.getOrNull() }
                val name = info?.title ?: context.getString(R.string.stats_hidden_number)
                val title = if (personCalls.size > 1) "$name (${personCalls.size})" else name
                val text = last.ringSeconds?.let {
                    context.getString(R.string.missed_rang, context.getString(R.string.ring_seconds, it.toInt()))
                } ?: context.getString(R.string.missed_call)
                val tag = TAG_PREFIX + keyOf(number)
                shownTags += tag
                lines += "$title · $text"
                val builder = Notification.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_notify_missed_call)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setCategory(Notification.CATEGORY_MISSED_CALL)
                    .setGroup(GROUP)
                    .setWhen(last.date)
                    .setShowWhen(true)
                    .setAutoCancel(true)
                    // Only the caller who just called makes a sound; the others are only brought up to date.
                    .setOnlyAlertOnce(!(alert && last.id == newest))
                    .setContentIntent(openIntent(context, number))
                    .setDeleteIntent(MissedCallActions.intent(context, MissedCallActions.ACTION_SEEN, number))
                info?.photoUri?.let { photo(context, it) }?.let { builder.setLargeIcon(Icon.createWithBitmap(it)) }
                if (number.isNotBlank()) {
                    builder.addAction(
                        Notification.Action.Builder(
                            null,
                            context.getString(R.string.missed_call_back),
                            MissedCallActions.intent(context, MissedCallActions.ACTION_CALL_BACK, number),
                        ).build(),
                    )
                }
                manager.notify(tag, ID, builder.build())
            }
            manager.activeNotifications
                .filter { it.tag?.startsWith(TAG_PREFIX) == true && it.tag !in shownTags }
                .forEach { manager.cancel(it.tag, it.id) }
            // One caller alone needs no bundle; Android would draw an empty header over them.
            if (people.size > 1) manager.notify(ID, summary(context, calls.size, lines, single = false))
        }

        private fun summary(context: Context, count: Int, lines: List<String>, single: Boolean): Notification {
            val title = context.resources.getQuantityString(R.plurals.missed_calls, count, count)
            val builder = Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_missed_call)
                .setContentTitle(title)
                .setCategory(Notification.CATEGORY_MISSED_CALL)
                .setContentIntent(openIntent(context, ""))
                .setDeleteIntent(MissedCallActions.intent(context, MissedCallActions.ACTION_SEEN_ALL, ""))
                .setAutoCancel(true)
                .setShowWhen(true)
            if (!single) {
                builder.setGroup(GROUP).setGroupSummary(true)
                    .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                    .setOnlyAlertOnce(true)
                    .setContentText(lines.joinToString(", ") { it.substringBefore(" · ") })
                    .setStyle(Notification.InboxStyle().also { style -> lines.take(6).forEach(style::addLine) })
            }
            return builder.build()
        }

        private fun openIntent(context: Context, number: String): PendingIntent = PendingIntent.getActivity(
            context,
            ("open:" + keyOf(number)).hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_MISSED)
                .putExtra(EXTRA_NUMBER, number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun photo(context: Context, uri: String): Bitmap? = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) { decoder, info, _ ->
                val side = minOf(info.size.width, info.size.height).coerceAtLeast(1)
                val scale = 192f / side
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }.getOrNull()
    }
}

/** The buttons and swipes on a missed-call notification; they only ever come from this app. */
class MissedCallActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(MissedCallReceiver.EXTRA_NUMBER).orEmpty()
        when (intent.action) {
            ACTION_SEEN -> MissedCallReceiver.markSeen(context, number)
            ACTION_SEEN_ALL -> MissedCallReceiver.clear(context)
            ACTION_CALL_BACK -> {
                MissedCallReceiver.markSeen(context, number)
                CallManager.place(context, number)
            }
        }
    }

    companion object {
        const val ACTION_SEEN = "com.bettercallsoul.action.MISSED_SEEN"
        const val ACTION_SEEN_ALL = "com.bettercallsoul.action.MISSED_SEEN_ALL"
        const val ACTION_CALL_BACK = "com.bettercallsoul.action.MISSED_CALL_BACK"

        fun intent(context: Context, action: String, number: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            (action + number).hashCode(),
            Intent(context, MissedCallActions::class.java).setAction(action).putExtra(MissedCallReceiver.EXTRA_NUMBER, number),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
