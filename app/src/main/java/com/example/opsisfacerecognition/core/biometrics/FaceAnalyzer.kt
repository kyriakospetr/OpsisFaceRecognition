package com.example.opsisfacerecognition.core.biometrics

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
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
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

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
    private val faceQualityAnalyzer: LightingDetector,
    private val detectionFeedbackEmitter: DetectionFeedbackEmitter,
) : ImageAnalysis.Analyzer, AutoCloseable {

    companion object {
        private const val STABILITY_DURATION_MS = 600L
        private const val ATTRIBUTE_CHECK_INTERVAL_MS = 500L
        private const val LIVENESS_CHECK_INTERVAL_MS = 700L
        private const val QUALITY_CHECK_INTERVAL_MS = 300L

        private const val TAG_CAPTURE_GATE = "FaceCaptureGate"
    }
    private val session = FaceCaptureSession()

    // Warms up the heavy models
    // So the camera on first time doesn't take too long to initialize the models
    // And the models are not loaded on the first frame
    private val worker = FaceAnalyzerWorker()

    init {
        worker.execute {
            runCatching {
                faceAttributeClassifier.warmUp()
                livenessDetector.warmUp()
            }.onFailure {
                Log.d(TAG_CAPTURE_GATE, "Scanner model warm-up failed: ${it.message}")
            }
        }
    }

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
            .addOnSuccessListener(worker.executor) { detectedFaces ->
                processDetectedFaces(detectedFaces, coordinateMapping, imageProxy, rotationDegrees)
            }
            .addOnCompleteListener(worker.executor) {
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

        // Booleans to determine if checks need to be run.
        val needsQualityCheck: Boolean = currentTime - session.lastQualityCheckTimeMs >= QUALITY_CHECK_INTERVAL_MS
        val needsAttributeCheck: Boolean = currentTime - session.lastAttributeCheckTimeMs >= ATTRIBUTE_CHECK_INTERVAL_MS
        val needsLivenessCheck: Boolean = currentTime - session.lastLivenessCheckTimeMs >= LIVENESS_CHECK_INTERVAL_MS
        val needsSample: Boolean = hasReachedStability(currentTime) && faceSampleCollector.shouldCaptureSample(currentTime, session)

        // We extract the bitmap first
        val upright = if (needsQualityCheck || needsAttributeCheck || needsLivenessCheck || needsSample) extractUprightBitmap(
            imageProxy,
            rotationDegrees
        ) else null

        try {
            // Check if bitmap is null
            if (upright == null) {
                return
            }

            // Check lighting quality
            if (needsQualityCheck) {
                session.lastQualityCheckTimeMs = currentTime
                val qualityIssue = faceQualityAnalyzer.analyze(upright).issue
                session.updateQualityState(qualityIssue)
                if (qualityIssue != FaceQualityIssue.NONE) {
                    emitDetection(session.getQualityFeedback() ?: Detection.FaceDetected, currentTime)
                    return
                }
            }
            // Check if we need attribute check for hat/glasses
            if (needsAttributeCheck) {
                session.lastAttributeCheckTimeMs = currentTime

                // Crop and scale for attribute classifier
                val attrCrop = faceAttributeClassifier.cropAndScale(upright, singleFace.boundingBox)
                try {
                    if (attrCrop != null) {
                        session.updateAttributeState(faceAttributeClassifier.classify(attrCrop))
                    }
                } finally {
                    attrCrop?.recycle()
                }
            }

            // If the lighting quality is not ideal don't run other checks.
            // Because liveness and attribute results may be wrong.
            val qualityFeedback = session.getQualityFeedback()
            if (qualityFeedback != null) {
                emitDetection(qualityFeedback, currentTime)
                return
            }

            // Check if we need liveness check
            if (needsLivenessCheck) {
                session.lastLivenessCheckTimeMs = currentTime
                session.updateLivenessState(livenessDetector.check(upright, singleFace.boundingBox))
            }

            // Emit feedbacks
            val attributeFeedback = session.getAttributeFeedback()
            if (attributeFeedback != null) {
                emitDetection(attributeFeedback, currentTime)
                return
            }

            val livenessFeedback = session.getLivenessFeedback()
            if (livenessFeedback != null) {
                emitDetection(livenessFeedback, currentTime)
                return
            }


            // If the latest check is not clean return
            if (!session.isLatestCheckClean()) {
                emitDetection(Detection.FaceDetected, currentTime)
                return
            }

            // If we don't need a sample return
            if (!needsSample) {
                emitDetection(Detection.FaceDetected, currentTime)
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
                CaptureResult.Added -> emitDetection(Detection.FaceDetected, currentTime)
                is CaptureResult.Completed -> onImagesCaptured(result.bitmaps)
                is CaptureResult.Skipped -> emitDetection(Detection.FaceDetected, currentTime)
            }
        } finally {
            upright?.recycle()
        }

    }

    private fun extractSingleFaceOrEmitFeedback(faces: List<Face>, now: Long): Face? {
        if (faces.isEmpty()) {
            // If we detect no face reset
            emitDetection(Detection.NoFace, now)
            return null
        }

        if (faces.size > 1) {
            // If we detect multiple faces
            emitDetection(Detection.MultipleFaces, now)
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
        // Because we capture multiple samples, it would be a mistake for example saving 2 bitmaps for the first user and 1 for the other
        // Later at our facenet calculations the embeddings will be wrong,

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
        // CameraX delivers frames rotated (e.g. 90°) this corrects the orientation
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

    override fun close() {
        worker.close()
    }

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

private data class CoordinateMapping(
    val scale: Float,
    val translationX: Float,
    val translationY: Float
)

sealed interface CaptureResult {
    data class Skipped(val reason: String) : CaptureResult
    data object Added : CaptureResult
    data class Completed(val bitmaps: List<Bitmap>) : CaptureResult
}

private class FaceAnalyzerWorker : AutoCloseable {
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    val executor: Executor = Executor { command ->
        if (executorService.isShutdown) {
            command.run()
        } else {
            try {
                executorService.execute(command)
            } catch (_: RejectedExecutionException) {
                command.run()
            }
        }
    }

    fun execute(block: () -> Unit) {
        executor.execute(block)
    }

    override fun close() {
        executorService.shutdown()
    }
}
