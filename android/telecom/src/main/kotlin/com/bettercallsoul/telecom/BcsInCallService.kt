package com.bettercallsoul.telecom

import com.bettercallsoul.diag.DebugLog
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.annotation.RequiresApi
import android.telecom.CallAudioState
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.InCallService
import com.bettercallsoul.island.CallNotificationContent
import com.bettercallsoul.island.CallNotifications
import com.bettercallsoul.island.CallOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Telecom binds this service for every call once the app holds the dialer role. */
class BcsInCallService : InCallService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val flip by lazy {
        FlipDetector(
            context = this,
            onFlip = CallManager::silenceRinger,
            onAlreadyDown = {
                DebugLog.i(TAG, "phone face down when the call came, silent=${CallManager.silentWhenFaceDown()}")
                if (CallManager.silentWhenFaceDown()) {
                    CallManager.silenceRinger()
                    startActivity(callScreenIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
    }
    private val overlay by lazy {
        CallOverlay(
            context = this,
            onOpen = { startActivity(callScreenIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            onAnswer = CallManager::answer,
            onSpeaker = CallManager::toggleSpeaker,
            onHangUp = CallManager::hangUp,
        )
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.attach(this)
        scope.launch {
            CallManager.call.distinctUntilChanged { old, new ->
                old?.phase == new?.phase && old?.caller == new?.caller && old?.connectedAt == new?.connectedAt
            }.collect(::updateNotification)
        }
        scope.launch {
            CallManager.call.map { it?.phase == CallPhase.Incoming }.distinctUntilChanged().collect { ringing ->
                if (ringing) flip.start() else flip.stop()
            }
        }
        scope.launch {
            combine(CallManager.call, CallManager.callScreenVisible, CallManager.audio) { call, screenVisible, audio ->
                call?.takeIf { !screenVisible && it.phase != CallPhase.SelectingAccount }?.let { it to audio.pillRoute }
            }.collect { shown ->
                val call = shown?.first
                when {
                    call == null -> overlay.hide()
                    call.phase == CallPhase.Incoming -> {
                        DebugLog.i(TAG, "pill: ringing")
                        overlay.showRinging(call.caller.title)
                    }
                    else -> overlay.show(call.connectedAt, shown.second)
                }
            }
        }
        scope.launch {
            // Held here rather than in the call screen, so the screen also goes dark for a call
            // answered from the pill or the notification while another app is open.
            combine(CallManager.call, CallManager.audio, CallManager.keypadOpen) { call, audio, keypad ->
                call != null && call.phase in EAR_PHASES && audio.routeKnown && audio.routeType == AudioRouteType.Earpiece && !keypad
            }.distinctUntilChanged().collect(::holdProximity)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        holdProximity(false)
        flip.stop()
        overlay.hide()
        CallManager.detach()
        super.onDestroy()
    }

    override fun onSilenceRinger() = CallManager.onSilenceRinger()

    override fun onCallAdded(call: Call) {
        if (WhatsAppTelecomCalls.hasOwnScreen(call)) {
            WhatsAppTelecomCalls.onCallAdded(call)
            return
        }
        CallManager.onCallAdded(call)
        // On an unlocked phone a ringing call stays a pop-up, so whatever is on screen isn't interrupted.
        val ringing = call.currentState == Call.STATE_RINGING || call.currentState == Call.STATE_SIMULATED_RINGING
        DebugLog.i(TAG, "call screen: ${if (!ringing || !screenInUse()) "opened" else "pop-up only (phone in use)"}")
        if (!ringing || !screenInUse()) {
            // An outgoing call comes up behind the burst on the app; a ringing one keeps the system's opening.
            val opening = if (ringing) 0 else Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(callScreenIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or opening))
        }
    }

    override fun onCallRemoved(call: Call) {
        if (WhatsAppTelecomCalls.hasOwnScreen(call)) {
            WhatsAppTelecomCalls.onCallRemoved(call)
            return
        }
        CallManager.onCallRemoved(call)
    }

    override fun onCanAddCallChanged(canAddCall: Boolean) {
        CallManager.onCanAddCallChanged()
    }

    @RequiresApi(34)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        CallManager.onRouteChanged(EndpointRoutes.routeOf(callEndpoint))
    }

    @RequiresApi(34)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        CallManager.onRoutesChanged(availableEndpoints.map(EndpointRoutes::routeOf))
    }

    @RequiresApi(34)
    override fun onMuteStateChanged(isMuted: Boolean) {
        CallManager.onMuteChanged(isMuted)
    }

    /** Before Android 14 this one callback tells the route, the choices and the mute state. */
    @Deprecated("Android 14 replaced it with the three callbacks above")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        if (Build.VERSION.SDK_INT >= 34) return
        CallManager.onRoutesChanged(LegacyRoutes.routesOf(audioState))
        CallManager.onRouteChanged(LegacyRoutes.currentOf(audioState))
        CallManager.onMuteChanged(audioState.isMuted)
    }

    private fun updateNotification(state: CallUiState?) {
        if (state == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        val notification = CallNotifications.build(
            context = this,
            smallIcon = R.drawable.ic_call_notification,
            content = CallNotificationContent(
                title = state.caller.title,
                subtitle = state.caller.subtitle,
                incoming = state.phase == CallPhase.Incoming,
                quiet = CallManager.callScreenVisible.value || ringsInPill(state),
                connectedAt = state.connectedAt,
                open = PendingIntent.getActivity(
                    this, 0, callScreenIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
                answer = CallActionReceiver.pendingIntent(this, CallActionReceiver.ACTION_ANSWER),
                hangUp = CallActionReceiver.pendingIntent(this, CallActionReceiver.ACTION_HANG_UP),
                answerLabel = getString(R.string.notification_answer),
                hangUpLabel = getString(if (state.phase == CallPhase.Incoming) R.string.notification_decline else R.string.notification_end),
            ),
        )
        try {
            startForeground(CallNotifications.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } catch (error: RuntimeException) {
            DebugLog.e(TAG, "startForeground failed", error)
        }
    }

    private val proximity by lazy {
        getSystemService(PowerManager::class.java)
            .takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "bcs:proximity")
            ?.apply { setReferenceCounted(false) }
    }

    /** Screen off near the ear; on release, wait until the phone leaves the ear before lighting up. */
    private fun holdProximity(near: Boolean) {
        val lock = proximity ?: return
        if (near) {
            if (!lock.isHeld) {
                DebugLog.i(TAG, "proximity lock on")
                lock.acquire()
            }
        } else if (lock.isHeld) {
            DebugLog.i(TAG, "proximity lock off")
            lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    /**
     * A call that comes in while the phone is in use already rings in the pill; with the user's say, the
     * pop-up stays away. On a locked phone the call screen opens as always.
     */
    private fun ringsInPill(state: CallUiState): Boolean =
        state.phase == CallPhase.Incoming && CallManager.ringInPill() && Settings.canDrawOverlays(this) && screenInUse()

    private fun screenInUse(): Boolean =
        getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    companion object {
        private const val TAG = "Service"
        private val EAR_PHASES = setOf(CallPhase.Dialing, CallPhase.Active, CallPhase.OnHold)
        const val ACTION_SHOW_CALL = "com.bettercallsoul.action.SHOW_CALL"

        fun callScreenIntent(context: Context): Intent =
            Intent(ACTION_SHOW_CALL).setPackage(context.packageName)
    }
}
