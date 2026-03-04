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
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import androidx.core.graphics.scale

class FaceAnalyzer(
    private val ovalCenter: Offset,
    private val ovalRadiusX: Float,
    private val ovalRadiusY: Float,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val onDetectionFeedback: (Detection) -> Unit,
    private val onImagesCaptured: (List<Bitmap>) -> Unit,
    private val faceAttributeClassifier: FaceAttributeClassifier
) : ImageAnalysis.Analyzer, AutoCloseable {

    companion object {
        private const val POSITION_TOLERANCE = 0.5f // How much we tolerate the user to be away from oval's center
        private const val MIN_FACE_SIZE_RATIO = 0.8f // The minimum face we need
        private const val MAX_ROTATION_DEGREES = 12f // How much we allow the face to be rotated
        private const val STABILITY_DURATION_MS = 600L // How many ms the face has to stay stable
        private const val TARGET_SAMPLES = 4 // How many bitmaps to capture
        private const val SAMPLE_INTERVAL_MS = 100L // How many seconds we pause before taking another sample
        private const val BLUR_VARIANCE_THRESHOLD = 220.0 // Threshold to determine if an image is blurry or not
        private const val MIN_EYE_DISTANCE_PX = 25f // Minimum distance between 2 eyes
        private const val MAX_CENTER_SPEED_PX_PER_SECOND = 2000f // The max speed the face should be moving
        private const val MIN_EYE_DISTANCE_FOR_ALIGNMENT = 10f // The minimum distance where we perform face alignment

        private const val SAME_FEEDBACK_COOLDOWN_MS = 400L // Cooldown for repeated identical feedback
        private const val FEEDBACK_SWITCH_COOLDOWN_MS = 400L // Minimum spacing between different feedback states

        private const val EYE_OPEN_THRESHOLD = 0.70f

        private const val ATTRIBUTE_CHECK_INTERVAL_MS = 500L

        // For our facenet Model
        // They are used to create the bitmap
        private const val FACE_SIZE = 112

        // Fixed values where the eyes should be
        // They differ for each model (facenet, arc) not on screen quality
        // I found those from the docs
        private const val TARGET_LEFT_EYE_X = 38f
        private const val TARGET_LEFT_EYE_Y = 40f
        private const val TARGET_RIGHT_EYE_X = 74f
        private const val TARGET_RIGHT_EYE_Y = 40f
    }

    // Get the FaceDetector from ML KIT
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .enableTracking()
            .build()
    )

    // Mutable state for the active capture session (tracking, liveness, captured images)
    private val session = FaceCaptureSessionState()

    private var lastAttributeCheckTimeMs = 0L
    private var lastAttributeResult = FaceAttributeResult(hasGlasses = false, hasHat = false)

    // Emits UI feedback with cooldowns so messages do not flicker frame-by-frame
    private val detectionFeedbackEmitter = DetectionFeedbackEmitter(
        sameFeedbackCooldownMs = SAME_FEEDBACK_COOLDOWN_MS,
        feedbackSwitchCooldownMs = FEEDBACK_SWITCH_COOLDOWN_MS
    )

    // Face geometry checks (oval center, Euler angle, movement speed, eye landmarks)
    private val faceValidation = FaceValidation(
        positionTolerance = POSITION_TOLERANCE,
        minFaceSizeRatio = MIN_FACE_SIZE_RATIO,
        maxRotationDegrees = MAX_ROTATION_DEGREES,
        minEyeDistancePx = MIN_EYE_DISTANCE_PX,
        maxCenterSpeedPxPerSecond = MAX_CENTER_SPEED_PX_PER_SECOND
    )

    // Frame -> aligned face crop -> blur check -> sample buffering
    private val sampleCollector = FaceSampleCollector(
        sampleIntervalMs = SAMPLE_INTERVAL_MS,
        blurVarianceThreshold = BLUR_VARIANCE_THRESHOLD,
        targetSamples = TARGET_SAMPLES,
        faceSize = FACE_SIZE,
        minEyeDistanceForAlignment = MIN_EYE_DISTANCE_FOR_ALIGNMENT,
        targetLeftEyeX = TARGET_LEFT_EYE_X,
        targetLeftEyeY = TARGET_LEFT_EYE_Y,
        targetRightEyeX = TARGET_RIGHT_EYE_X,
        targetRightEyeY = TARGET_RIGHT_EYE_Y
    )

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
        // But docs said its useful
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
        val now = SystemClock.elapsedRealtime()

        val singleFace = extractSingleFaceOrEmitFeedback(faces, now) ?: return

        if (!isFacePositionAndPoseValid(singleFace, mapping, now)) {
            // Single face exists, but it failed quality/position checks.
            // The specific feedback was already emitted.
            session.resetCaptureState()
            return
        }

        handleFaceCapture(singleFace, imageProxy, rotationDegrees)
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
        val screenPosition = calculateScreenPosition(face, mapping)

        val isFaceCentered = faceValidation.isFaceInsideOval(
            faceCenter = screenPosition,
            faceWidth = face.boundingBox.width() * mapping.scale,
            ovalCenter = ovalCenter,
            ovalRadiusX = ovalRadiusX,
            ovalRadiusY = ovalRadiusY
        )
        if (!isFaceCentered) {
            emitDetection(Detection.CenterFace, now)
            return false
        }

        val isFaceStraight = faceValidation.isFaceOrientationCorrect(face)
        if (!isFaceStraight) {
            emitDetection(Detection.LookStraight, now)
            return false
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

    private fun handleFaceCapture(face: Face, imageProxy: ImageProxy, rotationDegrees: Int) {
        // Requirements before starting capturing images
        // As ImageProxy to Bitmap is a heavy process
        // We do not want to capture every frame, convert it to bitmap and then validate
        // Also it ensures our facenet model will perform better
        val currentTime = SystemClock.elapsedRealtime()

        if (!isTrackingIdValid(face.trackingId)) return
        if (!hasReachedStability(currentTime)) return

        if (!faceValidation.isFaceMovementStable(face, currentTime, session)) {
            emitDetection(Detection.HoldStill, currentTime)
            session.stabilityStartTimeMs = null
            return
        }

        if (!faceValidation.areEyeLandmarksDetectable(face)) {
            emitDetection(Detection.MoveCloser, currentTime)
            return
        }

        val leftEyeOpen = face.leftEyeOpenProbability
        val rightEyeOpen = face.rightEyeOpenProbability
        if (leftEyeOpen == null || rightEyeOpen == null ||
            leftEyeOpen < EYE_OPEN_THRESHOLD || rightEyeOpen < EYE_OPEN_THRESHOLD) {
            emitDetection(Detection.EyesNotOpen, currentTime)
            return
        }

        if (currentTime - lastAttributeCheckTimeMs >= ATTRIBUTE_CHECK_INTERVAL_MS) {
            lastAttributeCheckTimeMs = currentTime
            val faceCrop = extractFaceCropForClassifier(imageProxy, face, rotationDegrees)
            if (faceCrop != null) {
                lastAttributeResult = faceAttributeClassifier.classify(faceCrop)
                faceCrop.recycle()
            }
        }

        if (lastAttributeResult.hasGlasses) {
            emitDetection(Detection.WearingGlasses, currentTime)
            return
        }
        if (lastAttributeResult.hasHat) {
            emitDetection(Detection.WearingHat, currentTime)
            return
        }

        emitDetection(Detection.FaceDetected, currentTime)

        // We pause a bit after a successful capture
        // We do not immediately start capturing 4 pictures
        if (!sampleCollector.shouldCaptureSample(currentTime, session)) return

        when (
            val result = sampleCollector.captureSample(
                imageProxy = imageProxy,
                face = face,
                rotationDegrees = rotationDegrees,
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
                // No-op
            }
        }
    }

    private fun isTrackingIdValid(trackingId: Int?): Boolean {
        // Tracking id is provided by ML KIT to track if the current face is the same
        // An impossible scenario would be for a face to quickly get out of the screen and another face comes fast enough so our filter check does not catch it
        // We want to be extra careful
        // Because we save 4 bitmaps for a user, it would be a mistake saving 2 bitmaps for the first user and 2 for the other
        // Later at our facenet calculations the embeddings will be wrong. Also the average embedding would not match any user or may collapse with another one

        // Sometimes the Face Detector model does not give tracking Id
        // Also the function expects an Int? so we have to check
        if (trackingId == null) return true

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

    private fun extractFaceCropForClassifier(imageProxy: ImageProxy, face: Face, rotationDegrees: Int): Bitmap? {
        val src = imageProxy.toBitmap()
        val upright = if (rotationDegrees % 360 == 0) {
            src
        } else {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val srcBounds = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
            val dstBounds = RectF(srcBounds)
            matrix.mapRect(dstBounds)
            matrix.postTranslate(-dstBounds.left, -dstBounds.top)
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        }

        val box = face.boundingBox
        val left = box.left.coerceAtLeast(0)
        val top = box.top.coerceAtLeast(0)
        val right = box.right.coerceAtMost(upright.width)
        val bottom = box.bottom.coerceAtMost(upright.height)
        val cropWidth = right - left
        val cropHeight = bottom - top

        if (cropWidth <= 0 || cropHeight <= 0) {
            if (upright !== src) upright.recycle()
            src.recycle()
            return null
        }

        val cropped = Bitmap.createBitmap(upright, left, top, cropWidth, cropHeight)
        if (upright !== src) upright.recycle()
        src.recycle()

        val inputSize = FaceAttributeClassifier.MODEL_INPUT_SIZE
        val scaled = cropped.scale(inputSize, inputSize)
        if (scaled !== cropped) cropped.recycle()

        return scaled
    }

    private fun emitDetection(feedback: Detection, nowMs: Long) {
        detectionFeedbackEmitter.emit(feedback, nowMs, onDetectionFeedback)
    }

    private data class CoordinateMapping(
        val scale: Float,
        val translationX: Float,
        val translationY: Float
    )

    override fun close() {
        faceDetector.close()
    }

    private class DetectionFeedbackEmitter(
        private val sameFeedbackCooldownMs: Long,
        private val feedbackSwitchCooldownMs: Long
    ) {
        private var lastFeedback: Detection? = null
        private var lastFeedbackTimeMs: Long = 0L

        fun emit(feedback: Detection, nowMs: Long, emitter: (Detection) -> Unit) {
            val previousFeedback = lastFeedback
            if (previousFeedback != null) {
                val elapsedMs = nowMs - lastFeedbackTimeMs
                if (feedback == previousFeedback && elapsedMs < sameFeedbackCooldownMs) return
                if (feedback != previousFeedback && elapsedMs < feedbackSwitchCooldownMs) return
            }

            lastFeedback = feedback
            lastFeedbackTimeMs = nowMs
            emitter(feedback)
        }
    }
}