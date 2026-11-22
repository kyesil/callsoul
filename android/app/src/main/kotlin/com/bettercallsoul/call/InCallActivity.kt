package com.bettercallsoul.call

import com.bettercallsoul.ui.systemAnimationsOn
import com.bettercallsoul.container
import android.os.Build
import com.bettercallsoul.AppLook
import android.content.Context
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
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLook.wrap(newBase))

    /** The app's animations switch, with the phone's own; off, the call screen opens and closes at once. */
    private val motion: Boolean get() = container.animationsEnabled && systemAnimationsOn(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!motion) {
            // The call screen is a task of its own, so Android would slide it in like an app.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
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
                    // Someone in the contacts: there is nothing to ask or tag, so the call just closes.
                    // A call that failed still stays, for the reason.
                    finished != null && finished.caller.isContact && !finished.failed -> LaunchedEffect(finished) {
                        CallManager.dismissEnded()
                        finish()
                    }
                    finished != null -> EndedScreen(finished, onClose = {
                        CallManager.dismissEnded()
                        finish()
                    })
                    else -> LaunchedEffect(Unit) { finish() }
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        if (!motion && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
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
