package com.bettercallsoul.telecom

import com.bettercallsoul.diag.DebugLog
import com.bettercallsoul.island.PillRoute
import android.content.Context
import android.net.Uri
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A call's state, which moved into its details in Android 12. */
@Suppress("DEPRECATION")
internal val Call.currentState: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) details.state else state

enum class CallPhase { SelectingAccount, Incoming, Dialing, Active, OnHold }

data class CallUiState(
    val phase: CallPhase,
    val caller: CallerInfo,
    val connectedAt: Long?,
    /** The other side called. */
    val incoming: Boolean = false,
    val canHold: Boolean,
    val canAddCall: Boolean = false,
    /** The other call on the line, waiting while this one is shown. */
    val held: HeldCall? = null,
    val canMerge: Boolean = false,
)

data class HeldCall(val title: String)

data class AudioDevice(val id: String, val name: String)

data class AudioUiState(
    val muted: Boolean = false,
    val routeType: AudioRouteType = AudioRouteType.Earpiece,
    val bluetoothDevices: List<AudioDevice> = emptyList(),
    /** The device in use, or the only one there is. */
    val bluetoothName: String? = null,
    /** False until Telecom has said where the sound goes; [routeType] is only a guess before that. */
    val routeKnown: Boolean = false,
) {
    val speakerOn: Boolean get() = routeType == AudioRouteType.Speaker

    /** For the pill's button. */
    val pillRoute: PillRoute
        get() = when (routeType) {
            AudioRouteType.Speaker -> PillRoute.Speaker
            AudioRouteType.Bluetooth -> PillRoute.Bluetooth
            AudioRouteType.Wired -> PillRoute.Wired
            AudioRouteType.Earpiece, AudioRouteType.Other -> PillRoute.Earpiece
        }
    val onBluetooth: Boolean get() = routeType == AudioRouteType.Bluetooth
}

data class EndedCall(
    val caller: CallerInfo,
    val durationSeconds: Long,
    val failed: Boolean,
    val failureText: String?,
)

