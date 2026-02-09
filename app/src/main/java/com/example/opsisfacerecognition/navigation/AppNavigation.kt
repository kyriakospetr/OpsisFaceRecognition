package com.example.opsisfacerecognition.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.example.opsisfacerecognition.R
import com.example.opsisfacerecognition.core.states.FaceFlowMode
import com.example.opsisfacerecognition.viewmodel.FaceRecognizerViewModel
import com.example.opsisfacerecognition.ui.EnrollScreen
import com.example.opsisfacerecognition.ui.EnrollSuccessScreen
import com.example.opsisfacerecognition.ui.HomeScreen
import com.example.opsisfacerecognition.ui.PrepScreen
import com.example.opsisfacerecognition.ui.ProcessFailed
import com.example.opsisfacerecognition.ui.ScannerScreen
import com.example.opsisfacerecognition.ui.SettingsScreen
import com.example.opsisfacerecognition.ui.VerifySuccess

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(navController = navController)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(navController = navController)
        }

        composable(Routes.ENROLL_PREP) {
            PrepScreen(
                navController = navController,
                title = "Get Ready for Face Scan",
                subtitle = "Scan your face to add your identity",
                illustrationRes = R.drawable.face_scan,
                buttonText = "Begin face scan",
                tip = "A clear and stable capture improves enrollment quality and verification accuracy.",
                onGo = Routes.ENROLL_GRAPH,
            )
        }
        composable(Routes.VERIFY_PREP) {
            PrepScreen(
                navController = navController,
                title = "Verify Your Identity",
                subtitle = "Please ensure your face is clearly visible for the validation process.",
                illustrationRes = R.drawable.face_verify,
                buttonText = "Start verification",
                tip = "To verify your identity, you must first complete your initial face enrollment.",
                onGo = Routes.VERIFY_GRAPH,
            )
        }

        // We use navgraph so our viewmodel instance is alive
        // We need the pending user from the face recognizer viewmodel
        // Because we first take the embedding and then we ask for the full name
        navigation(route = Routes.ENROLL_GRAPH, startDestination = Routes.ENROLL_SCAN) {
            composable(
                route = Routes.ENROLL_SCAN,
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.ENROLL_GRAPH)
                }
                val viewModel: FaceRecognizerViewModel = hiltViewModel(parentEntry)
                ScannerScreen(
                    navController = navController,
                    title = "Face Enrollment",
                    subTitle = "Align your face with the guide. We’ll capture when it’s stable.",
                    popUpToRoute = Routes.ENROLL_GRAPH,
                    backRoute = Routes.ENROLL_PREP,
                    mode = FaceFlowMode.ENROLL,
                    viewModel = viewModel
                )
            }
            composable(Routes.ENROLL_PROCESSED) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.ENROLL_GRAPH)
                }
                val viewModel: FaceRecognizerViewModel = hiltViewModel(parentEntry)
                EnrollScreen(
                    navController = navController,
                    title = "Enrollment Completed!",
                    description = "Great! We've successfully captured your scan. Please provide a name below to complete your profile.",
                    viewmodel = viewModel
                )
            }
            composable(Routes.ENROLL_SUCCESS) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.ENROLL_GRAPH)
                }
                val viewModel: FaceRecognizerViewModel = hiltViewModel(parentEntry)
                EnrollSuccessScreen(
                    navController = navController,
                    title = "Enrollment Successful",
                    description = "Your profile has been saved successfully. You can now verify your identity at any time.",
                    viewmodel = viewModel
                )
            }
            composable(Routes.ENROLL_FAILED) {
                ProcessFailed(
                    navController = navController,
                    title = "Enrollment Failed",
                    description = "We couldn't complete enrollment. Please ensure your face is clearly visible and try again.",
                    onGoToRoute = Routes.HOME
                )
            }
        }

        navigation(route = Routes.VERIFY_GRAPH, startDestination = Routes.VERIFY_SCAN) {
            composable(
                Routes.VERIFY_SCAN,
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.VERIFY_GRAPH)
                }
                val viewModel: FaceRecognizerViewModel = hiltViewModel(parentEntry)
                ScannerScreen(
                    navController = navController,
                    title = "Face Verification",
                    subTitle = "Align your face to verify your identity.",
                    popUpToRoute = Routes.VERIFY_GRAPH,
                    backRoute = Routes.VERIFY_PREP,
                    mode = FaceFlowMode.VERIFY,
                    viewModel = viewModel
                )
            }
            composable(Routes.VERIFY_SUCCESS) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.VERIFY_GRAPH)
                }
                val viewModel: FaceRecognizerViewModel = hiltViewModel(parentEntry)
                VerifySuccess(
                    navController = navController,
                    title = "Identity Verified",
                    description = "Your identity has been successfully verified.",
                    viewmodel = viewModel
                )
            }
            composable(Routes.VERIFY_FAILED) {
                ProcessFailed(
                    navController = navController,
                    title = "Verification Failed",
                    description = "We couldn't verify your identity. Please make sure your face is clearly visible and try again.",
                    onGoToRoute = Routes.HOME
                )
            }
        }
    }
}
