package com.bettercallsoul.call

import com.bettercallsoul.diag.DebugLog
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.telecom.CallManager
import com.bettercallsoul.telecom.CallPhase
import com.bettercallsoul.ui.BcsTheme
import android.graphics.Color
import androidx.activity.SystemBarStyle

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The call screens are dark on every theme, and so is everything that opens over them.
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            BcsTheme(dark = true) {
                val call by CallManager.call.collectAsStateWithLifecycle()
                val ended by CallManager.ended.collectAsStateWithLifecycle()
                val active = call
                val finished = ended
                // A phone left to ring silently shouldn't time out and hide the call.
                val ringing = active?.phase == CallPhase.Incoming
                LaunchedEffect(ringing) {
                    if (ringing) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                when {
                    active != null -> CallScreen(active)
                    finished != null -> EndedScreen(finished, onClose = {
                        CallManager.dismissEnded()
                        finish()
                    })
                    else -> LaunchedEffect(Unit) { finish() }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        DebugLog.i("Screen", "call screen visible")
        CallManager.setCallScreenVisible(true)
    }

    override fun onStop() {
        DebugLog.i("Screen", "call screen hidden")
        CallManager.setCallScreenVisible(false)
        super.onStop()
    }
}