/** The single source of truth for calls. The service feeds it, the screens read and command it. */
object CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private const val TAG = "Call"
    private const val AUDIO = "Audio"

    private lateinit var directory: CallerDirectory
    internal var silentWhenFaceDown: () -> Boolean = { false }
        private set

    /** The user's choice that a call coming in while the phone is in use rings in the pill, not a pop-up. */
    internal var ringInPill: () -> Boolean = { false }
        private set

    private var service: InCallService? = null
    private val calls = mutableListOf<Call>()
    private val callers = mutableMapOf<Call, CallerInfo>()
    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            DebugLog.i(TAG, "state ${DebugLog.mask(numberOf(call))} -> ${stateName(state)}")
            publish()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        override fun onParentChanged(call: Call, parent: Call?) = publish()
        override fun onChildrenChanged(call: Call, children: List<Call>) = publish()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) = publish()
    }

    private var routes: List<AudioRoute> = emptyList()
    private var currentRoute: AudioRoute? = null
    private var routeBeforeSpeaker: AudioRoute? = null

    private val _call = MutableStateFlow<CallUiState?>(null)
    val call: StateFlow<CallUiState?> = _call.asStateFlow()

    private val _audio = MutableStateFlow(AudioUiState())
    val audio: StateFlow<AudioUiState> = _audio.asStateFlow()

    private val _keypadOpen = MutableStateFlow(false)
    val keypadOpen: StateFlow<Boolean> = _keypadOpen.asStateFlow()

    private val _callScreenVisible = MutableStateFlow(false)
    val callScreenVisible: StateFlow<Boolean> = _callScreenVisible.asStateFlow()

    private val _ended = MutableStateFlow<EndedCall?>(null)
    val ended: StateFlow<EndedCall?> = _ended.asStateFlow()

    fun init(directory: CallerDirectory, silentWhenFaceDown: () -> Boolean, ringInPill: () -> Boolean = { false }) {
        this.directory = directory
        this.silentWhenFaceDown = silentWhenFaceDown
        this.ringInPill = ringInPill
    }

    @SuppressLint("MissingPermission") // The app opens only once the phone permission is granted.
    fun place(context: Context, number: String) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        DebugLog.i(TAG, "place ${DebugLog.mask(number)}")
        telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
    }

    internal fun attach(service: InCallService) {
        this.service = service
    }

    internal fun detach() {
        service = null
        routes = emptyList()
        currentRoute = null
        routeBeforeSpeaker = null
        // The next call starts without knowing its route, so the proximity lock waits for Telecom.
        _audio.value = AudioUiState()
    }

    internal fun onCallAdded(call: Call) {
        _ended.value = null
        calls += call
        call.registerCallback(callback)
        val number = numberOf(call)
        DebugLog.i(TAG, "added ${DebugLog.mask(number)} ${stateName(call.currentState)}, calls=${calls.size}, conference=${isConference(call)}")
        callers[call] = CallerInfo(number = number, title = number)
        publish()
        if (isConference(call)) return
        scope.launch {
            val local = directory.local(number)
            callers[call] = local
            publish()
            callers[call] = directory.remote(local)
            publish()
        }
    }

    internal fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        val cause = call.details.disconnectCause
        DebugLog.i(TAG, "removed ${DebugLog.mask(numberOf(call))} cause=${cause?.code} ${cause?.reason.orEmpty()}, left=${calls.size - 1}")
        noteRingTime(call)
        val caller = callers.remove(call)
        calls -= call
        if (calls.isEmpty() && caller != null && !isConference(call)) _ended.value = endedOf(call, caller)
        publish()
    }

    internal fun onCanAddCallChanged() = publish()

    internal fun onRouteChanged(route: AudioRoute) {
        DebugLog.i(AUDIO, "route -> ${routeName(route)}")
        currentRoute = route
        if (route.type != AudioRouteType.Speaker) routeBeforeSpeaker = route
        publishAudio()
    }

    internal fun onRoutesChanged(available: List<AudioRoute>) {
        DebugLog.i(AUDIO, "available: ${available.joinToString { routeName(it) }}")
        routes = available
        publishAudio()
    }

    internal fun onMuteChanged(muted: Boolean) {
        _audio.update { it.copy(muted = muted) }
    }

    fun answer() {
        DebugLog.i(TAG, "answer")
        primary()?.takeIf { it.currentState == Call.STATE_RINGING }?.answer(0)
    }

    fun hangUp() {
        val call = primary() ?: return
        DebugLog.i(TAG, if (call.currentState == Call.STATE_RINGING) "reject" else "hang up")
        if (call.currentState == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
    }

    /** Turns the ringing call down with [message]; Telecom sends it as an SMS to the caller. */
    fun rejectWithMessage(message: String) {
        val call = primary()?.takeIf { it.currentState == Call.STATE_RINGING } ?: return
        DebugLog.i(TAG, "reject with message")
        call.reject(true, message)
    }

    fun toggleHold() {
        val call = primary() ?: return
        DebugLog.i(TAG, "hold toggle from ${stateName(call.currentState)}")
        if (call.currentState == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    /** Stops the ringing only: the call keeps coming in, and the default phone app is allowed to do this. */
    @SuppressLint("MissingPermission")
    internal fun silenceRinger() {
        DebugLog.i(TAG, "silence ringer")
        service?.getSystemService(TelecomManager::class.java)?.silenceRinger()
    }

    /** Going to the waiting call puts the current one on hold; Telecom takes care of that. */
    fun swap() {
        val current = primary() ?: return
        DebugLog.i(TAG, "swap")
        topLevel().firstOrNull { it != current }?.unhold()
    }

    fun merge() {
        val call = primary() ?: return
        val partner = call.conferenceableCalls.firstOrNull()
        DebugLog.i(TAG, "merge, partner=${partner != null}")
        if (partner != null) call.conference(partner) else call.mergeConference()
    }

    fun toggleMute() {
        DebugLog.i(AUDIO, "mute toggle")
        service?.setMuted(!_audio.value.muted)
    }

    /** One tap leaves any route, Bluetooth included, for the speaker; the next tap goes back. */
    fun toggleSpeaker() {
        DebugLog.i(AUDIO, "speaker toggle")
        val target = if (_audio.value.speakerOn) {
            routeBeforeSpeaker?.takeIf { it in routes }
                ?: routes.firstOrNull { it.type == AudioRouteType.Bluetooth }
                ?: routes.firstOrNull { it.type != AudioRouteType.Speaker }
        } else {
            routes.firstOrNull { it.type == AudioRouteType.Speaker }
        }
        route(target)
    }

    /** Off Bluetooth this goes to the first device; the screen asks first when there are several. */
    fun toggleBluetooth() {
        DebugLog.i(AUDIO, "bluetooth toggle")
        route(
            if (_audio.value.onBluetooth) {
                routes.firstOrNull { it.type == AudioRouteType.Earpiece }
                    ?: routes.firstOrNull { it.type != AudioRouteType.Bluetooth }
            } else {
                routes.firstOrNull { it.type == AudioRouteType.Bluetooth }
            },
        )
    }

    fun selectBluetooth(device: AudioDevice) {
        route(routes.firstOrNull { it.id == device.id })
    }

    private fun route(target: AudioRoute?) {
        val service = service ?: return
        if (target == null) {
            DebugLog.w(AUDIO, "no route to switch to; available: ${routes.joinToString { routeName(it) }}")
            return
        }
        DebugLog.i(AUDIO, "request ${routeName(target)}")
        if (Build.VERSION.SDK_INT >= 34) EndpointRoutes.request(service, target) else LegacyRoutes.request(service, target)
    }

    private fun routeName(route: AudioRoute): String =
        if (route.type == AudioRouteType.Bluetooth) "bluetooth(${route.name})" else route.type.name.lowercase()

    private fun stateName(state: Int) = when (state) {
        Call.STATE_NEW -> "NEW"
        Call.STATE_DIALING -> "DIALING"
        Call.STATE_RINGING -> "RINGING"
        Call.STATE_HOLDING -> "HOLDING"
        Call.STATE_ACTIVE -> "ACTIVE"
        Call.STATE_DISCONNECTED -> "DISCONNECTED"
        Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_ACCOUNT"
        Call.STATE_CONNECTING -> "CONNECTING"
        Call.STATE_DISCONNECTING -> "DISCONNECTING"
        Call.STATE_PULLING_CALL -> "PULLING"
        Call.STATE_AUDIO_PROCESSING -> "AUDIO_PROCESSING"
        Call.STATE_SIMULATED_RINGING -> "SIMULATED_RINGING"
        else -> "STATE_$state"
    }

    fun playDtmf(digit: Char) {
        primary()?.run {
            playDtmfTone(digit)
            stopDtmfTone()
        }
    }

    fun selectAccount(account: PhoneAccountHandle) {
        calls.firstOrNull { it.currentState == Call.STATE_SELECT_PHONE_ACCOUNT }?.phoneAccountSelected(account, false)
    }

    fun setKeypadOpen(open: Boolean) {
        _keypadOpen.value = open
    }

    fun setCallScreenVisible(visible: Boolean) {
        _callScreenVisible.value = visible
    }

    fun dismissEnded() {
        _ended.value = null
    }

    /** A ringing call takes over the screen; otherwise show the one that is on the line. */
    private fun primary(): Call? = topLevel().minByOrNull {
        when (it.currentState) {
            Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> 0
            Call.STATE_SELECT_PHONE_ACCOUNT -> 1
            Call.STATE_ACTIVE -> 2
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL -> 3
            Call.STATE_HOLDING -> 4
            else -> 5
        }
    }

    /** The calls inside a conference stay out of the list; the conference stands for them. */
    private fun topLevel(): List<Call> = calls.filter { it.parent == null }

    private fun isConference(call: Call) = call.details.hasProperty(Call.Details.PROPERTY_CONFERENCE)

    private fun callerOf(call: Call): CallerInfo? {
        if (!isConference(call)) return callers[call]
        return CallerInfo(number = "", title = call.children.mapNotNull { callers[it]?.title }.joinToString(", "))
    }

    private fun publish() {
        val call = primary()
        val caller = call?.let(::callerOf)
        val other = topLevel().firstOrNull { it != call }
        _call.value = if (call == null || caller == null) null else CallUiState(
            phase = phaseOf(call),
            caller = caller,
            connectedAt = call.details.connectTimeMillis.takeIf { it > 0 },
            incoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING,
            canHold = call.details.can(Call.Details.CAPABILITY_HOLD),
            canAddCall = service?.canAddCall() == true,
            held = other?.let(::callerOf)?.let { HeldCall(it.title) },
            canMerge = other != null &&
                (call.conferenceableCalls.isNotEmpty() || call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)),
        )
    }

    private fun publishAudio() {
        val bluetooth = routes.filter { it.type == AudioRouteType.Bluetooth }
        val inUse = currentRoute?.takeIf { it.type == AudioRouteType.Bluetooth } ?: bluetooth.singleOrNull()
        _audio.update { state ->
            state.copy(
                routeType = currentRoute?.type ?: state.routeType,
                routeKnown = currentRoute != null,
                bluetoothDevices = bluetooth.map { AudioDevice(it.id, it.name) },
                bluetoothName = inUse?.name,
            )
        }
    }

    private fun phaseOf(call: Call): CallPhase = when (call.currentState) {
        Call.STATE_SELECT_PHONE_ACCOUNT -> CallPhase.SelectingAccount
        Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> CallPhase.Incoming
        Call.STATE_ACTIVE -> CallPhase.Active
        Call.STATE_HOLDING -> CallPhase.OnHold
        else -> CallPhase.Dialing
    }

    private fun numberOf(call: Call): String = call.details.handle?.schemeSpecificPart.orEmpty()

    /** A call that came in and was never answered: how long it rang, which the phone's log doesn't say. */
    private fun noteRingTime(call: Call) {
        val details = call.details
        if (details.callDirection != Call.Details.DIRECTION_INCOMING || details.connectTimeMillis > 0) return
        val number = numberOf(call)
        if (number.isBlank()) return
        val created = details.creationTimeMillis
        val seconds = ((System.currentTimeMillis() - created + 500) / 1000).coerceAtLeast(1)
        DebugLog.i(TAG, "rang ${seconds}s")
        scope.launch { directory.saveRingTime(number, created, seconds) }
    }

    private fun endedOf(call: Call, caller: CallerInfo): EndedCall {
        val connectedAt = call.details.connectTimeMillis
        val cause = call.details.disconnectCause
        return EndedCall(
            caller = caller,
            durationSeconds = if (connectedAt > 0) (System.currentTimeMillis() - connectedAt) / 1000 else 0,
            failed = cause?.code == DisconnectCause.ERROR,
            failureText = cause?.description?.toString()?.ifBlank { null },
        )
    }
}
