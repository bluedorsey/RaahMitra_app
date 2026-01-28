package com.example.yourappname // TODO: CHECK THIS PACKAGE NAME

import android.Manifest
import android.graphics.Bitmap
import android.location.Geocoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Locale

// --- Color Palette ---
val BackgroundColor = Color(0xFF0D1117)
val CardColor = Color(0xFF161B22)
val PrimaryBlue = Color(0xFF2962FF)
val TextWhite = Color.White
val TextGray = Color(0xFF8B949E)
val AiPurple = Color(0xFFD500F9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueScreen(onSubmitSuccess: (String, String, Bitmap?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- State Management ---
    // Added "Other" just in case the AI is unsure
    val categories = listOf("Overflow trashBin", "Flood", "Poor build canal", "Watertank issue", "Other")
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var description by remember { mutableStateOf("") }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var locationAddress by remember { mutableStateOf("Tap camera to fetch location") }

    // Loading States
    var isLocationLoading by remember { mutableStateOf(false) }
    var isAiAnalyzing by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    // --- GEMINI AI CONFIGURATION ---
    val generativeModel = remember {
        GenerativeModel(
            // NAME NOTE: "gemini-2.5" does not exist.
            // Use "gemini-1.5-flash" OR "gemini-2.0-flash-exp" (if available in your region)
            modelName = "gemini-2.5-flash",
            apiKey = "",
            generationConfig = generationConfig {
                temperature = 0.4f
                // FIX: Increased to 4096 to stop "MAX_TOKEN" error

            }
        )
    }

    // --- 1. AI Logic: Auto-Select Category & Description ---
    fun generateAIDescription() {
        if (capturedImage == null) {
            Toast.makeText(context, "Take a photo first!", Toast.LENGTH_SHORT).show()
            return
        }
        isAiAnalyzing = true

        scope.launch {
            try {
                // Updated Prompt to handle "Laptop Screen" photos
                val inputPrompt = """
                    Analyze this image. NOTE: This might be a photo of a laptop screen or monitor. 
                    Ignore screen artifacts (glare, pixels, wavy lines) and focus on the CONTENT shown on the screen.
                    
                    1. Select the BEST MATCH from this specific list:
                    [${categories.joinToString(", ")}]
                    
                    2. Write a short description (max 2 sentences).
                    
                    Output format:
                    CATEGORY: [Exact Category Name]
                    DESCRIPTION: [Your description]
                """.trimIndent()

                val response = generativeModel.generateContent(
                    content {
                        image(capturedImage!!)
                        text(inputPrompt)
                    }
                )

                val text = response.text ?: ""

                // --- Parsing Logic ---
                val descLine = text.lines().find { it.uppercase().startsWith("DESCRIPTION:") }
                if (descLine != null) {
                    description = descLine.substringAfter(":").trim()
                } else {
                    description = text.replace("CATEGORY:", "").trim()
                }

                // Smart Category Match
                val matchedCategory = categories.find { category ->
                    text.contains(category, ignoreCase = true)
                }

                if (matchedCategory != null) {
                    selectedCategory = matchedCategory
                } else {
                    // Optional: Default to "Other" if no match found
                    selectedCategory = "Other"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // Show the specific error to help debug
                Toast.makeText(context, "AI Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isAiAnalyzing = false
            }
        }
    }

    // --- 2. Database Logic ---
    fun uploadAndSubmit() {
        isSubmitting = true
        val firestore = FirebaseFirestore.getInstance()

        val reportData = hashMapOf(
            "userName" to "Ashutosh",
            "category" to selectedCategory,
            "description" to description,
            "location" to locationAddress,
            "imageUrl" to "Image upload skipped (Text only mode)",
            "timestamp" to Timestamp.now(),
            "status" to "pending"
        )

        firestore.collection("reports")
            .add(reportData)
            .addOnSuccessListener {
                isSubmitting = false
                showConfirmationDialog = false
                Toast.makeText(context, "Report Sent Successfully!", Toast.LENGTH_LONG).show()
                onSubmitSuccess(description, locationAddress, capturedImage)
            }
            .addOnFailureListener { e ->
                isSubmitting = false
                Toast.makeText(context, "Database Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- 3. Location Logic ---
    fun fetchLocation() {
        isLocationLoading = true
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        locationAddress = addresses?.firstOrNull()?.getAddressLine(0)
                            ?: "${location.latitude}, ${location.longitude}"
                    } catch (e: Exception) {
                        locationAddress = "${location.latitude}, ${location.longitude}"
                    }
                }
                isLocationLoading = false
            }
        } catch (e: SecurityException) {
            isLocationLoading = false
        }
    }

    // Launchers
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            capturedImage = bitmap
            fetchLocation()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val cameraGranted = perms[Manifest.permission.CAMERA] == true
        if (cameraGranted) {
            cameraLauncher.launch()
        }
    }

    // --- UI LAYOUT ---
    Scaffold(
        containerColor = BackgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Report Issue", color = TextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { /* Handle Back */ }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundColor)
            )
        }
    ) { paddingValues ->

        if (showConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmationDialog = false },
                containerColor = CardColor,
                title = { Text("Confirm Report", color = TextWhite) },
                text = {
                    Column {
                        Text("User: Ashutosh", color = TextGray)
                        Text("Category: $selectedCategory", color = TextGray)
                        Text("Location: ${locationAddress.take(30)}...", color = TextGray)
                        Spacer(Modifier.height(8.dp))
                        Text("Are the details correct?", color = TextWhite)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { uploadAndSubmit() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Yes, Submit")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmationDialog = false }) {
                        Text("Edit", color = TextGray)
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("ISSUE CATEGORY")

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategory
                    Surface(
                        onClick = { selectedCategory = category },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) PrimaryBlue else CardColor,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                category,
                                color = if (isSelected) TextWhite else TextGray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("DESCRIPTION")
                SmallFloatingActionButton(
                    onClick = { generateAIDescription() },
                    containerColor = if (isAiAnalyzing) CardColor else AiPurple,
                    contentColor = TextWhite,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isAiAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AiPurple)
                    } else {
                        Icon(Icons.Default.AutoAwesome, "AI", modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            DescriptionInput(value = description, onValueChange = { description = it })

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("EVIDENCE")
            Spacer(modifier = Modifier.height(12.dp))

            EvidenceSection(
                imageBitmap = capturedImage,
                onAddPhotoClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onRemovePhotoClick = { capturedImage = null }
            )

            Spacer(modifier = Modifier.height(24.dp))
            LocationCard(address = locationAddress, isLoading = isLocationLoading)

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (description.isNotEmpty() && capturedImage != null) {
                        showConfirmationDialog = true
                    } else {
                        Toast.makeText(context, "Please add a photo and description", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Verify & Submit", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

// --- HELPER COMPOSABLES ---

@Composable
fun DescriptionInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Describe the issue or tap the AI star button...", color = TextGray) },
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CardColor,
            unfocusedContainerColor = CardColor,
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite,
            focusedBorderColor = PrimaryBlue.copy(0.5f),
            unfocusedBorderColor = Color.Transparent
        )
    )
}

@Composable
fun EvidenceSection(imageBitmap: Bitmap?, onAddPhotoClick: () -> Unit, onRemovePhotoClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(110.dp)
                .dashedBorder(color = TextGray, radius = 16.dp)
                .clickable { onAddPhotoClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(PrimaryBlue.copy(0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PhotoCamera, null, tint = PrimaryBlue)
                }
                Text("Add Photo", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        if (imageBitmap != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    bitmap = imageBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(0.6f), CircleShape)
                        .clickable { onRemovePhotoClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, null, tint = TextWhite, modifier = Modifier.size(14.dp))
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun LocationCard(address: String, isLoading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(Color(0xFF1B253E), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = PrimaryBlue)
            } else {
                Icon(Icons.Default.LocationOn, null, tint = PrimaryBlue)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Current Location", color = TextWhite, fontWeight = FontWeight.Bold)
            Text(address, color = TextGray, fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

fun Modifier.dashedBorder(strokeWidth: Float = 2f, color: Color, radius: androidx.compose.ui.unit.Dp) = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        ),
        cornerRadius = CornerRadius(radius.toPx())
    )
}