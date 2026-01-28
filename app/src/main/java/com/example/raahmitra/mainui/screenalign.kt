package com.example.raahmitra

import android.graphics.Bitmap
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.SafetyCheck
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable

// --- IMPORTANT: Ensure these imports match your actual file structure ---

import com.example.raahmitra.mainui.ReportStatusScreen
import com.example.raahmitra.ui.theme.HomeScreen
import com.example.yourappname.ReportIssueScreen

// Enum for Routes
enum class ScreenName() {
    mainscreen,
    report_problem,
    status
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun screen_align(
    navController: NavHostController = rememberNavController(),
    accelX: Float, accelY: Float, accelZ: Float,
    gyroX: Float, gyroY: Float, gyroZ: Float,
    gravX: Float, gravY: Float, gravZ: Float
) {
    // --- HOISTED STATE (Shared Data) ---
    // These hold the data while we switch screens
    var sentDescription by remember { mutableStateOf("") }
    var sentLocation by remember { mutableStateOf("") }
    var sentImage by remember { mutableStateOf<Bitmap?>(null) }

    Scaffold(
        containerColor = Color(16, 16, 28, 255),
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // LAYER 1: Main Content
            AnimatedNavHost(
                navController = navController,
                startDestination = ScreenName.mainscreen.name,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { slideInHorizontally(initialOffsetX = { it / 8 }, animationSpec = tween(160)) + fadeIn(animationSpec = tween(120)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 8 }, animationSpec = tween(120)) + fadeOut(animationSpec = tween(90)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 8 }, animationSpec = tween(140)) + fadeIn(animationSpec = tween(100)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 8 }, animationSpec = tween(120)) + fadeOut(animationSpec = tween(90)) }
            ) {
                // 1. Home Screen
                composable(ScreenName.mainscreen.name) {
                    HomeScreen()
                }

                // 2. Report Input Screen
                composable(ScreenName.report_problem.name) {
                    ReportIssueScreen(
                        // FIX: Added explicit types here (String, String, Bitmap?)
                        onSubmitSuccess = { desc: String, loc: String, img: Bitmap? ->
                            sentDescription = desc
                            sentLocation = loc
                            sentImage = img
                            // Navigate to Status Screen
                            navController.navigate(ScreenName.status.name)
                        }
                    )
                }

                // 3. Status/History Screen
                composable(ScreenName.status.name) {
                    ReportStatusScreen(
                        // These can now safely be null because we updated the file above!
                        newReportDescription = sentDescription,
                        newReportLocation = sentLocation,
                        newReportImage = sentImage,
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
            }

            // LAYER 2: Floating Bottom Bar
            FloatingBottomBar(
                navController = navController,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
fun FloatingBottomBar(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp),
        shape = RoundedCornerShape(40.dp),
        elevation = CardDefaults.cardElevation(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(26, 33, 45, 255))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

            BottomBarItem(
                title = "Drive Mode",
                icon = Icons.Default.CarRepair,
                selected = currentRoute == ScreenName.mainscreen.name,
                onClick = { navController.navigate(ScreenName.mainscreen.name) },
                selectedColor = Color(0xFF275FF8),
                unselectedColor = Color.Gray
            )
            BottomBarItem(
                title = "Report",
                icon = Icons.Default.Report,
                // Highlight if we are on Report OR Status screen
                selected = currentRoute == ScreenName.report_problem.name || currentRoute == ScreenName.status.name,
                onClick = { navController.navigate(ScreenName.report_problem.name) },
                selectedColor = Color(0xFF2960FC),
                unselectedColor = Color.Gray
            )
            BottomBarItem(
                title = "Quiz", // Placeholder for now
                icon = Icons.Default.SafetyCheck,
                selected = false,
                onClick = { navController.navigate(ScreenName.status.name) },
                selectedColor = Color(0xFF2A61FD),
                unselectedColor = Color.Gray
            )
        }
    }
}

@Composable
fun BottomBarItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color,
    unselectedColor: Color
) {
    val iconColor = if (selected) selectedColor else unselectedColor
    val iconSize by animateDpAsState(
        targetValue = if (selected) 28.dp else 24.dp,
        animationSpec = tween(durationMillis = 200),
        label = "icon size"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
        if (selected) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = iconColor,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}