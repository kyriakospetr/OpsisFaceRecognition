package com.example.opsisfacerecognition.core.biometrics

import android.content.Context
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

class FaceDetector(
    private val ovalCenter: Offset,
    private val ovalRadiusX: Float,
    private val ovalRadiusY: Float,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val onFacesDetected: (List<Face>) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val DETECTION_THROTTLE_MS = 100L
        private const val POSITION_TOLERANCE = 0.5f
        private const val MIN_FACE_SIZE_RATIO = 0.8f
    }

    // Scope to run detection on a background thread to prevent the UI from freezing
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Google's ML Kit Face Detection client
    private val faceDetector = FaceDetection.getClient()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        scope.launch {
            // Extract the media image. If null, close proxy and abort.
            val mediaImage = imageProxy.image ?: run { imageProxy.close(); return@launch }

            // Images from front-camera are rotated
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            // Since they are rotated we swap width, height
            val imageWidth = imageProxy.height
            val imageHeight = imageProxy.width

            // Calculate scaling to map camera coordinates to screen pixels
            val scaleX = screenWidth / imageWidth.toFloat()
            val scaleY = screenHeight / imageHeight.toFloat()
            val scale = maxOf(scaleX, scaleY)

            // Suspend until ML Kit processing is complete
            suspendCoroutine { continuation ->
                faceDetector.process(inputImage)
                    .addOnSuccessListener { faces ->

                        // Check if any face is inside our UI oval
                        val facesInsideOval = faces.filter { face ->
                            val rawCenterX = face.boundingBox.centerX().toFloat()
                            val rawCenterY = face.boundingBox.centerY().toFloat()

                            val screenX = screenWidth - (rawCenterX * scale)
                            val screenY = rawCenterY * scale

                            isFaceInsideOval(
                                Offset(screenX, screenY),
                                face.boundingBox.width() * scale
                            )
                        }
                        onFacesDetected(facesInsideOval)
                    }
                    .addOnFailureListener {
                        it.printStackTrace()
                    }
                    .addOnCompleteListener {
                        continuation.resume(Unit)
                    }
            }
            // We wait a bit before processing the next frame
            // So we don't overload the phone's resources
            delay(DETECTION_THROTTLE_MS)
        }.invokeOnCompletion {
            imageProxy.close()
        }
    }

    private fun isFaceInsideOval(
        faceCenter: Offset,
        faceWidth: Float,
    ): Boolean {
        // Distance check between face center and UI oval center
        val diffX = abs(faceCenter.x - ovalCenter.x)
        val diffY = abs(faceCenter.y - ovalCenter.y)

        // We allow the face center to be within
        // We don't need to be strict
        val xTolerance = ovalRadiusX * POSITION_TOLERANCE
        val yTolerance = ovalRadiusY * POSITION_TOLERANCE

        val isCentered = diffX <= xTolerance && diffY <= yTolerance

        // Ensure the face is large enough (user is not too far away)
        val isBigEnough = faceWidth > (ovalRadiusX * MIN_FACE_SIZE_RATIO)

        return isCentered && isBigEnough
    }
}

fun startFaceDetection(
    context: Context,
    density: Density,
    cameraController: LifecycleCameraController,
    lifecycleOwner: LifecycleOwner,
    ovalCenter: Offset,
    ovalWidthDp: Dp,
    ovalHeightDp: Dp,
    onFacesDetected: (List<Face>) -> Unit
) {
    val metrics = context.resources.displayMetrics
    val screenWidth = metrics.widthPixels.toFloat()
    val screenHeight = metrics.heightPixels.toFloat()

    // Convert to pixels for our detector
    val ovalWidthPx = with(density) { ovalWidthDp.toPx() }
    val ovalHeightPx = with(density) { ovalHeightDp.toPx() }

    val faceDetector = FaceDetector(
        ovalCenter = ovalCenter,
        ovalRadiusX = ovalWidthPx / 2f,
        ovalRadiusY = ovalHeightPx / 2f,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        onFacesDetected = onFacesDetected
    )

    cameraController.setImageAnalysisAnalyzer(
        ContextCompat.getMainExecutor(context),
        faceDetector
    )
    cameraController.bindToLifecycle(lifecycleOwner)
}