package com.bettercallsoul.telecom

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.bettercallsoul.diag.DebugLog

/**
 * A black window over everything, at the lowest brightness, that swallows every touch: the screen as
 * good as off while the phone is at the ear, whichever app is open. Needs the "display over other
 * apps" access the call pill uses; without it only the call screen goes dark.
 */
internal class EarCover(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show() {
        if (view != null) return
        if (!Settings.canDrawOverlays(context)) {
            DebugLog.i(TAG, "no overlay access: only the call screen darkens")
            return
        }
        val cover = View(context).apply {
            setBackgroundColor(Color.BLACK)
            @Suppress("ClickableViewAccessibility")
            setOnTouchListener { _, _ -> true }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            screenBrightness = 0f
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "bcs:ear"
        }
        runCatching { windows.addView(cover, params) }
            .onSuccess { view = cover }
            .onFailure { DebugLog.i(TAG, "cover failed: ${it.message}") }
    }

    fun hide() {
        val cover = view ?: return
        view = null
        runCatching { windows.removeViewImmediate(cover) }
    }

    private companion object {
        const val TAG = "Ear"
    }
}
