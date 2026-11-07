package com.bettercallsoul.island

import com.bettercallsoul.diag.DebugLog
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.edit
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The small pill that floats over other apps during a call: tap it to reopen the call, or tap the
 * red button to hang up. While a call rings it shows the caller, and the green button answers
 * without leaving the current app. It can be dragged and sticks to the nearest side. Plain views,
 * because a window owned by a service has none of the lifecycle owners Compose expects.
 */
class CallOverlay(
    context: Context,
    private val onOpen: () -> Unit,
    private val onAnswer: () -> Unit,
    private val onSpeaker: () -> Unit = {},
    private val onHangUp: () -> Unit,
) {
    private val context = context.applicationContext
    private val windows = this.context.getSystemService(WindowManager::class.java)
    private val prefs = this.context.getSharedPreferences("call_overlay", Context.MODE_PRIVATE)
    private val density = this.context.resources.displayMetrics.density

    private var root: View? = null
    private var timer: Chronometer? = null
    private var green: ImageView? = null
    private var ringing = false
    private var speakerOn = false
    private var style = PillStyle()
    private var snapping: ValueAnimator? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Keeps y measured from the top of the screen, the same frame that snap() clamps in.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    /** Does nothing until the user has allowed drawing over other apps. */
    fun show(connectedAt: Long?, speakerOn: Boolean = false) {
        if (!Settings.canDrawOverlays(context)) return
        if (root == null) attach()
        val clock = timer ?: return
        this.speakerOn = speakerOn
        setRinging(false)
        if (connectedAt == null) {
            clock.stop()
            clock.text = context.getString(R.string.overlay_dialing)
        } else {
            clock.base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - connectedAt)
            clock.start()
        }
    }

    /** Does nothing until the user has allowed drawing over other apps. */
    fun showRinging(caller: String) {
        if (!Settings.canDrawOverlays(context)) return
        if (root == null) attach()
        val clock = timer ?: return
        setRinging(true)
        clock.stop()
        clock.text = caller
    }

    /** Ringing: a green answer button. In a call: a speaker switch, white while the speaker is on. */
    private fun setRinging(value: Boolean) {
        ringing = value
        val button = green ?: return
        val background = button.background as GradientDrawable
        if (value) {
            button.setImageResource(R.drawable.ic_overlay_call)
            background.setColor(CALL_GREEN)
            button.imageTintList = ColorStateList.valueOf(Color.WHITE)
            button.contentDescription = context.getString(R.string.overlay_answer)
        } else {
            button.setImageResource(R.drawable.ic_overlay_speaker)
            background.setColor(if (speakerOn) Color.WHITE else SPEAKER_OFF)
            button.imageTintList = ColorStateList.valueOf(if (speakerOn) Color.BLACK else Color.WHITE)
            button.contentDescription = context.getString(R.string.overlay_speaker)
            button.isSelected = speakerOn
        }
    }

    fun hide() {
        snapping?.cancel()
        timer?.stop()
        timer = null
        green = null
        root?.let(windows::removeView)
        root = null
    }

    private fun attach() {
        style = PillStyle.load(context)
        val clock = Chronometer(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * style.scale)
            fontFeatureSettings = "tnum"
            gravity = Gravity.CENTER
            maxWidth = if (style.vertical) sdp(96f) else sdp(150f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val call = circle(R.drawable.ic_overlay_call, CALL_GREEN).apply {
            setOnClickListener { if (ringing) onAnswer() else onSpeaker() }
        }
        val end = circle(R.drawable.ic_overlay_end, END_RED).apply {
            contentDescription = context.getString(R.string.overlay_end)
            setOnClickListener { onHangUp() }
        }
        val pill = LinearLayout(context).apply {
            orientation = if (style.vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            contentDescription = context.getString(R.string.overlay_open)
            background = GradientDrawable().apply {
                setColor(PILL)
                cornerRadius = sdp(22f).toFloat()
            }
            // A soft shadow; black rather than the default grey so it shows on dark wallpapers too.
            elevation = dp(SHADOW_DP).toFloat()
            outlineAmbientShadowColor = Color.BLACK
            outlineSpotShadowColor = Color.BLACK
            alpha = style.opacity
            val inset = sdp(4f)
            setPadding(inset, inset, inset, inset)
            setOnClickListener { onOpen() }
            val (first, last) = if (style.endFirst) end to call else call to end
            addView(first)
            addView(clock, spaced())
            addView(last)
        }
        // The window has to be a little larger than the pill, or the shadow is cut off at its edge.
        val frame = FrameLayout(context).apply {
            setPadding(dp(FRAME_DP), dp(FRAME_DP), dp(FRAME_DP), dp(FRAME_DP))
            clipToPadding = false
            addView(pill)
        }
        val drag = Drag()
        pill.setOnTouchListener(drag)
        end.setOnTouchListener(drag)
        call.setOnTouchListener(drag)

        params.x = 0
        params.y = prefs.getInt(KEY_Y, -1).takeIf { it >= 0 } ?: (bounds().height() / 4)
        try {
            windows.addView(frame, params)
        } catch (error: RuntimeException) {
            DebugLog.e("Pill", "could not add the pill window", error)
            return
        }
        DebugLog.i("Pill", "shown, style=$style")
        // The text changes width once the call connects, so the pill has to stay on its side.
        frame.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) place(frame, onRight = prefs.getBoolean(KEY_RIGHT, true))
        }
        root = frame
        timer = clock
        green = call
    }

    private fun circle(icon: Int, color: Int) = ImageView(context).apply {
        setImageResource(icon)
        val inset = sdp(8f)
        setPadding(inset, inset, inset, inset)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        layoutParams = LinearLayout.LayoutParams(sdp(36f), sdp(36f))
    }

    private fun spaced() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        if (style.vertical) setMargins(0, sdp(6f), 0, sdp(6f)) else setMargins(sdp(8f), 0, sdp(8f), 0)
    }

    /** Density pixels scaled by the chosen pill size. */
    private fun sdp(value: Float) = (value * style.scale * density).roundToInt()

    @Suppress("DEPRECATION")
    private fun bounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return windows.currentWindowMetrics.bounds
        val size = Point()
        windows.defaultDisplay.getRealSize(size)
        return Rect(0, 0, size.x, size.y)
    }

    /** The status bar's and navigation bar's height, top then bottom. */
    private fun barHeights(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = windows.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.systemBars())
            return bars.top to bars.bottom
        }
        fun height(name: String): Int {
            val id = context.resources.getIdentifier(name, "dimen", "android")
            return if (id != 0) context.resources.getDimensionPixelSize(id) else 0
        }
        return height("status_bar_height") to height("navigation_bar_height")
    }

    private fun place(view: View, onRight: Boolean) {
        params.x = sideX(view, onRight)
        windows.updateViewLayout(view, params)
    }

    private fun sideX(view: View, onRight: Boolean) = if (onRight) bounds().width() - view.width else 0

    /** Goes to the nearest side and stays clear of the status and navigation bars. */
    private fun snap(view: View) {
        val onRight = params.x + view.width / 2 > bounds().width() / 2
        val (top, bottom) = barHeights()
        params.y = params.y.coerceIn(top, (bounds().height() - bottom - view.height).coerceAtLeast(top))
        prefs.edit {
            putBoolean(KEY_RIGHT, onRight)
            putInt(KEY_Y, params.y)
        }
        snapping = ValueAnimator.ofInt(params.x, sideX(view, onRight)).apply {
            duration = 180
            addUpdateListener {
                if (root != null) {
                    params.x = it.animatedValue as Int
                    windows.updateViewLayout(view, params)
                }
            }
            start()
        }
    }

    /** Set on the pill and its button, so that a drag can start anywhere and a tap still clicks. */
    private inner class Drag : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val window = root ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapping?.cancel()
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    view.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (!moved && hypot(dx, dy) > slop) {
                        moved = true
                        view.isPressed = false
                    }
                    if (moved) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        windows.updateViewLayout(window, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (moved) snap(window) else view.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    if (moved) snap(window)
                }
            }
            return true
        }
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    private companion object {
        const val SHADOW_DP = 6
        const val FRAME_DP = 10
        const val KEY_RIGHT = "right"
        const val KEY_Y = "y"
        const val PILL = 0xF01E2328.toInt()
        const val CALL_GREEN = 0xFF12A85F.toInt()
        const val END_RED = 0xFFE5484D.toInt()
        const val SPEAKER_OFF = 0x40FFFFFF
    }
}
