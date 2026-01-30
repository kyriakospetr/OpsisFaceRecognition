package com.example.opsisfacerecognition.views

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.example.opsisfacerecognition.R
import com.example.opsisfacerecognition.ui.components.FacePrepContent

@Composable
fun ScanPrepareScreen(navController: NavController) {
    FacePrepContent(
        navController = navController,
        title = "Get Ready for Face Scan",
        subtitle = "Scan your face to add your identity",
        illustrationRes = R.drawable.face_scan,
        buttonText = "BEGIN FACE SCAN",
        tip = "After your initial scan, you can optionally set up mask recognition for seamless access.",
        onPrimaryClick = { TODO("NAVIGATE TO SCAN FACE") }
    )
}