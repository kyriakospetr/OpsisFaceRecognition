package com.example.opsisfacerecognition.core.ui.components

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.opsisfacerecognition.core.biometrics.FaceAnalyzer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraPreviewWithAnalysis(
    modifier: Modifier = Modifier,
    analyzer: FaceAnalyzer
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // We use different executor so we don't overload the main one
    val cameraExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

    // Create the PreviewView once and remember it.
    // We do this outside AndroidView so we can reference it in the LaunchedEffect.
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // We close the executor when we leave the screen in order to prevent memory leaks
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // The LaunchedEffect block runs only when analyzer, lifecycleOwner change.
    // This prevents the camera from re-binding unnecessary when other UI elements like text update.
    LaunchedEffect(analyzer, lifecycleOwner) {
        val cameraProvider = context.getCameraProvider()

        // Configuration
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO))
            .setResolutionStrategy(ResolutionStrategy(Size(480, 360), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()

        // Use Cases
        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().apply {
                setAnalyzer(cameraExecutor, analyzer)
            }

        try {
            // Unbind everything before binding new use cases.
            cameraProvider.unbindAll()

            // The camera automatically stops when the app goes to background.
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                analysis
            )
        } catch (e: Exception) {
            Log.e("CameraPreview", "Binding failed", e)
        }
    }

    // The UI Rendering displays the view we created
    AndroidView(
        modifier = modifier,
        factory = { previewView }
    )
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val processCameraProvider = ProcessCameraProvider.getInstance(this)
    processCameraProvider.addListener({
        continuation.resume(processCameraProvider.get())
    }, ContextCompat.getMainExecutor(this))
}