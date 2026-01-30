package com.example.opsisfacerecognition.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Stable
data class CameraPermissionUiState(
    val showRationaleDialog: Boolean,
    val showSettingsDialog: Boolean,
    val dismissRationale: () -> Unit,
    val dismissSettings: () -> Unit
)

@Stable
class CameraPermissionRequester internal constructor(
    private val context: Context,
    private val launcher: () -> Unit,
    private val onGranted: () -> Unit
) {
    fun request() {
        // We check if we already have the permission before asking
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            onGranted()
        } else {
            launcher()
        }
    }

    // Helper method to open settings directly from the dialog
    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }
}

@Composable
fun rememberCameraPermissionRequester(
    onGranted: () -> Unit
): Pair<CameraPermissionRequester, CameraPermissionUiState> {

    val context = LocalContext.current
    val activity = context as? Activity

    // Keeps the latest onGranted lambda in case recomposition occurs
    val onGrantedState by rememberUpdatedState(onGranted)

    // Local UI state for permission dialogs
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Launcher that triggers the system permission dialog
    // and receives the result (granted or denied).
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Permission granted, clear dialogs and continue
            showRationaleDialog = false
            showSettingsDialog = false
            onGrantedState()
        } else {
            // Check whether we should explain the permission rationale
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false

            if (shouldShowRationale) {
                // Permission denied but can be requested again
                showRationaleDialog = true
            } else {
                // Permission permanently denied ("Don't ask again") or blocked (user denied many times)
                showSettingsDialog = true
            }
        }
    }

    val requester = remember(context) {
        CameraPermissionRequester(
            context = context,
            launcher = { launcher.launch(Manifest.permission.CAMERA) },
            onGranted = { onGrantedState() }
        )
    }

    // Exposes the UI state for the screen to render dialogs.
    val uiState = remember(showRationaleDialog, showSettingsDialog) {
        CameraPermissionUiState(
            showRationaleDialog = showRationaleDialog,
            showSettingsDialog = showSettingsDialog,
            dismissRationale = { showRationaleDialog = false },
            dismissSettings = { showSettingsDialog = false }
        )
    }

    return requester to uiState
}
