package com.bettercallsoul

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bettercallsoul.calllog.ContactAccount
import com.bettercallsoul.calllog.ContactEditor
import com.bettercallsoul.calllog.MovePlan
import com.bettercallsoul.diag.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Moves the contacts of one address book into another, merging those the other already has. It
 * runs on its own, so leaving the page doesn't stop it halfway; one run at a time.
 */
class ContactMoves(private val editor: ContactEditor) {
    data class Run(
        val done: Int,
        val total: Int,
        val finished: Boolean = false,
        val failed: Int = 0,
        val merged: Int = 0,
    )

    var run by mutableStateOf<Run?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val busy: Boolean get() = run?.finished == false

    /** Carries out [plan]; a phone [to] is where new contacts may go on this phone. */
    fun move(plan: MovePlan, to: ContactAccount) {
        if (busy || plan.rawIds.isEmpty()) return
        run = Run(0, plan.rawIds.size)
        scope.launch {
            var failed = 0
            var merged = 0
            plan.rawIds.forEachIndexed { index, raw ->
                // One that can't be read or written stays where it is.
                val into = plan.matches[raw]
                val ok = runCatching {
                    if (into != null) editor.mergeRaw(raw, into) else editor.moveRaw(raw, to)
                }.onFailure { DebugLog.e(TAG, "move failed", it) }.getOrDefault(false)
                when {
                    !ok -> failed++
                    into != null -> merged++
                }
                run = run?.copy(done = index + 1)
            }
            editor.notifyChanged()
            DebugLog.i(TAG, "move: ${plan.rawIds.size - failed} of ${plan.rawIds.size}, $merged merged")
            run = run?.copy(finished = true, failed = failed, merged = merged)
        }
    }

    /** Puts away the line about a finished run. */
    fun dismiss() {
        if (run?.finished == true) run = null
    }

    private companion object {
        const val TAG = "ContactMoves"
    }
}
