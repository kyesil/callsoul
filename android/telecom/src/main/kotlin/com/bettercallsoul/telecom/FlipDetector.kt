package com.bettercallsoul.telecom

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/**
 * Reports once when the phone is turned face down. It has to be seen facing another way first, so a
 * phone already lying on its face keeps ringing, and it has to stay down for a moment, so a phone
 * tumbling in a pocket doesn't count. A phone found on its face right away is reported separately.
 */
internal class FlipDetector(
    context: Context,
    private val onFlip: () -> Unit,
    private val onAlreadyDown: () -> Unit,
) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private var armed = false
    private var firstReading = true
    private var downSince = 0L

    fun start() {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return
        armed = false
        firstReading = true
        downSince = 0L
        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensors.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val z = event.values[2]
        if (firstReading) {
            firstReading = false
            if (z < FACE_DOWN) onAlreadyDown()
        }
        when {
            z > NOT_DOWN -> {
                armed = true
                downSince = 0L
            }
            z < FACE_DOWN && armed -> {
                val now = SystemClock.elapsedRealtime()
                if (downSince == 0L) downSince = now
                if (now - downSince >= HOLD_MILLIS) {
                    stop()
                    onFlip()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        const val FACE_DOWN = -8f
        const val NOT_DOWN = -3f
        const val HOLD_MILLIS = 400L
    }
}
