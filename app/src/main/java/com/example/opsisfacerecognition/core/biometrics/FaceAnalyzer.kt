package com.example.opsisfacerecognition.core.biometrics

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.example.opsisfacerecognition.core.states.FaceUiState.Detection
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

// Assisted Injection for Hilt
class FaceAnalyzer @AssistedInject constructor(
    @Assisted("ovalCenterX") private val ovalCenterX: Float,
    @Assisted("ovalCenterY") private val ovalCenterY: Float,
    @Assisted("ovalRadiusX") private val ovalRadiusX: Float,
    @Assisted("ovalRadiusY") private val ovalRadiusY: Float,
    @Assisted("screenWidth") private val screenWidth: Float,
    @Assisted("screenHeight") private val screenHeight: Float,
    @Assisted private val onDetectionFeedback: (Detection) -> Unit,
    @Assisted private val onImagesCaptured: (List<Bitmap>) -> Unit,

    // These will be initialized by Hilt
    private val faceDetector: FaceDetector,
    private val faceValidation: FaceValidation,
    private val faceAttributeClassifier: FaceAttributeClassifier,
    private val faceSampleCollector: FaceSampleCollector,
    private val livenessDetector: LivenessDetector,
    private val detectionFeedbackEmitter: DetectionFeedbackEmitter,
) : ImageAnalysis.Analyzer {

    companion object {
        private const val STABILITY_DURATION_MS = 600L
        private const val EYE_OPEN_THRESHOLD = 0.40f
        private const val ATTRIBUTE_CHECK_INTERVAL_MS = 300L
        private const val CONSECUTIVE_FAILURES_REQUIRED = 2
    }
    private val session = FaceCaptureSession()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        // If we have finished, close and return
        if (session.isCaptureComplete) {
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

        // Get the mapping
        val coordinateMapping = calculateCoordinateMapping(rotationDegrees, imageProxy.width, imageProxy.height)

        faceDetector.process(inputImage)
            .addOnSuccessListener { detectedFaces ->
                processDetectedFaces(detectedFaces, coordinateMapping, imageProxy, rotationDegrees)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun calculateCoordinateMapping(rotation: Int, imageWidth: Int, imageHeight: Int): CoordinateMapping {
        // Swap dimensions if rotated
        // It's an extra layer of protection since the application will not be horizontal rotated
        val (width, height) = if (rotation == 90 || rotation == 270) {
            imageHeight to imageWidth
        } else {
            imageWidth to imageHeight
        }

        // We need to map because
        // User may see his face inside the oval in the preview
        // But that does not mean the face is on the same position on the camera
        // The coordinates do not match
        val scaleX = screenWidth / width.toFloat()
        val scaleY = screenHeight / height.toFloat()
        val scale = maxOf(scaleX, scaleY)

        val translationX = (screenWidth - width * scale) / 2f
        val translationY = (screenHeight - height * scale) / 2f

        return CoordinateMapping(scale, translationX, translationY)
    }

    private fun processDetectedFaces(faces: List<Face>, mapping: CoordinateMapping, imageProxy: ImageProxy, rotationDegrees: Int) {
        val currentTime: Long = SystemClock.elapsedRealtime()

        val singleFace = extractSingleFaceOrEmitFeedback(faces, currentTime) ?: return
        if (!isFacePositionAndPoseValid(singleFace, mapping, currentTime)) {
            // Single face exists, but it failed quality/position checks.
            // The specific feedback was already emitted.
            // Keep tracking ID so we don't restart from scratch on the next frame.
            session.resetCaptureState()
            return
        }

        if (!isTrackingIdValid(singleFace.trackingId)) return

        val leftEyeOpen = singleFace.leftEyeOpenProbability
        val rightEyeOpen = singleFace.rightEyeOpenProbability

        if (leftEyeOpen == null || rightEyeOpen == null ||
            leftEyeOpen < EYE_OPEN_THRESHOLD || rightEyeOpen < EYE_OPEN_THRESHOLD
        ) {
            emitDetection(Detection.EyesNotOpen, currentTime)
            return
        }

        val needsAttributeCheck: Boolean = currentTime - session.lastAttributeCheckTimeMs >= ATTRIBUTE_CHECK_INTERVAL_MS
        val needsSample: Boolean = hasReachedStability(currentTime) && faceSampleCollector.shouldCaptureSample(currentTime, session)

        // attribute/liveness checks run during stability AND capture

        // 1. We extract the bitmap first
        val upright = if (needsAttributeCheck || needsSample) extractUprightBitmap(
            imageProxy,
            rotationDegrees
        ) else null

        try {
            // Check if we need attribute check for hat/glasses and liveness
            if (needsAttributeCheck && upright != null) {
                session.lastAttributeCheckTimeMs = currentTime

                // Crop and scale for attribute classifier
                val attrCrop = faceAttributeClassifier.cropAndScale(upright, singleFace.boundingBox)
                try {
                    if (attrCrop != null) {
                        session.lastAttributeResult = faceAttributeClassifier.classify(attrCrop)
                    }
                } finally {
                    attrCrop?.recycle()
                }

                session.lastLivenessResult = livenessDetector.check(upright, singleFace.boundingBox)

                // Update consecutive failure counters
                if (session.lastAttributeResult.hasGlasses) session.consecutiveGlassesFailures++ else session.consecutiveGlassesFailures = 0
                if (session.lastAttributeResult.hasHat) session.consecutiveHatFailures++ else session.consecutiveHatFailures = 0
                if (!session.lastLivenessResult.isLive) session.consecutiveLivenessFailures++ else session.consecutiveLivenessFailures = 0
            }

            // Look how clean these returns are now! No manual recycling needed.
            if (session.consecutiveGlassesFailures >= CONSECUTIVE_FAILURES_REQUIRED) {
                emitDetection(Detection.WearingGlasses, currentTime)
                return
            }
            if (session.consecutiveHatFailures >= CONSECUTIVE_FAILURES_REQUIRED) {
                emitDetection(Detection.WearingHat, currentTime)
                return
            }
            if (session.consecutiveLivenessFailures >= CONSECUTIVE_FAILURES_REQUIRED) {
                emitDetection(Detection.SpoofDetected, currentTime)
                return
            }

            emitDetection(Detection.FaceDetected, currentTime)

            if (!needsSample) {
                return
            }

            when (
                val result = faceSampleCollector.captureSample(
                    uprightBitmap = upright,
                    face = singleFace,
                    currentTime = currentTime,
                    session = session
                )
            ) {
                FaceSampleCollector.CaptureResult.Blurry -> {
                    session.lastSampleTimeMs = currentTime
                    emitDetection(Detection.ImproveFocus, currentTime)
                }

                is FaceSampleCollector.CaptureResult.Completed -> {
                    onImagesCaptured(result.bitmaps)
                }

                FaceSampleCollector.CaptureResult.Added,
                FaceSampleCollector.CaptureResult.Skipped -> {
                }
            }

        } finally {
            // 3. THE SAFETY NET
            // Kotlin guarantees this block will run right before the function exits,
            // no matter which 'return' statement was triggered above.
            upright?.recycle()
        }

    }

    private fun extractSingleFaceOrEmitFeedback(faces: List<Face>, now: Long): Face? {
        if (faces.isEmpty()) {
            // If we detect no face reset
            emitDetection(Detection.NoFace, now)
            session.resetCaptureState()
            return null
        }

        if (faces.size > 1) {
            // If we detect multiple faces
            emitDetection(Detection.MultipleFaces, now)
            session.resetCaptureState()
            return null
        }

        return faces.first()
    }

    private fun isFacePositionAndPoseValid(face: Face, mapping: CoordinateMapping, now: Long): Boolean {
        // We check if the person's face is inside the oval and doesn't look sideways etc.
        val screenPosition = calculateScreenPosition(face, mapping)

        val ovalCheck = faceValidation.checkFaceInsideOval(
            faceCenter = screenPosition,
            faceWidth = face.boundingBox.width() * mapping.scale,
            ovalCenter = Offset(ovalCenterX, ovalCenterY),
            ovalRadiusX = ovalRadiusX,
            ovalRadiusY = ovalRadiusY
        )
        when (ovalCheck) {
            FaceValidation.OvalCheckResult.NOT_CENTERED -> { emitDetection(Detection.CenterFace, now); return false }
            FaceValidation.OvalCheckResult.TOO_FAR -> { emitDetection(Detection.TooFar, now); return false }
            FaceValidation.OvalCheckResult.TOO_CLOSE -> { emitDetection(Detection.TooClose, now); return false }
            FaceValidation.OvalCheckResult.OK -> {  }
        }

        val orientationCheck = faceValidation.checkFaceOrientation(face)
        when (orientationCheck) {
            FaceValidation.OrientationCheckResult.LOOK_STRAIGHT -> { emitDetection(Detection.LookStraight, now); return false }
            FaceValidation.OrientationCheckResult.LOOK_STRAIGHT_AHEAD -> { emitDetection(Detection.LookStraightAhead, now); return false }
            FaceValidation.OrientationCheckResult.DONT_TILT -> { emitDetection(Detection.DontTiltHead, now); return false }
            FaceValidation.OrientationCheckResult.OK -> {  }
        }

        return true
    }

    private fun calculateScreenPosition(face: Face, mapping: CoordinateMapping): Offset {
        // Face Detector gives us the center of the bbox (Image Pixels)
        val imageX = face.boundingBox.centerX().toFloat()
        val imageY = face.boundingBox.centerY().toFloat()

        // We convert to Screen Pixels
        val mappedX = imageX * mapping.scale + mapping.translationX
        val mappedY = imageY * mapping.scale + mapping.translationY

        // Front camera acts like a mirror
        val screenX = screenWidth - mappedX

        return Offset(screenX, mappedY)
    }

    private fun isTrackingIdValid(trackingId: Int?): Boolean {
        // Tracking id is provided by ML KIT to track if the current face is the same
        // An impossible scenario would be for a face to quickly get out of the screen and another face comes fast enough so our filter check does not catch it
        // We want to be extra careful
        // Because we save 3 bitmaps for a user, it would be a mistake saving 2 bitmaps for the first user and 1 for the other
        // Later at our facenet calculations the embeddings will be wrong, Also the average embedding would not match any user or may collapse with another one

        // Sometimes the Face Detector model does not give tracking id
        // Also the function expects an Int? so we have to check
        if (trackingId == null) return false

        // Start tracking the person in the camera
        if (session.currentTrackingId == null) {
            session.currentTrackingId = trackingId
            session.stabilityStartTimeMs = null
            session.lastSampleTimeMs = 0L
            session.capturedBitmaps.clear()
            return false
        }

        // If the face is lost or a new one is found reset state and update the currentTrackingId
        if (trackingId != session.currentTrackingId) {
            session.resetCaptureState()
            session.currentTrackingId = trackingId
            return false
        }

        return true
    }

    private fun hasReachedStability(currentTime: Long): Boolean {
        // We want the face to be stable in the correct position for a fixed time (ms)
        // So we check if the face has been stable
        // In order to start capturing
        if (session.stabilityStartTimeMs == null) {
            session.stabilityStartTimeMs = currentTime
            return false
        }

        return currentTime - session.stabilityStartTimeMs!! >= STABILITY_DURATION_MS
    }

    private fun extractUprightBitmap(imageProxy: ImageProxy, rotationDegrees: Int): Bitmap {
        // Rotates the raw camera frame to upright orientation.
        // CameraX delivers frames rotated (e.g. 90°) — this corrects the orientation
        // so the face appears straight. If no rotation is needed (0°), returns the original bitmap.
        val src = imageProxy.toBitmap()
        if (rotationDegrees % 360 == 0) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, false).also { src.recycle() }
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val srcBounds = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        val dstBounds = RectF(srcBounds)
        matrix.mapRect(dstBounds)
        matrix.postTranslate(-dstBounds.left, -dstBounds.top)
        val upright = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        src.recycle()
        return upright
    }

    private fun emitDetection(feedback: Detection, nowMs: Long) {
        detectionFeedbackEmitter.emit(feedback, nowMs, onDetectionFeedback)
    }


    private data class CoordinateMapping(
        val scale: Float,
        val translationX: Float,
        val translationY: Float
    )
}

@AssistedFactory
interface FaceAnalyzerFactory {
    fun create(
        @Assisted("ovalCenterX") ovalCenterX: Float,
        @Assisted("ovalCenterY") ovalCenterY: Float,
        @Assisted("ovalRadiusX") ovalRadiusX: Float,
        @Assisted("ovalRadiusY") ovalRadiusY: Float,
        @Assisted("screenWidth") screenWidth: Float,
        @Assisted("screenHeight") screenHeight: Float,
        onDetectionFeedback: (Detection) -> Unit,
        onImagesCaptured: (List<Bitmap>) -> Unit,
    ): FaceAnalyzer
}
