package com.bettercallsoul.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle

data class CallNotificationContent(
    val title: String,
    val subtitle: String?,
    val incoming: Boolean,
    /** Wall-clock start of the conversation, or null until it connects. */
    val connectedAt: Long?,
    val open: PendingIntent,
    val answer: PendingIntent,
    val hangUp: PendingIntent,
    /** The call screen is already in front, so a pop-up would only cover it. */
    val quiet: Boolean = false,
    /** Button labels for Android 10 and 11, which have no call notification style. */
    val answerLabel: String = "",
    val hangUpLabel: String = "",
)

/**
 * Builds the system call notification. Samsung's Now Bar, Hyper Island and the Android 16+
 * status bar chip all read this one standard notification, and tapping it reopens the call.
 */
object CallNotifications {
    const val ID = 1001
    private const val CHANNEL_INCOMING = "incoming_call"
    private const val CHANNEL_ONGOING = "ongoing_call"

    fun createChannels(context: Context, incomingName: String, ongoingName: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Telecom plays the ringtone, so the channel itself stays silent.
        val incoming = NotificationChannel(CHANNEL_INCOMING, incomingName, NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
        }
        val ongoing = NotificationChannel(CHANNEL_ONGOING, ongoingName, NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
        }
        manager.createNotificationChannels(listOf(incoming, ongoing))
    }

    fun build(context: Context, smallIcon: Int, content: CallNotificationContent): Notification {
        val channel = if (content.incoming && !content.quiet) CHANNEL_INCOMING else CHANNEL_ONGOING
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(smallIcon)
            .setContentIntent(content.open)
            .setContentTitle(content.title)
            .setContentText(content.subtitle)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(!content.incoming)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = Person.Builder().setName(content.title).setImportant(true).build()
            builder.setStyle(
                if (content.incoming) {
                    Notification.CallStyle.forIncomingCall(person, content.hangUp, content.answer)
                } else {
                    Notification.CallStyle.forOngoingCall(person, content.hangUp)
                },
            )
        } else {
            val icon = Icon.createWithResource(context, smallIcon)
            builder.addAction(Notification.Action.Builder(icon, content.hangUpLabel, content.hangUp).build())
            if (content.incoming) builder.addAction(Notification.Action.Builder(icon, content.answerLabel, content.answer).build())
        }

        if (content.incoming && !content.quiet) {
            builder.setFullScreenIntent(content.open, true)
        }
        content.connectedAt?.let {
            builder.setWhen(it).setShowWhen(true).setUsesChronometer(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // The system sets the promoted flag itself, and only for notifications that ask this way.
            builder.addExtras(Bundle().apply { putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        }
        return builder.build()
    }
}
