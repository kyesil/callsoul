package com.bettercallsoul.telecom

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import java.util.Locale
import com.bettercallsoul.diag.DebugLog

/**
 * Watches the proximity sensor alongside the system's proximity lock, which some phones don't honour
 * in every position. The moment the sensor says near, the screen is covered in black and stops taking
 * touches, however the phone is held; far again, it comes back. Each change is logged with how the
 * phone is held and whether the system turned the display off, so a phone that misses it can be told apart.
 */
internal class EarWatch(
    context: Context,
    private val onNear: (Boolean) -> Unit,
) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val proximity: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val gravitySensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravity = FloatArray(3)
    private var near: Boolean? = null
    private var running = false

    fun start() {
        if (running) return
        val sensor = proximity ?: run {
            DebugLog.i(TAG, "no proximity sensor")
            return
        }
        running = true
        near = null
        gravitySensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    fun stop() {
        if (!running) return
        running = false
        sensors.unregisterListener(this)
        near = null
        onNear(false)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> onProximity(event.values[0], event.sensor.maximumRange)
            else -> event.values.copyInto(gravity, endIndex = 3)
        }
    }

    private fun onProximity(distance: Float, range: Float) {
        // Most sensors only report 0 or their full range; "near" is anything closer than both 5 cm and the range.
        val now = distance < range && distance < NEAR_CM
        if (now == near) return
        near = now
        val display = when (displays.getDisplay(Display.DEFAULT_DISPLAY)?.state) {
            Display.STATE_ON -> "on"
            Display.STATE_OFF -> "off"
            else -> "dozing"
        }
        DebugLog.i(TAG, "proximity ${if (now) "near" else "far"} ($distance of $range cm, ${held()}, display $display)")
        onNear(now)
    }

    /** How the phone is held, from gravity: upright, sideways or flat. Only for the log. */
    private fun held(): String {
        val (x, y, z) = gravity.map { "%.1f".format(Locale.ROOT, it) }
        val pose = when {
            kotlin.math.abs(gravity[2]) > FLAT -> "flat"
            kotlin.math.abs(gravity[0]) > kotlin.math.abs(gravity[1]) -> "sideways"
            else -> "upright"
        }
        return "$pose x=$x y=$y z=$z"
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        const val TAG = "Ear"
        const val NEAR_CM = 5f
        const val FLAT = 8f
    }
}
