package com.bettercallsoul.telecom

import android.telecom.Call
import android.telecom.VideoProfile
import com.bettercallsoul.diag.DebugLog
import com.bettercallsoul.whatsapp.FinishedCall
import com.bettercallsoul.whatsapp.TelecomCall
import com.bettercallsoul.whatsapp.TelecomCoverage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * WhatsApp's calls as it reports them to Telecom, with the moments they were answered and ended. They
 * come here only to be written down: they never reach [CallManager], so no screen of ours opens and
 * WhatsApp keeps its own. Calls of other apps with their own screens are left alone.
 */
object WhatsAppTelecomCalls {
    private const val TAG = "WhatsAppCalls"
    private val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val coverage = TelecomCoverage()
    private val withVideo = mutableSetOf<Call>()
    private val live = mutableSetOf<Call>()
    private var save: suspend (FinishedCall) -> Unit = {}

    private val _inCall = MutableStateFlow(false)

    /** A WhatsApp call is going on and Telecom knows of it, so its sound can be moved from here. */
    val inCall: StateFlow<Boolean> = _inCall.asStateFlow()

    private val _video = MutableStateFlow(false)

    /**
     * A WhatsApp call going on right now is a video call. WhatsApp tells Telecom so, while its
     * notification text says only "Calling" on some phones.
     */
    val video: StateFlow<Boolean> = _video.asStateFlow()

    private val callback = object : Call.Callback() {
        override fun onDetailsChanged(call: Call, details: Call.Details) = noteVideo(call)
    }

    fun init(save: suspend (FinishedCall) -> Unit) {
        this.save = save
    }

    /** Whether a call that began at [start] is one Telecom told about, so its notifications add nothing. */
    fun covers(start: Long): Boolean = coverage.covers(start)

    /** A call with a screen of its own in the app that made it. */
    internal fun hasOwnScreen(call: Call): Boolean = call.details.hasProperty(Call.Details.PROPERTY_SELF_MANAGED)

    internal fun onCallAdded(call: Call) {
        if (!isWhatsApp(call)) return
        DebugLog.i(TAG, "telecom added, incoming=${isIncoming(call)}")
        coverage.opened()
        _inCall.value = coverage.active
        live += call
        noteVideo(call)
        call.registerCallback(callback)
    }

    internal fun onCallRemoved(call: Call) {
        if (!isWhatsApp(call)) return
        call.unregisterCallback(callback)
        noteVideo(call)
        live -= call
        _video.value = live.any { VideoProfile.isVideo(it.details.videoState) }
        val details = call.details
        val end = System.currentTimeMillis()
        coverage.closed(end)
        _inCall.value = coverage.active
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val finished = TelecomCall(
            name = details.callerDisplayName?.takeIf { it.isNotBlank() } ?: number,
            number = number,
            video = withVideo.remove(call),
            incoming = isIncoming(call),
            createdAt = details.creationTimeMillis,
            connectedAt = details.connectTimeMillis,
            endedAt = end,
        ).finished()
        DebugLog.i(TAG, "telecom ${finished.outcome} video=${finished.video} seconds=${finished.seconds}")
        scope.launch { save(finished) }
    }

    private fun isWhatsApp(call: Call) =
        hasOwnScreen(call) && call.details.accountHandle?.componentName?.packageName in PACKAGES

    private fun isIncoming(call: Call) = call.details.callDirection == Call.Details.DIRECTION_INCOMING

    /** A voice call can turn into a video call on the way. */
    private fun noteVideo(call: Call) {
        if (VideoProfile.isVideo(call.details.videoState)) withVideo += call
        _video.value = live.any { VideoProfile.isVideo(it.details.videoState) }
    }
}
