package com.example.raahmitra

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableStateOf
import com.example.raahmitra.ui.theme.HomeScreen

// Make sure to remove any other conflicting imports

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var gravity: Sensor? = null

    // States to hold raw sensor data to pass to UI
    private val accelState = mutableStateOf(Triple(0f, 0f, 0f))
    private val gyroState = mutableStateOf(Triple(0f, 0f, 0f))
    private val gravState = mutableStateOf(Triple(0f, 0f, 0f))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force Light Mode as per previous code, or remove to follow system
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

        // High refresh rate logic
        val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        val maxMode = currentDisplay?.supportedModes?.maxByOrNull { it.refreshRate }
        if (maxMode != null && maxMode.refreshRate > 60f) {
            val params = window.attributes
            params.preferredDisplayModeId = maxMode.modeId
            window.attributes = params
        }

        setContent {
            // Deconstruct state values
            val (ax, ay, az) = accelState.value
            val (gx, gy, gz) = gyroState.value
            val (grx, gry, grz) = gravState.value

           screen_align(accelX = ax, accelY = ay, accelZ = az,
               gyroX = gx, gyroY = gy, gyroZ = gz,
               gravX = grx, gravY = gry, gravZ = grz)

        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gravity?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER ->
                accelState.value = Triple(event.values[0], event.values[1], event.values[2])
            Sensor.TYPE_GYROSCOPE ->
                gyroState.value = Triple(event.values[0], event.values[1], event.values[2])
            Sensor.TYPE_GRAVITY ->
                gravState.value = Triple(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}