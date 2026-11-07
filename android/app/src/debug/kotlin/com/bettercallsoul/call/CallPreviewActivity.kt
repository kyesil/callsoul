package com.bettercallsoul.call

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bettercallsoul.island.CallNotificationContent
import com.bettercallsoul.island.CallNotifications
import com.bettercallsoul.island.CallOverlay
import com.bettercallsoul.spam.SpamCategory
import com.bettercallsoul.spam.Verdict
import com.bettercallsoul.telecom.CallPhase
import com.bettercallsoul.telecom.CallUiState
import com.bettercallsoul.telecom.CallerInfo
import com.bettercallsoul.telecom.HeldCall
import com.bettercallsoul.telecom.EndedCall
import com.bettercallsoul.ui.BcsTheme

/**
 * Debug only. Shows the call screens with sample data, for phones without a voice line:
 * adb shell am start -n dev.yesil.callsoul/com.bettercallsoul.call.CallPreviewActivity --es mode incoming
 * The modes notify and notify-off post and remove a sample ongoing call notification,
 * overlay, overlay-ringing and overlay-off do the same for the floating call button. The unknown
 * caller of the spam and ended modes takes its number from --es number.
 */
class CallPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val now = System.currentTimeMillis()
        when (intent.getStringExtra("mode")) {
            "notify" -> return sampleNotification(now)
            "overlay" -> {
                overlay(this).show(now - 83_000)
                return finish()
            }
            "overlay-ringing" -> {
                overlay(this).showRinging("Örnek Kişi")
                return finish()
            }
            "overlay-off" -> {
                overlay(this).hide()
                return finish()
            }
            "notify-off" -> {
                getSystemService(NotificationManager::class.java).cancel(CallNotifications.ID)
                return finish()
            }
        }
        enableEdgeToEdge()
        val contact = CallerInfo(
            number = "05321234567",
            title = "Örnek Kişi",
            subtitle = "Soul Lojistik · Müdür",
            isContact = true,
            lastCallAt = now - 600_000,
        )
        val unknownNumber = intent.getStringExtra("number") ?: "+99900000002"
        val unknown = CallerInfo(number = unknownNumber, title = unknownNumber)
        val spam = unknown.copy(verdict = Verdict.Spam, category = SpamCategory.Scam, spamVotes = 12)
        fun state(phase: CallPhase, caller: CallerInfo, connectedAt: Long? = null) =
            CallUiState(phase, caller, connectedAt, canHold = true, canAddCall = true)

        setContent {
            BcsTheme {
                when (intent.getStringExtra("mode")) {
                    "spam" -> CallScreen(state(CallPhase.Incoming, spam))
                    "dialing" -> CallScreen(state(CallPhase.Dialing, contact))
                    "active" -> CallScreen(state(CallPhase.Active, contact, now - 83_000))
                    "two" -> CallScreen(
                        state(CallPhase.Active, contact, now - 12_000).copy(held = HeldCall("Diğer Kişi"), canMerge = true),
                    )
                    "ended" -> EndedScreen(EndedCall(unknown, 42, failed = false, failureText = null), onClose = ::finish)
                    else -> CallScreen(state(CallPhase.Incoming, contact))
                }
            }
        }
    }

    private companion object {
        @SuppressLint("StaticFieldLeak") // CallOverlay keeps the application context, not this activity.
        var sample: CallOverlay? = null

        fun overlay(context: android.content.Context): CallOverlay = sample ?: CallOverlay(
            context = context,
            onOpen = { sample?.show(null) },
            onAnswer = { sample?.show(System.currentTimeMillis()) },
            onHangUp = { sample?.hide() },
        ).also { sample = it }
    }

    /** Without a real call there is no foreground service, so a full-screen intent makes CallStyle legal. */
    private fun sampleNotification(now: Long) {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, CallPreviewActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = CallNotifications.build(
            context = this,
            smallIcon = android.R.drawable.sym_action_call,
            content = CallNotificationContent("Örnek Kişi", "Soul Lojistik", false, now - 83_000, open, open, open),
        )
        notification.fullScreenIntent = open
        getSystemService(NotificationManager::class.java).notify(CallNotifications.ID, notification)
        finish()
    }
}
