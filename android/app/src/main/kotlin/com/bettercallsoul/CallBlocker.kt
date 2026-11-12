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
 */
class CallBlocker : CallScreeningService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(details: Call.Details) {
        val app = container
        val rules = app.blockRules
        if (details.callDirection != Call.Details.DIRECTION_INCOMING || !rules.any) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val number = details.handle?.schemeSpecificPart.orEmpty()
        scope.launch {
            val reason = runCatching {
                val local = app.directory.local(number)
                rules.reason(number, local.isContact) {
                    // What the phone already knows, else one quick look-up (it gives up within seconds).
                    val known = if (local.verdict == Verdict.Unknown) app.directory.remote(local) else local
                    known.spamVotes
                }
            }.onFailure { DebugLog.e(TAG, "screening failed, the call rings", it) }.getOrNull()
            DebugLog.i(TAG, "${DebugLog.mask(number)}: ${reason ?: "rings"}")
            val response = if (reason == null) {
                CallResponse.Builder().build()
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CallBlocker"
    }
}
