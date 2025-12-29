import android.Manifest
import android.content.Context
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import java.util.Locale

// --- Color Palette ---
val BackgroundColor = Color(0xFF0D1117)
val CardColor = Color(0xFF161B22)
val PrimaryBlue = Color(0xFF2962FF)
val TextWhite = Color.White
val TextGray = Color(0xFF8B949E)
val GreenSuccess = Color(0xFF2EA043)
val AiPurple = Color(0xFFD500F9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueScreen(onSubmitSuccess: (String, String, Bitmap?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- State Management ---
    var description by remember { mutableStateOf("") }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var locationAddress by remember { mutableStateOf("Tap camera to fetch location") }
    var isLocationLoading by remember { mutableStateOf(false) }
    var isAiAnalyzing by remember { mutableStateOf(false) }

    // --- ADVANCED GEMINI CONFIGURATION ---
    val generativeModel = remember {
        GenerativeModel(
            modelName = "gemini-2.0-flash-001",
            apiKey = "", // <---  KEY HERE
            systemInstruction = content {
                text(
                    "In addition to structured classification, you must generate a short, clear,\n" +
                            "human-readable paragraph describing the detected civic issue.\n" +
                            "\n" +
                            "Description rules:\n" +
                            "- Write like a municipal field report, not marketing text.\n" +
                            "- Be factual, neutral, and concise.\n" +
                            "- Do not speculate beyond visible evidence.\n" +
                            "- Do not mention AI, models, or probabilities.\n" +
                            "- If the image is INVALID or NO_ISSUE, explain that clearly in one sentence.\n" +
                            "- The description must be understandable by non-technical government staff.\n"
                )
            },

            // 2. CONFIGURATION: Controls creativity & length
            generationConfig = generationConfig {
                temperature = 0.4f       // Low randomness = Factual answers
                topK = 32
                topP = 1f
                maxOutputTokens = 200    // Limit length so it fits the text box
            }
        )
    }

    // --- Logic Helpers ---

    // 1. AI Logic: Image -> Text
    fun generateAIDescription() {
        if (capturedImage == null) {
            Toast.makeText(context, "Take a photo first!", Toast.LENGTH_SHORT).show()
            return
        }

        isAiAnalyzing = true
        description = "AI Assessor is analyzing..."

        scope.launch {
            try {
                // We don't need a complex prompt here because System Instructions handle the rules
                val inputPrompt = "Analyze the provided image as a municipal field inspector.\n" +
                        "\n" +
                        "Tasks:\n" +
                        "1. Verify image authenticity.\n" +
                        "2. Detect and classify any public civic issue.\n" +
                        "3. Assess severity and public safety risk.\n" +
                        "4. Generate a short descriptive paragraph suitable for a civic report.\n" +
                        "5. Reject fake, reused, edited, or irrelevant images.\n" +
                        "\n" +
                        "Allowed issue categories:\n" +
                        "- STREET_LIGHT_FAILURE\n" +
                        "- FLOODING\n" +
                        "- UNWANTED_VEGETATION\n" +
                        "- GARBAGE_ACCUMULATION\n" +
                        "- POTHOLE_OR_ROAD_DAMAGE\n" +
                        "- DRAINAGE_BLOCKAGE\n" +
                        "- NO_ISSUE\n" +
                        "- INVALID_IMAGE\n" +
                        "\n" +
                        "Validation rules:\n" +
                        "- Screenshots, images of screens, social media images, AI-generated visuals, watermarked or edited images → INVALID_IMAGE.\n" +
                        "- If uncertain, choose INVALID_IMAGE instead of guessing.\n" +
                        "- Only consider visible, public infrastructure issues.\n" +
                        "\n" +
                        "Return output strictly in JSON using the format below.\n"

                val response = generativeModel.generateContent(
                    content {
                        image(capturedImage!!)
                        text(inputPrompt)
                    }
                )
                description = response.text ?: "AI could not verify damage."
            } catch (e: Exception) {
                if (e.localizedMessage?.contains("Unable to resolve host") == true) {
                    description = "Error: No Internet Connection."
                } else {
                    description = "Error: ${e.localizedMessage}"
                }
            } finally {
                isAiAnalyzing = false
            }
        }
    }

    // 2. Location Logic
    fun fetchLocation() {
        isLocationLoading = true
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        locationAddress = addresses?.firstOrNull()?.getAddressLine(0)
                            ?: "${location.latitude}, ${location.longitude}"
                    } catch (e: Exception) {
                        locationAddress = "${location.latitude}, ${location.longitude}"
                    }
                } else {
                    locationAddress = "Location not found (Enable GPS)"
                }
                isLocationLoading = false
            }
        } catch (e: SecurityException) {
            isLocationLoading = false
        }
    }

    // 3. Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedImage = bitmap
            fetchLocation()
        }
    }

    // 4. Permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true &&
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            cameraLauncher.launch()
        } else {
            Toast.makeText(context, "Permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = BackgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Report Issue", color = TextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextWhite) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundColor)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("ISSUE CATEGORY")
            CategorySelector()

            Spacer(modifier = Modifier.height(24.dp))

            // --- Description Header with AI Button ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("DESCRIPTION")

                // AI TRIGGER BUTTON
                SmallFloatingActionButton(
                    onClick = { generateAIDescription() },
                    containerColor = if(isAiAnalyzing) CardColor else AiPurple,
                    contentColor = TextWhite,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isAiAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AiPurple, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, "AI Generate", modifier = Modifier.size(18.dp))
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
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
                    )
                },
                onRemovePhotoClick = { capturedImage = null }
            )

            Spacer(modifier = Modifier.height(24.dp))

            LocationCard(address = locationAddress, isLoading = isLocationLoading)

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { Toast.makeText(context, "Report Submitted!", Toast.LENGTH_SHORT).show()
                    onSubmitSuccess(description, locationAddress, capturedImage)},
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Submit Report", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

