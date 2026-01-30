package com.example.opsisfacerecognition.core

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.opsisfacerecognition.views.HomeScreen
import com.example.opsisfacerecognition.views.ScanFaceScreen
import com.example.opsisfacerecognition.views.ScanPrepareScreen
import com.example.opsisfacerecognition.views.VerifyIdentityPrepScreen

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

        composable("face_scan_prep") {
            ScanPrepareScreen(navController = navController)
        }
        composable("face_scan") {
            ScanFaceScreen(navController = navController)
        }
        composable("face_verify_prep") {
            VerifyIdentityPrepScreen(navController = navController)
        }
    }
}