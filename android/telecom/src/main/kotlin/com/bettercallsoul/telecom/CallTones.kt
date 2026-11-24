package com.bettercallsoul.telecom

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.HandlerThread

/** The short beeps a call can give when it connects and when it ends, in the call's own sound. */
internal object CallTones {
    private const val VOLUME = 70
    private const val LENGTH_MILLIS = 150
    private const val RELEASE_AFTER_MILLIS = 600L

    fun connected() = play(ToneGenerator.TONE_PROP_BEEP)

    fun ended() = play(ToneGenerator.TONE_PROP_BEEP2)

    // Making a ToneGenerator opens an audio track, which can take a while; never on the main thread,
    // where it would stall the call screen right as the call connects.
    private val thread = HandlerThread("bcs-tones").apply { start() }
    private val handler = Handler(thread.looper)

    private fun play(tone: Int) {
        handler.post {
            runCatching {
                val generator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, VOLUME)
                generator.startTone(tone, LENGTH_MILLIS)
                handler.postDelayed({ generator.release() }, RELEASE_AFTER_MILLIS)
            }
        }
    }
}
