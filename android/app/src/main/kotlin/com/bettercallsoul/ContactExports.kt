package com.bettercallsoul

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bettercallsoul.calllog.ContactAccount
import com.bettercallsoul.calllog.ContactExport
import com.bettercallsoul.diag.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Writes contacts to a file the user picked; like a move it goes on when the page is left. */
class ContactExports(context: Context, val export: ContactExport) {
    data class Run(val done: Int, val total: Int, val finished: Boolean = false, val written: Int = 0, val failed: Boolean = false)

    private val resolver = context.applicationContext.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var run by mutableStateOf<Run?>(null)
        private set

    val busy: Boolean get() = run?.finished == false

    /** [account] null for every contact. */
    fun start(format: ContactExport.Format, account: ContactAccount?, file: Uri) {
        if (busy) return
        run = Run(0, 0)
        scope.launch {
            val result = runCatching {
                val ids = export.contactIds(account)
                run = Run(0, ids.size)
                resolver.openOutputStream(file, "wt")!!.use { out ->
                    export.write(format, ids, account, out) { done -> run = run?.copy(done = done) }
                }
            }.onFailure { DebugLog.e(TAG, "export failed", it) }
            DebugLog.i(TAG, "$format: ${result.getOrNull()} of ${run?.total}")
            run = run?.copy(finished = true, written = result.getOrDefault(0), failed = result.isFailure)
        }
    }

    fun dismiss() {
        if (run?.finished == true) run = null
    }

    private companion object {
        const val TAG = "ContactExports"
    }
}
