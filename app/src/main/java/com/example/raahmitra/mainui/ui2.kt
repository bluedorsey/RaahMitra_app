package com.example.raahmitra.mainui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Theme Colors ---
val StatusDarkBackground = Color(0xFF0D1117)
val StatusCardColor = Color(0xFF161B22)
val StatusPrimaryBlue = Color(0xFF2962FF)
val StatusTextWhite = Color.White
val StatusTextGray = Color(0xFF8B949E)
val StatusGreen = Color(0xFF2EA043)
val StatusOrange = Color(0xFFD29922)
val StatusRed = Color(0xFFD32F2F) // For Delete Button

data class ReportModel(
    val id: String,
    val description: String,
    val location: String,
    val date: String,
    val status: String,
    val image: Bitmap? = null
)

@Composable
fun ReportStatusScreen(
    newReportDescription: String? = null,
    newReportLocation: String? = null,
    newReportImage: Bitmap? = null,
    onBackClick: () -> Unit
) {
    // 1. Mutable List for Deletion Support
    val reportList = remember {
        mutableStateListOf(
            ReportModel("RPT-1092", "Deep pothole on highway", "Lat: 22.1, Lon: 82.3", "Today, 10:30 AM", "In Review"),
            ReportModel("RPT-1045", "Street light broken", "Sector 4, Main Rd", "Yesterday, 04:15 PM", "Pending")
        )
    }

    // Add new report logic
    LaunchedEffect(newReportDescription) {
        if (!newReportDescription.isNullOrEmpty()) {
            val newItem = ReportModel(
                id = "RPT-${(1000..9999).random()}",
                description = newReportDescription,
                location = newReportLocation ?: "Unknown Location",
                date = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date()),
                status = "In Review",
                image = newReportImage
            )
            // Prevent duplicates
            if (reportList.none { it.description == newReportDescription }) {
                reportList.add(0, newItem)
            }
        }
    }

    // --- State for Deletion & Navigation ---
    var selectedReport by remember { mutableStateOf<ReportModel?>(null) }
    var itemToDelete by remember { mutableStateOf<ReportModel?>(null) } // Tracks item to delete
    var showDeleteDialog by remember { mutableStateOf(false) } // Toggles dialog

    // --- Logic: Delete Item ---
    if (showDeleteDialog && itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = StatusCardColor,
            title = { Text("Delete Report?", color = StatusTextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove this report? This action cannot be undone.", color = StatusTextGray) },
            confirmButton = {
                Button(
                    onClick = {
                        reportList.remove(itemToDelete)
                        showDeleteDialog = false
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = StatusTextGray)
                }
            }
        )
    }

    if (selectedReport == null) {
        // Pass the long-click logic to the list view
        ReportListView(
            reports = reportList,
            onItemClick = { selectedReport = it },
            onItemLongClick = { report ->
                itemToDelete = report
                showDeleteDialog = true
            },
            onBackClick = onBackClick
        )
    } else {
        ReportDetailView(selectedReport!!, { selectedReport = null })
    }
}

// --- List View with Long Press ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportListView(
    reports: List<ReportModel>,
    onItemClick: (ReportModel) -> Unit,
    onItemLongClick: (ReportModel) -> Unit, // New Callback
    onBackClick: () -> Unit
) {
    Scaffold(
        containerColor = StatusDarkBackground,
        topBar = { TopAppBar(title = { Text("Your Reports", color = StatusTextWhite) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = StatusDarkBackground)) }
    ) { p ->
        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(p), Alignment.Center) {
                Text("No reports found", color = StatusTextGray)
            }
        } else {
            LazyColumn(Modifier.padding(p).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(reports) { item ->
                    ReportItemCard(
                        report = item,
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) } // Pass Long Click
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class) // Required for combinedClickable
@Composable
fun ReportItemCard(
    report: ReportModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // 🔴 CHANGED: specific long-click modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(StatusCardColor)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val color = if (report.status == "Resolved") StatusGreen else StatusOrange
            Box(Modifier.size(48.dp).background(color.copy(0.15f), CircleShape), Alignment.Center) {
                Icon(Icons.Default.Refresh, null, tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(report.description, color = StatusTextWhite, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${report.date} • ${report.status}", color = StatusTextGray, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = StatusTextGray)
        }
    }
}

// --- Detail View (Unchanged Logic, reusing components) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailView(report: ReportModel, onBackClick: () -> Unit) {
    Scaffold(
        containerColor = StatusDarkBackground,
        topBar = { TopAppBar(title = { Text("Details", color = StatusTextWhite) }, navigationIcon = { IconButton(onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = StatusTextWhite) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = StatusDarkBackground)) }
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
            StatusBanner(report.status, report.id)
            Spacer(Modifier.height(24.dp))
            Text("EVIDENCE", color = StatusTextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(StatusCardColor), Alignment.Center) {
                if (report.image != null) Image(report.image.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("No Image", color = StatusTextGray)
            }
            Spacer(Modifier.height(24.dp))
            DetailCard("DESCRIPTION", report.description, Icons.Default.CheckCircle)
            Spacer(Modifier.height(16.dp))
            DetailCard("LOCATION", report.location, Icons.Default.LocationOn)
        }
    }
}

// Helpers
@Composable
fun StatusBanner(status: String, id: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(StatusCardColor)) {
        Row(Modifier.padding(20.dp)) {
            Column { Text("Status: $status", color = StatusOrange, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("ID: $id", color = StatusTextGray) }
        }
    }
}

@Composable
fun DetailCard(title: String, content: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column {
        Text(title, color = StatusTextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(StatusCardColor).padding(16.dp)) {
            Row { Icon(icon, null, tint = StatusPrimaryBlue, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(content, color = StatusTextWhite) }
        }
    }
}