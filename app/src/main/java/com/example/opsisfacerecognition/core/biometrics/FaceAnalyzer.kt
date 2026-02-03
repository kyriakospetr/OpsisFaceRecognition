package com.example.opsisfacerecognition.core.biometrics

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import kotlin.math.abs

class FaceAnalyzer(
    private val ovalCenter: Offset,
    private val ovalRadiusX: Float,
    private val ovalRadiusY: Float,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val onFacesDetected: (List<Face>) -> Unit,
    private val onEnrollmentImagesCaptured: (List<Bitmap>) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val POSITION_TOLERANCE = 0.5f
        private const val MIN_FACE_SIZE_RATIO = 0.8f
        private const val MAX_ROTATION_DEG = 12f
        private const val STABLE_DURATION_MS = 600L // How many seconds the face must stay inside the oval
        private const val TARGET_SAMPLES = 4 // How many bitmaps we need to enroll a user
        private const val SAMPLE_INTERVAL_MS = 100L // Distance between samples
    }

    // Get the FaceDetector from ML KIT
    private val faceDetector = FaceDetection.getClient()

    // State Tracking
    private var currentTrackingId: Int? = null // Id of the user we are tracking
    private var stableSinceMs: Long? = null // How long the face has stayed stable

    private val collectedBitmaps = mutableListOf<Bitmap>()
    private var lastSampleTimeMs: Long = 0L
    private var isFinished = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        // If we have finished, close and return
        if (isFinished) {
            imageProxy.close()
            return
        }

        // If the media image is null, close and return
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // CameraX handles the rotation degrees
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Convert to InputImage for the ML KIT
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        // Map for ui
        val mapping = computeMapping(rotationDegrees, imageProxy.width, imageProxy.height)

        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                processFaces(faces, mapping, imageProxy, rotationDegrees)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun processFaces(
        faces: List<Face>,
        mapping: Mapping,
        imageProxy: ImageProxy,
        rotation: Int
    ) {
        // Get only the valid faces from our helper
        val validFaces = filterFaces(faces, mapping)

        // We send the result to the UI so it can update
        onFacesDetected(validFaces)

        // If there is only 1 face we start capturing
        if (validFaces.size == 1) {
            val face = validFaces.first()
            handleEnrollmentLogic(face, imageProxy, rotation)
        } else {
            // If face is lost, or we get 2 faces reset state
            resetState()
        }
    }

    private fun handleEnrollmentLogic(face: Face, imageProxy: ImageProxy, rotation: Int) {
        val now = SystemClock.elapsedRealtime()
        val trackingId = face.trackingId

        // Tracking id is provided by ML KIT to track if the current face is the same
        // An impossible scenario would be for a face to quickly get out of the screen and another face comes fast enough so our filterFaces function doesn't catch it
        // We want to be extra careful.
        // Because we save 4 bitmaps for a user, it would be a mistake saving 2 bitmaps for the first user and 2 for the other
        // Later at our facenet calculations the embeddings will be wrong, they won't almost be identical
        if (trackingId != null && trackingId != currentTrackingId) {
            // Face left or new face, we reset state and return
            resetState()
            currentTrackingId = trackingId
        }

        // Stability timer
        if (stableSinceMs == null) {
            stableSinceMs = now
            return
        }

        // If the stable duration ms does not have passed we return
        if (now - stableSinceMs!! < STABLE_DURATION_MS) return

        // We get samples
        if (now - lastSampleTimeMs >= SAMPLE_INTERVAL_MS) {
            // Convert image to bitmap
            val bitmap = captureFaceBitmap(imageProxy, face, rotation)

            // If bitmap is ok
            if (bitmap != null) {
                collectedBitmaps.add(bitmap)
                lastSampleTimeMs = now

                // If we have as much samples as we need, we have finished
                if (collectedBitmaps.size >= TARGET_SAMPLES) {
                    isFinished = true
                    onEnrollmentImagesCaptured(collectedBitmaps.toList())
                }
            }
        }
    }

    // Helper function to reset state
    private fun resetState() {
        currentTrackingId = null
        stableSinceMs = null
        collectedBitmaps.clear()
    }

    private fun filterFaces(faces: List<Face>, mapping: Mapping): List<Face> {
        val (scale, dx, dy) = mapping

        return faces.filter { face ->
            // Map Coordinates from image to screen
            val rawX = face.boundingBox.centerX().toFloat()
            val rawY = face.boundingBox.centerY().toFloat()

            // Apply scale and translation
            val mappedX = rawX * scale + dx
            val mappedY = rawY * scale + dy

            // Front end camera acts as a mirror
            // Screen Width - X creates the mirror effect
            val screenX = screenWidth - mappedX

            // Get faces only inside the oval and with the correct distance
            val isCentered = isFaceInsideOval(
                faceCenter = Offset(screenX, mappedY),
                faceWidth = face.boundingBox.width() * scale
            )

            // We want only straight face, not looking from angles
            // As we want block future false embeddings
            val isStraight = abs(face.headEulerAngleY) <= MAX_ROTATION_DEG &&
                    abs(face.headEulerAngleX) <= MAX_ROTATION_DEG &&
                    abs(face.headEulerAngleZ) <= MAX_ROTATION_DEG

            isCentered && isStraight
        }
    }

    private fun computeMapping(rotation: Int, imgWidth: Int, imgHeight: Int): Mapping {
        // Swap dimensions if rotated
        // It's an extra layer of protection since the application will not be horizontal rotated
        // But docs said its useful
        val (w, h) = if (rotation == 90 || rotation == 270) imgHeight to imgWidth else imgWidth to imgHeight

        val scaleX = screenWidth / w.toFloat()
        val scaleY = screenHeight / h.toFloat()

        // maxOf implies FILL_CENTER (zooms in to fill screen, cropping edges)
        val scale = maxOf(scaleX, scaleY)

        val dx = (screenWidth - w * scale) / 2f
        val dy = (screenHeight - h * scale) / 2f

        return Mapping(scale, dx, dy)
    }

    private fun isFaceInsideOval(faceCenter: Offset, faceWidth: Float): Boolean {
        val diffX = abs(faceCenter.x - ovalCenter.x)
        val diffY = abs(faceCenter.y - ovalCenter.y)

        // We don't need to be strict
        // The face doesn't need to be 100% inside our oval
        val passedPosition = diffX <= (ovalRadiusX * POSITION_TOLERANCE) &&
                diffY <= (ovalRadiusY * POSITION_TOLERANCE)

        val passedSize = faceWidth > (ovalRadiusX * MIN_FACE_SIZE_RATIO)

        return passedPosition && passedSize
    }

    // Placeholder for bitmap extraction
    private fun captureFaceBitmap(proxy: ImageProxy, face: Face, rotation: Int): Bitmap? {
        // Implement YUV to Bitmap conversion
        return null
    }

    // Data class to hold mapping values
    private data class Mapping(val scale: Float, val dx: Float, val dy: Float)
}