// --- Sub-Composables ---

@Composable
fun DescriptionInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Describe the issue or tap the AI star button...", color = TextGray, fontSize = 14.sp) },
        modifier = Modifier.fillMaxWidth().height(150.dp),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CardColor,
            unfocusedContainerColor = CardColor,
            disabledContainerColor = CardColor,
            cursorColor = PrimaryBlue,
            focusedBorderColor = PrimaryBlue.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite
        )
    )
}

@Composable
fun EvidenceSection(imageBitmap: Bitmap?, onAddPhotoClick: () -> Unit, onRemovePhotoClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.weight(1f).height(110.dp)
                .dashedBorder(color = TextGray, radius = 16.dp)
                .clickable { onAddPhotoClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(40.dp).background(PrimaryBlue.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PhotoCamera, null, tint = PrimaryBlue)
                }
                Text("Add Photo", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(top=8.dp))
            }
        }

        if (imageBitmap != null) {
            Box(modifier = Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(16.dp))) {
                Image(bitmap = imageBitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                        .background(Color.Black.copy(0.6f), CircleShape).clickable { onRemovePhotoClick() },
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(Color(0xFF1B253E), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = PrimaryBlue, strokeWidth = 2.dp)
            else Icon(Icons.Default.LocationOn, null, tint = PrimaryBlue)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Current Location", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(address, color = TextGray, fontSize = 12.sp, maxLines = 2)
        }
        if (!isLoading && !address.startsWith("Permission")) Icon(Icons.Default.CheckCircle, null, tint = GreenSuccess)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
fun CategorySelector() {
    val categories = listOf("Overflow trashBin", "Flood", "Poor build canal", "watertank issue")
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            Surface(
                onClick = { selectedCategory = category },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) PrimaryBlue else CardColor,
                modifier = Modifier.height(40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(category, color = if (isSelected) TextWhite else TextGray, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

fun Modifier.dashedBorder(strokeWidth: Float = 2f, color: Color, radius: androidx.compose.ui.unit.Dp) = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)),
        cornerRadius = CornerRadius(radius.toPx())
    )
}



