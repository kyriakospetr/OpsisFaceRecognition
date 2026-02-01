package com.example.opsisfacerecognition.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.opsisfacerecognition.R
import com.example.opsisfacerecognition.views.FaceScannerScreen
import com.example.opsisfacerecognition.views.FacePreparationScreen
import com.example.opsisfacerecognition.views.HomeScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(navController = navController)
        }

        composable("face_enroll_prep") {
            FacePreparationScreen(
                navController = navController,
                title = "Get Ready for Face Scan",
                subtitle = "Scan your face to add your identity",
                illustrationRes = R.drawable.face_scan,
                buttonText = "BEGIN FACE SCAN",
                tip = "After your initial scan, you can optionally set up mask recognition for seamless access.",
                onGo = "face_enroll_scan",
            )
        }
        composable("face_enroll_scan",
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }) {
            FaceScannerScreen(
                navController = navController
            )
        }
        composable("face_verify_prep") {
            FacePreparationScreen(
                navController = navController,
                title = "Verify Your Identity",
                subtitle = "Please ensure your face is clearly visible for the validation process.",
                illustrationRes = R.drawable.face_verify,
                buttonText = "START VERIFICATION",
                tip = "To verify your identity, you must first complete your initial face enrollment.",
                onGo = "face_verify_scan",
            )
        }
    }
}