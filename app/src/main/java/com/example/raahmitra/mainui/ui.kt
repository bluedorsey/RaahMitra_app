package com.example.raahmitra.ui.theme

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

// --- Theme Colors ---
val DarkBackground = Color(0xFF0F131A)
val CardBackground = Color(0xFF1C222E)
val PrimaryBlue = Color(0xFF2962FF)
val ActiveGreen = Color(0xFF4CAF50)
val AlertRed = Color(0xFFE53935)
val TextWhite = Color.White
val TextGrey = Color(0xFF8B95A5)

/* 🔔 Notification sound helper */
fun playTingSound(context: Context) {
    try {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone.play()
    } catch (e: Exception) { e.printStackTrace() }
}

/* 🌍 GPS Helper */
fun checkAndEnableLocation(context: Context, launcher: (IntentSenderRequest) -> Unit) {
    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
    val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
    val client = LocationServices.getSettingsClient(context)
    val task = client.checkLocationSettings(builder.build())

    task.addOnFailureListener { exception ->
        if (exception is ResolvableApiException) {
            try {
                launcher(IntentSenderRequest.Builder(exception.resolution).build())
            } catch (sendEx: IntentSender.SendIntentException) { /* Ignore */ }
        }
    }
}

@Composable
fun HomeScreen(
    accelX: Float, accelY: Float, accelZ: Float,
    gyroX: Float, gyroY: Float, gyroZ: Float
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // State Management
    var driveMode by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready to Drive") }
    var locationDetail by remember { mutableStateOf("Distraction-free mode is off") }
    var isAlertActive by remember { mutableStateOf(false) }
    var lastTriggerTime by remember { mutableLongStateOf(0L) }

    // Filtering logic for pothole detection
    var lastAz by remember { mutableFloatStateOf(0f) }
    val alpha = 0.85f

    // Permissions & GPS Settings
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val settingResultRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) statusMessage = "GPS Required for Potholes!"
    }

    // --- Pothole Detection Logic ---
    if (driveMode) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        DisposableEffect(driveMode) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val az = event.values[2]
                    lastAz = alpha * lastAz + (1 - alpha) * az
                    val linearZ = az - lastAz
                    val now = System.currentTimeMillis()

                    if (linearZ > 4.5f && now - lastTriggerTime > 7000) {
                        lastTriggerTime = now
                        isAlertActive = true
                        playTingSound(context)

                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener { loc ->
                                    statusMessage = "POTHOLE DETECTED!"
                                    locationDetail = "Lat: ${loc?.latitude}, Lon: ${loc?.longitude}"
                                }
                        } else {
                            statusMessage = "BUMP DETECTED"
                        }

                        coroutineScope.launch {
                            delay(4000)
                            isAlertActive = false
                            statusMessage = "Driving Mode Active"
                            locationDetail = "Monitoring road conditions..."
                        }
                    }
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopHeader()
            Spacer(modifier = Modifier.height(24.dp))

            CarDisplayCard(isAlert = isAlertActive)

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if(isAlertActive) AlertRed else TextWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = locationDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = TextGrey,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
            SensorDebugBox(accelX, accelY, accelZ, gyroX, gyroY, gyroZ)
            Spacer(modifier = Modifier.height(32.dp))

            SwipeToDriveButton(
                isDriving = driveMode,
                onSwipeComplete = {
                    driveMode = true
                    statusMessage = "Driving Mode Active"
                    locationDetail = "Monitoring road conditions..."
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    checkAndEnableLocation(context) { intent -> settingResultRequest.launch(intent) }
                },
                onStopClick = {
                    driveMode = false
                    statusMessage = "Ready to Drive"
                    locationDetail = "Distraction-free mode is off"
                }
            )
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun SwipeToDriveButton(isDriving: Boolean, onSwipeComplete: () -> Unit, onStopClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    if (isDriving) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(ActiveGreen.copy(alpha = 0.15f))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStopClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Text("TAP TO STOP", color = ActiveGreen, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Box(Modifier.align(Alignment.CenterEnd).padding(6.dp).size(60.dp).clip(CircleShape).background(ActiveGreen), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Stop, null, tint = TextWhite)
            }
        }
    } else {
        val dragSize = 64.dp
        val dragSizePx = with(LocalDensity.current) { dragSize.toPx() }
        var maxWidthPx by remember { mutableFloatStateOf(0f) }
        val offsetX = remember { Animatable(0f) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(CardBackground)
                .onGloballyPositioned { maxWidthPx = it.size.width.toFloat() - dragSizePx }
        ) {
            Text("SWIPE TO DRIVE", color = TextGrey.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .padding(6.dp)
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            scope.launch { offsetX.snapTo((offsetX.value + delta).coerceIn(0f, maxWidthPx)) }
                        },
                        onDragStopped = {
                            if (offsetX.value > maxWidthPx * 0.7f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    offsetX.animateTo(maxWidthPx, tween(200))
                                    onSwipeComplete()
                                    delay(500)
                                    offsetX.snapTo(0f)
                                }
                            } else {
                                scope.launch { offsetX.animateTo(0f, tween(300)) }
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.KeyboardArrowRight, null, tint = TextWhite, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun CarDisplayCard(isAlert: Boolean) {
    val bgColor by animateColorAsState(if (isAlert) AlertRed.copy(alpha = 0.2f) else CardBackground, label = "")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(bgColor, DarkBackground))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isAlert) Icons.Outlined.Report else Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = if (isAlert) AlertRed else Color.DarkGray,
            modifier = Modifier.size(if (isAlert) 150.dp else 120.dp)
        )
    }
}

@Composable
fun TopHeader() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("RaahMitra", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("Safe Drive Assistant", color = TextGrey, fontSize = 12.sp)
        }
        Icon(Icons.Default.Settings, null, tint = TextGrey)
    }
}

@Composable
fun SensorDebugBox(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
    Column(Modifier.fillMaxWidth().background(CardBackground.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text("SENSOR DATA MONITOR", color = TextGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Accel: %.2f, %.2f, %.2f".format(ax, ay, az), color = TextGrey, fontSize = 11.sp)
            Text("Gyro: %.2f".format(gx), color = TextGrey, fontSize = 11.sp)
        }
    }
}