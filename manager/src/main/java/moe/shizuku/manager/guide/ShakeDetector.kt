package moe.shizuku.manager.guide

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

internal class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val gesture = ShakeGesture()
    private var listening = false

    fun start() {
        if (listening) return
        val manager = sensorManager ?: return
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        listening = manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (listening) sensorManager?.unregisterListener(this)
        listening = false
        gesture.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!listening) return
        val (x, y, z) = event.values
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (gesture.onSample(gForce, event.timestamp / 1_000_000L)) onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
