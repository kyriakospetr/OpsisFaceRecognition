package com.example.opsisfacerecognition.ui.components

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    lensFacing: Int = CameraSelector.LENS_FACING_FRONT
) {
    // Context needed to access CameraX and system services
    val context = LocalContext.current


    // LifecycleOwner is required so CameraX can bind/unbind automatically
    // when the screen enters or leaves the foreground
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // PreviewView is an Android View that displays the live camera feed
    // We remember it so it is not recreated on every recomposition
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // This effect runs when the composable enters the composition
    // or when the lensFacing parameter changes
    LaunchedEffect(lensFacing) {

        // CameraProvider gives access to the camera lifecycle
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        // Select the camera (front or back)
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            // Unbind any previously bound use cases before rebinding
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to bind camera preview", e)
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
