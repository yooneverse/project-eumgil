package com.ssafy.e102.eumgil.core.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidCurrentHeadingManager(
    context: Context,
) : CurrentHeadingManager,
    SensorEventListener {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val orientationRadians = FloatArray(3)
    private val mutableLatestHeading = MutableStateFlow<HeadingSnapshot?>(null)
    private var isTracking = false

    override val latestHeading: StateFlow<HeadingSnapshot?> = mutableLatestHeading.asStateFlow()

    override fun startHeadingUpdates() {
        if (isTracking) return
        val sensor = rotationVectorSensor ?: return
        isTracking =
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI,
            )
    }

    override fun stopHeadingUpdates() {
        if (!isTracking) return
        sensorManager.unregisterListener(this)
        isTracking = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationRadians)
        val rawAzimuthDegrees = Math.toDegrees(orientationRadians[0].toDouble())
        val smoothedAzimuthDegrees =
            smoothHeadingDegrees(
                previousDegrees = mutableLatestHeading.value?.azimuthDegrees,
                nextDegrees = rawAzimuthDegrees,
            )
        mutableLatestHeading.value =
            HeadingSnapshot(
                azimuthDegrees = smoothedAzimuthDegrees,
                recordedAtEpochMillis = System.currentTimeMillis(),
            )
    }

    override fun onAccuracyChanged(
        sensor: Sensor,
        accuracy: Int,
    ) = Unit
}
