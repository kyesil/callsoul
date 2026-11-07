package com.bettercallsoul

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.bettercallsoul.diag.DebugLog
import com.bettercallsoul.island.CallOverlay
import com.bettercallsoul.island.PillRoute
import com.bettercallsoul.telecom.CallManager
import com.bettercallsoul.telecom.WhatsAppTelecomCalls
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The floating pill during a WhatsApp call, the one the phone's calls have. WhatsApp's notification
 * gives it the way back to the call and the hang-up button. Where WhatsApp reports the call to Telecom
 * the speaker goes through Telecom; elsewhere the pill picks the phone's communication device itself,
 * which Android lets the call follow, and lets go of it when the call ends. A phone call takes the place
 * over: it has a pill of its own.
 */
class WhatsAppPill(context: Context) {
    /** [ringing] with [title] while a call comes in, when [answer] takes it. */
    private class Shown(
        val connectedAt: Long?,
        val open: PendingIntent?,
        val hangUp: PendingIntent?,
        val ringing: Boolean = false,
        val title: String = "",
        val answer: PendingIntent? = null,
    )

    private val context = context.applicationContext
    private val scope = MainScope()
    private val shown = MutableStateFlow<Shown?>(null)
    private val sound = this.context.getSystemService(AudioManager::class.java)
    private val device = MutableStateFlow(routeOf(currentDevice()))
    private val deviceListener = AudioManager.OnCommunicationDeviceChangedListener { device.value = routeOf(it) }
    private var switchable = false

    /** The communication device was chosen here, and has to be given back. */
    private var holdsDevice = false
    private val overlay = CallOverlay(
        context = this.context,
        onOpen = ::open,
        onAnswer = ::answer,
        onSpeaker = ::toggleSpeaker,
        onHangUp = ::hangUp,
        timerColor = WHATSAPP_GREEN,
    )

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sound.addOnCommunicationDeviceChangedListener(this.context.mainExecutor, deviceListener)
        }
        scope.launch {
            combine(shown, CallManager.call, CallManager.audio, WhatsAppTelecomCalls.inCall, device) { call, phoneCall, audio, inTelecom, other ->
                val viaTelecom = inTelecom && audio.routeKnown
                Triple(call?.takeIf { phoneCall == null }, viaTelecom, if (viaTelecom) audio.pillRoute else other)
            }.collect { (call, viaTelecom, route) ->
                switchable = viaTelecom
                when {
                    call == null -> {
                        overlay.hide()
                        letGoOfDevice()
                    }
                    call.ringing -> overlay.showRinging(call.title)
                    else -> overlay.show(call.connectedAt, route)
                }
            }
        }
    }

    /** [connectedAt] is null until the other side answers. */
    fun show(notification: Notification, connectedAt: Long?) {
        val hangUp = intentIn(notification, EXTRA_HANG_UP) ?: notification.actions?.firstOrNull()?.actionIntent
        shown.value = Shown(connectedAt, notification.contentIntent, hangUp)
    }

    /**
     * A call coming in, with WhatsApp's own answer and decline. Only from its incoming-call notification,
     * whose type says so in any language. False when the notification has neither.
     */
    fun ring(notification: Notification, title: String): Boolean {
        if (notification.extras.getInt(EXTRA_CALL_TYPE) != CALL_TYPE_INCOMING) return false
        val actions = notification.actions.orEmpty()
        // The call style puts decline first and answer last.
        val answer = intentIn(notification, EXTRA_ANSWER) ?: actions.lastOrNull()?.actionIntent
        val decline = intentIn(notification, EXTRA_DECLINE) ?: actions.firstOrNull()?.actionIntent
        if (answer == null || decline == null || answer == decline) return false
        shown.value = Shown(null, notification.contentIntent, decline, ringing = true, title = title, answer = answer)
        return true
    }

    /**
     * Right away rather than through the flow: a phone that freezes idle apps may do so the moment this
     * returns, and the pill would stay on the screen until something wakes the app.
     */
    fun hide() {
        shown.value = null
        overlay.hide()
        letGoOfDevice()
    }

    fun close() {
        letGoOfDevice()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) sound.removeOnCommunicationDeviceChangedListener(deviceListener)
        scope.cancel()
        overlay.hide()
    }

    /**
     * To the speaker and back. Off the speaker, the sound goes where WhatsApp had it, a headset before
     * the earpiece.
     */
    private fun toggleSpeaker() {
        if (switchable) {
            CallManager.toggleSpeaker()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            open()
            return
        }
        val devices = sound.availableCommunicationDevices
        val target = if (device.value == PillRoute.Speaker) {
            if (holdsDevice) {
                letGoOfDevice()
                return
            }
            devices.firstOrNull { routeOf(it) == PillRoute.Bluetooth }
                ?: devices.firstOrNull { routeOf(it) == PillRoute.Wired }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
        } else {
            devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        }
        if (target == null) {
            DebugLog.w(TAG, "no device to move the sound to")
            return
        }
        val moved = sound.setCommunicationDevice(target)
        holdsDevice = holdsDevice || moved
        DebugLog.i(TAG, "sound to ${routeOf(target)}: $moved")
    }

    private fun letGoOfDevice() {
        if (!holdsDevice || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        sound.clearCommunicationDevice()
        holdsDevice = false
        DebugLog.i(TAG, "sound back to WhatsApp's choice")
    }

    /** The device the phone talks through, whoever chose it. Android 12 and up only. */
    private fun currentDevice(): AudioDeviceInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) sound.communicationDevice else null

    private fun routeOf(info: AudioDeviceInfo?): PillRoute? = when (info?.type) {
        null -> null
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> PillRoute.Speaker
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID -> PillRoute.Bluetooth
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> PillRoute.Wired
        else -> PillRoute.Earpiece
    }

    private fun open() {
        val intent = shown.value?.open
        if (intent != null) send(intent) else DebugLog.w(TAG, "no way back to the call")
    }

    private fun answer() {
        val intent = shown.value?.answer
        if (intent != null) send(intent) else DebugLog.w(TAG, "no answer button in the notification")
    }

    @Suppress("DEPRECATION")
    private fun intentIn(notification: Notification, key: String): PendingIntent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notification.extras.getParcelable(key, PendingIntent::class.java)
        } else {
            notification.extras.getParcelable(key)
        }

    private fun hangUp() {
        val intent = shown.value?.hangUp
        if (intent != null) send(intent) else DebugLog.w(TAG, "no hang-up button in the notification")
    }

    private fun send(intent: PendingIntent) {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The tap is on our window, but Android asks the sender to say that it may open an activity.
            @Suppress("DEPRECATION")
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        try {
            intent.send(context, 0, null, null, null, null, options.toBundle())
        } catch (error: PendingIntent.CanceledException) {
            DebugLog.w(TAG, "the call's notification is gone: ${error.message}")
        }
    }

    private companion object {
        const val TAG = "WhatsAppCalls"
        const val WHATSAPP_GREEN = 0xFF25D366.toInt()

        // The call style's extras, kept by name: the constants are newer than the oldest Android we run on.
        const val EXTRA_CALL_TYPE = "android.callType"
        const val EXTRA_ANSWER = "android.answerIntent"
        const val EXTRA_DECLINE = "android.declineIntent"
        const val EXTRA_HANG_UP = "android.hangUpIntent"
        const val CALL_TYPE_INCOMING = 1
    }
}
