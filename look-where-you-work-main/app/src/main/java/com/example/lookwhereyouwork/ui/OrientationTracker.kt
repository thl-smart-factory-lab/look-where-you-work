package com.example.lookwhereyouwork.ui.mqtt

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.PI

class OrientationTracker(
    private val sensorManager: SensorManager,
    private val onUpdate: (yawDeg: Float, pitchDeg: Float, rollDeg: Float) -> Unit
) : SensorEventListener {

    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientationRad = FloatArray(3)

    // Offset für "Reset"
    private var yaw0 = 0f
    private var pitch0 = 0f
    private var roll0 = 0f
    private var hasBaseline = false


    fun start() {
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        Log.d("SENSOR", "rotationVectorSensor=$rotationVectorSensor")

    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun resetBaseline() {
        hasBaseline = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationRad)

        // Android liefert [azimuth(yaw), pitch, roll] in Radiant
        val yaw = radToDeg(orientationRad[0])
        val pitch = radToDeg(orientationRad[1])
        val roll = radToDeg(orientationRad[2])

        if (!hasBaseline) {
            yaw0 = yaw
            pitch0 = pitch
            roll0 = roll
            hasBaseline = true
        }

        onUpdate(yaw - yaw0, pitch - pitch0, roll - roll0)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // nichts zu tun
    }

    private fun radToDeg(x: Float): Float = (x * 180f / PI.toFloat())
}