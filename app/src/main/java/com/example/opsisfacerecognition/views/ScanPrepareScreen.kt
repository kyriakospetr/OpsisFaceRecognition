package com.example.opsisfacerecognition.views

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.example.opsisfacerecognition.R
import com.example.opsisfacerecognition.core.rememberCameraPermissionRequester
import com.example.opsisfacerecognition.ui.components.FacePrepContent
import com.example.opsisfacerecognition.ui.dialogs.PermissionDialog

@Composable
fun ScanPrepareScreen(
    navController: NavController
) {
    val (camera, ui) = rememberCameraPermissionRequester(
        onGranted = { navController.navigate("face_scan") }
    )

    FacePrepContent(
        title = "Get Ready for Face Scan",
        subtitle = "Scan your face to add your identity",
        illustrationRes = R.drawable.face_scan,
        buttonText = "BEGIN FACE SCAN",
        tip = "After your initial scan, you can optionally set up mask recognition for seamless access.",
        onPrimaryClick = { camera.request() },
        onBack = { navController.popBackStack() }
    )

    if (ui.showRationaleDialog) {
        PermissionDialog(
            title = "Camera permission required",
            message = "Camera access is required to perform face scanning.",
            confirmText = "Allow",
            dismissText = "Cancel",
            onConfirm = {
                ui.dismissRationale()
                camera.request()
            },
            onDismiss = ui.dismissRationale
        )
    }

    if (ui.showSettingsDialog) {
        PermissionDialog(
            title = "Permission permanently denied",
            message = "Camera access has been blocked. Please enable it from app settings.",
            confirmText = "Open settings",
            dismissText = "Cancel",
            onConfirm = {
                ui.dismissSettings()
                camera.openAppSettings()
            },
            onDismiss = ui.dismissSettings
        )
    }
}
