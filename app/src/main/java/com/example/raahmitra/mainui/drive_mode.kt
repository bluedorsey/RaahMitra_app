package com.example.raahmitra.ui.theme

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Paint
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.LinkedList
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
val GraphLineColor = Color(0xFF00E5FF)

// --- Firebase Data Model ---
data class RoadAlert(
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

data class GraphPoint(
    val value: Float,
    val timestamp: Long,
    val locationTag: String? = null
)

data class BufferPoint(val value: Float, val timestamp: Long)

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
fun HomeScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // --- FIREBASE REFERENCE (Node name: "potholes") ---
    // I changed the name to "potholes" to be specific
    val database = remember { FirebaseDatabase.getInstance().getReference("potholes") }

    // State Management
    var driveMode by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready to Drive") }

    var currentLat by remember { mutableDoubleStateOf(0.0) }
    var currentLng by remember { mutableDoubleStateOf(0.0) }
    var latestLocationStr by remember { mutableStateOf("Locating...") }

    var isAlertActive by remember { mutableStateOf(false) }
    var lastAlertTime by remember { mutableLongStateOf(0L) }

    val graphData = remember { mutableStateListOf<GraphPoint>() }
    val maxDataPoints = 150

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val settingResultRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) statusMessage = "GPS Required!"
    }

    // --- Background Location Worker ---
    LaunchedEffect(driveMode) {
        if (driveMode) {
            while(driveMode) {
                if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) {
                            currentLat = loc.latitude
                            currentLng = loc.longitude
                            latestLocationStr = "%.4f, %.4f".format(loc.latitude, loc.longitude)
                        }
                    }
                }
                delay(2000)
            }
        }
    }

    // --- SENSOR LOGIC ---
    if (driveMode) {
        DisposableEffect(driveMode) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            val bufferSize = 40
            val sensorBuffer = LinkedList<BufferPoint>()
            val currentGravity = FloatArray(3) { 0f }
            var isGravityInit = false
            val gravityFilter = FloatArray(3)
            val alpha = 0.8f

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_GRAVITY) {
                        System.arraycopy(event.values, 0, currentGravity, 0, 3)
                        isGravityInit = true
                    } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && !isGravityInit) {
                        gravityFilter[0] = alpha * gravityFilter[0] + (1 - alpha) * event.values[0]
                        gravityFilter[1] = alpha * gravityFilter[1] + (1 - alpha) * event.values[1]
                        gravityFilter[2] = alpha * gravityFilter[2] + (1 - alpha) * event.values[2]
                        System.arraycopy(gravityFilter, 0, currentGravity, 0, 3)
                    }

                    if ((event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION || event.sensor.type == Sensor.TYPE_ACCELEROMETER)) {
                        val now = System.currentTimeMillis()
                        var ax = event.values[0]
                        var ay = event.values[1]
                        var az = event.values[2]

                        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                            ax -= gravityFilter[0]
                            ay -= gravityFilter[1]
                            az -= gravityFilter[2]
                        }

                        // Vertical Force Calculation
                        val gMagnitude = sqrt(currentGravity[0]*currentGravity[0] + currentGravity[1]*currentGravity[1] + currentGravity[2]*currentGravity[2])
                        var verticalAccel = 0f
                        if (gMagnitude > 0) {
                            verticalAccel = (ax * currentGravity[0] + ay * currentGravity[1] + az * currentGravity[2]) / gMagnitude
                        }

                        sensorBuffer.add(BufferPoint(verticalAccel, now))
                        if (sensorBuffer.size > bufferSize) sensorBuffer.removeFirst()

                        var detectedTag: String? = null

                        if (sensorBuffer.size == bufferSize && (now - lastAlertTime > 1500)) {
                            val maxPoint = sensorBuffer.maxByOrNull { it.value }!!
                            val minPoint = sensorBuffer.minByOrNull { it.value }!!
                            val threshold = 3.5f

                            if (maxPoint.value > threshold && minPoint.value < -threshold) {
                                lastAlertTime = now
                                isAlertActive = true
                                playTingSound(context)
                                detectedTag = latestLocationStr

                                // Determine Type
                                val anomalyType = if (maxPoint.timestamp < minPoint.timestamp) "SPEED BREAKER" else "POTHOLE"
                                statusMessage = anomalyType

                                // --- CHANGED LOGIC HERE ---
                                // 1. Check if it is a POTHOLE (Ignore Speed Breakers for DB)
                                // 2. Check if we have valid GPS Coordinates
                                if (anomalyType == "POTHOLE" && currentLat != 0.0 && currentLng != 0.0) {
                                    val alertData = RoadAlert(
                                        type = "POTHOLE",
                                        latitude = currentLat,
                                        longitude = currentLng,
                                        timestamp = now
                                    )
                                    // PUSH creates a new unique entry every time
                                    database.push().setValue(alertData)
                                }

                                coroutineScope.launch {
                                    delay(3000)
                                    isAlertActive = false
                                    statusMessage = "Driving Mode Active"
                                }
                            }
                        }

                        graphData.add(GraphPoint(verticalAccel, now, detectedTag))
                        if (graphData.size > maxDataPoints) graphData.removeAt(0)
                    }
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }

            sensorManager.registerListener(listener, linearSensor, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
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
            Text("LIVE Z-AXIS DATA", color = TextGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            ContinuousSensorGraph(dataPoints = graphData)

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

            Spacer(modifier = Modifier.height(32.dp))

            SwipeToDriveButton(
                isDriving = driveMode,
                onSwipeComplete = {
                    driveMode = true
                    statusMessage = "Driving Mode Active"
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    checkAndEnableLocation(context) { intent -> settingResultRequest.launch(intent) }
                },
                onStopClick = {
                    driveMode = false
                    statusMessage = "Ready to Drive"
                    graphData.clear()
                    currentLat = 0.0
                    currentLng = 0.0
                }
            )
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

// --- KEEP THESE UI COMPONENTS ---
// They are perfect as they are.

@Composable
fun ContinuousSensorGraph(dataPoints: List<GraphPoint>) {
    val density = LocalDensity.current
    val textPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (dataPoints.isEmpty()) return@Canvas
            val width = size.width
            val height = size.height
            val midHeight = height / 2f

            drawLine(TextGrey.copy(alpha = 0.3f), Offset(0f, midHeight), Offset(width, midHeight), 2f)

            val maxRange = 10f
            val scale = (height / 2f) / maxRange
            val stepX = width / (dataPoints.size - 1).coerceAtLeast(1)
            val path = Path()

            dataPoints.forEachIndexed { index, point ->
                val x = index * stepX
                val y = midHeight - (point.value * scale).coerceIn(-midHeight + 10f, midHeight - 10f)

                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)

                if (point.locationTag != null) {
                    drawLine(TextGrey.copy(alpha = 0.5f), Offset(x, 10f), Offset(x, height - 25f), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    val dotColor = if (point.value > 0) ActiveGreen else AlertRed
                    drawCircle(dotColor, radius = 8f, center = Offset(x, y))
                    drawContext.canvas.nativeCanvas.drawText(point.locationTag, x, height - 12f, textPaint)
                }
            }
            drawPath(path, GraphLineColor, style = Stroke(width = 3.dp.toPx()))
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
            .height(220.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(bgColor, DarkBackground))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isAlert) Icons.Outlined.Report else Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = if (isAlert) AlertRed else Color.DarkGray,
            modifier = Modifier.size(if (isAlert) 120.dp else 100.dp)
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