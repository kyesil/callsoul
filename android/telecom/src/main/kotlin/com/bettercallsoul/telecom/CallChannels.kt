package com.bettercallsoul.telecom

import android.content.Context
import com.bettercallsoul.island.CallNotifications

/** Keeps the notification module an implementation detail of this one. */
object CallChannels {
    fun create(context: Context, incomingName: String, ongoingName: String) {
        CallNotifications.createChannels(context, incomingName, ongoingName)
    }
}
