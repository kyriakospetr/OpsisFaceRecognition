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
import com.example.opsisfacerecognition.views.EnrollScreen
import com.example.opsisfacerecognition.views.EnrollSuccessScreen
import com.example.opsisfacerecognition.views.HomeScreen
import com.example.opsisfacerecognition.views.PrepScreen
import com.example.opsisfacerecognition.views.ProcessFailed
import com.example.opsisfacerecognition.views.ScannerScreen
import com.example.opsisfacerecognition.views.VerifySuccess

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

        composable(Routes.ENROLL_PREP) {
            PrepScreen(
                navController = navController,
                title = "Get Ready for Face Scan",
                subtitle = "Scan your face to add your identity",
                illustrationRes = R.drawable.face_scan,
                buttonText = "Begin face scan",
                tip = "After your initial scan, you can optionally set up mask recognition for seamless access.",
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
        // And second we need to match the masked embedding to the correct user
        navigation(route = Routes.ENROLL_GRAPH, startDestination = Routes.ENROLL_SCAN) {
            composable(
                route = Routes.ENROLL_SCAN,
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                val parentEntry = remember {
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
            composable(Routes.ENROLL_PROCESSED) {
                val parentEntry = remember {
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
            composable(Routes.ENROLL_SUCCESS) {
                val parentEntry = remember {
                    navController.getBackStackEntry(Routes.ENROLL_GRAPH)
                }
                val viewModel: FaceRecognizerViewModel = hiltViewModel(parentEntry)
                EnrollSuccessScreen(
                    navController = navController,
                    title = "Enrollment Successful",
                    description = "Your profile has been saved. You can now verify your identity anytime.",
                    viewmodel = viewModel
                )
            }
            composable(Routes.ENROLL_MASKED_FAILED) {
                ProcessFailed(
                    navController = navController,
                    title = "Not Available Yet",
                    description = "Coming soon",
                    onGoToRoute = Routes.HOME
                )
            }

            composable(
                route = Routes.ENROLL_MASKED_SCAN,
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                val parentEntry = remember {
                    navController.getBackStackEntry(Routes.ENROLL_GRAPH)
                }
                val viewModel: FaceRecognizerViewModel = hiltViewModel(parentEntry)
                ScannerScreen(
                    navController = navController,
                    title = "Mask Face Enrollment",
                    subTitle = "Please wear your mask and align your face within the guide. We’ll capture the scan when it’s stable.",
                    popUpToRoute = Routes.ENROLL_GRAPH,
                    backRoute = Routes.ENROLL_SUCCESS,
                    mode = FaceFlowMode.ENROLL_MASKED,
                    viewModel = viewModel
                )
            }
        }

        navigation(route = Routes.VERIFY_GRAPH, startDestination = Routes.VERIFY_SCAN) {
            composable(
                Routes.VERIFY_SCAN,
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                val parentEntry = remember {
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
            composable(Routes.VERIFY_SUCCESS) {
                val parentEntry = remember {
                    navController.getBackStackEntry(Routes.VERIFY_GRAPH)
                }
                val viewModel: FaceRecognizerViewModel = hiltViewModel(parentEntry)
                VerifySuccess(
                    navController = navController,
                    title = "Identity Verified",
                    description = "Your identity has been successfully verified. You can now continue securely.",
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
