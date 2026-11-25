package com.bettercallsoul

import android.telecom.Call
import android.telecom.CallScreeningService
import com.bettercallsoul.diag.DebugLog
import com.bettercallsoul.spam.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Turns calls away before they ring, by the rules in Settings: beginning with a blocked prefix, from
 * outside the allowed country codes, or with enough spam votes. Android asks the default phone app about every incoming call;
 * a call turned away here shows in the call log as blocked, with no ring and no notification.
 * A caller muted on their page for a while is let in without sound instead.
 */
class CallBlocker : CallScreeningService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(details: Call.Details) {
        val app = container
        val rules = app.blockRules
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val muted = incoming && app.muted.isMuted(number)
        if (!incoming || !rules.any) {
            if (muted) DebugLog.i(TAG, "${DebugLog.mask(number)}: muted, rings without sound")
            respondToCall(details, if (muted) silent() else CallResponse.Builder().build())
            return
        }
        scope.launch {
            val reason = runCatching {
                val local = app.directory.local(number)
                rules.reason(number, local.isContact) {
                    // What the phone already knows, else one quick look-up (it gives up within seconds).
                    val known = if (local.verdict == Verdict.Unknown) app.directory.remote(local) else local
                    known.spamVotes
                }
            }.onFailure { DebugLog.e(TAG, "screening failed, the call rings", it) }.getOrNull()
            DebugLog.i(TAG, "${DebugLog.mask(number)}: ${reason ?: if (muted) "muted, rings without sound" else "rings"}")
            val response = if (reason == null) {
                if (muted) silent() else CallResponse.Builder().build()
            } else {
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(true)
                    .build()
            }
            withContext(Dispatchers.Main) { respondToCall(details, response) }
        }
    }

    private fun silent() = CallResponse.Builder().setSilenceCall(true).build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CallBlocker"
    }
}
