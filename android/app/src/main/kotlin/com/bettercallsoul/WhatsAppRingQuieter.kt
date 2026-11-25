package com.bettercallsoul

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.bettercallsoul.diag.DebugLog

/**
 * Quiets a WhatsApp call from someone muted on their page. WhatsApp plays its ringtone itself, so the
 * phone's ring volume is muted while its call notification rings and given back once it stops. Android
 * lets an app with notification access do that; without it nothing changes. The ring may be heard
 * for a moment, before the notification reaches this app. A ring left muted by an app that was stopped
 * meanwhile is given back the next time the listener connects.
 */
internal class WhatsAppRingQuieter(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("muted_callers", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var ringing: String? = null
    private val giveBackLater = Runnable {
        DebugLog.i(TAG, "rang too long, ring volume given back")
        giveBack()
    }

    /** The notification with this key rings a muted caller. */
    fun quiet(key: String) {
        if (ringing == key) return
        ringing = key
        main.removeCallbacks(giveBackLater)
        main.postDelayed(giveBackLater, LONGEST_RING_MILLIS)
        if (prefs.getBoolean(KEY_MUTED_BY_US, false) || audio.isStreamMute(AudioManager.STREAM_RING)) return
        val done = runCatching { audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0) }
            .onFailure { DebugLog.e(TAG, "ring volume not muted", it) }
            .isSuccess && audio.isStreamMute(AudioManager.STREAM_RING)
        DebugLog.i(TAG, "muted caller rings, ring volume muted=$done")
        if (done) prefs.edit(commit = true) { putBoolean(KEY_MUTED_BY_US, true) }
    }

    /** The notification stopped ringing, or another stage of the call replaced it. */
    fun stopped(key: String?) {
        if (ringing == null || (key != null && key != ringing)) return
        giveBack()
    }

    /** Gives the ring volume back if this app muted it, also after the app was stopped mid-ring. */
    fun giveBack() {
        ringing = null
        main.removeCallbacks(giveBackLater)
        if (!prefs.getBoolean(KEY_MUTED_BY_US, false)) return
        runCatching { audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0) }
            .onFailure { DebugLog.e(TAG, "ring volume not given back", it) }
        DebugLog.i(TAG, "ring volume given back")
        prefs.edit(commit = true) { remove(KEY_MUTED_BY_US) }
    }

    private companion object {
        const val TAG = "WhatsAppCalls"
        const val KEY_MUTED_BY_US = "ring_muted_by_us"
        const val LONGEST_RING_MILLIS = 90_000L
    }
}
