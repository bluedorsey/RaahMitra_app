package com.example.raahmitra.ui.theme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/* 🔔 Ting sound */
fun playTingSound(context: Context) {
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val ringtone = RingtoneManager.getRingtone(context, uri)
    ringtone.play()
}

@Composable
fun UiScreen(
    accelX: Float, accelY: Float, accelZ: Float,
    gyroX: Float, gyroY: Float, gyroZ: Float,
    gravX: Float, gravY: Float, gravZ: Float
) {
    val context = LocalContext.current
    val fusedLocationClient =
        remember { LocationServices.getFusedLocationProviderClient(context) }

    val coroutineScope = rememberCoroutineScope()

    var driveMode by remember { mutableStateOf(false) }
    var locationInfo by remember { mutableStateOf("Location: Not available") }
    var lastTriggerTime by remember { mutableStateOf(0L) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}

    Column(modifier = Modifier.padding(16.dp)) {

        Button(onClick = {
            driveMode = !driveMode
            if (
                driveMode &&
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }) {
            Text(if (driveMode) "Exit Drive Mode" else "Drive Mode")
        }

        Text("Accel: $accelX, $accelY, $accelZ")
        Text("Gyro: $gyroX, $gyroY, $gyroZ")
        Text("Gravity: $gravX, $gravY, $gravZ")
        Text(locationInfo)
    }

    if (driveMode) {
        val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val accelerometer =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        DisposableEffect(driveMode) {

            val listener = object : SensorEventListener {

                override fun onSensorChanged(event: SensorEvent) {

                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]

                    // Remove gravity (~9.8)
                    val netAcceleration =
                        sqrt(ax * ax + ay * ay + az * az) - 9.8f

                    val now = System.currentTimeMillis()

                    // Threshold + cooldown
                    if (netAcceleration > 6.5f && now - lastTriggerTime > 10_000) {
                        lastTriggerTime = now

                        if (
                            context.checkSelfPermission(
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            fusedLocationClient.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                null
                            ).addOnSuccessListener { location ->
                                location?.let {

                                     playTingSound(context)

                                    locationInfo =
                                        "BUMP DETECTED\nLat: ${it.latitude}, Lon: ${it.longitude}"

                                    coroutineScope.launch {
                                        delay(3000)
                                        locationInfo = "Location: Not available"
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(
                listener,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME
            )

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }
}
