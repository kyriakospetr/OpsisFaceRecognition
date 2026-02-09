package com.example.opsisfacerecognition.core.biometrics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import com.example.opsisfacerecognition.core.states.FaceUiState.Detection
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class FaceAnalyzer(
    private val ovalCenter: Offset,
    private val ovalRadiusX: Float,
    private val ovalRadiusY: Float,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val onDetectionFeedback: (Detection) -> Unit,
    private val onImagesCaptured: (List<Bitmap>) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val POSITION_TOLERANCE = 0.5f // How much we tolerate the user to be away from oval's center
        private const val MIN_FACE_SIZE_RATIO = 0.8f // The minimum face we need
        private const val MAX_ROTATION_DEGREES = 12f // How much we allow the face to be rotated
        private const val STABILITY_DURATION_MS = 600L // How much ms the face has to stay stable
        private const val TARGET_SAMPLES = 4 // How much bitmaps to capture
        private const val SAMPLE_INTERVAL_MS = 100L // How much seconds we pause before taking another sample
        private const val BLUR_VARIANCE_THRESHOLD = 220.0 // Threshold to determine if an image is blurry or not
        private const val MIN_EYE_DISTANCE_PX = 25f // Minimum distance between 2 eyes
        private const val MAX_CENTER_SPEED_PX_PER_SECOND = 2000f  // The max speed the face should be moving
        private const val MIN_EYE_DISTANCE_FOR_ALIGNMENT = 10f // The minimum distance where we perform face alignment

        private const val SAME_FEEDBACK_COOLDOWN_MS = 400L // Cooldown for repeated identical feedback
        private const val FEEDBACK_SWITCH_COOLDOWN_MS = 140L // Minimum spacing between different feedback states

        // For our facenet Model
        // They are used to create the bitmap
        private const val FACE_SIZE = 112
        private const val FACE_PIXELS = FACE_SIZE * FACE_SIZE

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
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .enableTracking()
            .build()
    )

    private var currentTrackingId: Int? = null // Our current tracking id
    private var stabilityStartTimeMs: Long? = null // When the face was stable enough to start capturing
    private val capturedBitmaps = mutableListOf<Bitmap>() // Our bitmaps
    private var lastSampleTimeMs: Long = 0L // The last time we took a sample
    private var lastFaceCenter: PointF? = null // The last position from the user's face (center)
    private var lastCenterUpdateTimeMs: Long = 0L // The last time we updated when the face was centered
    private var isCaptureComplete = false // To determine if we should end the face detection process
    private var lastFeedback: Detection? = null // Our lastFeedback
    private var lastFeedbackTimeMs: Long = 0L // When was the last time we sent a feedback

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        // If we have finished, close and return
        if (isCaptureComplete) {
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
        // The coordinates doesn't match
        val scaleX = screenWidth / width.toFloat()
        val scaleY = screenHeight / height.toFloat()
        val scale = maxOf(scaleX, scaleY)

        val translationX = (screenWidth - width * scale) / 2f
        val translationY = (screenHeight - height * scale) / 2f

        return CoordinateMapping(scale, translationX, translationY)
    }

    private fun processDetectedFaces(
        faces: List<Face>,
        mapping: CoordinateMapping,
        imageProxy: ImageProxy,
        rotationDegrees: Int
    ) {
        val now = SystemClock.elapsedRealtime()
        if (faces.isEmpty()) {
            // If we detect no face reset
            emitDetection(Detection.NoFace, now)
            resetCaptureState()
            return
        }

        if (faces.size > 1) {
            // If we detect multiple faces
            emitDetection(Detection.MultipleFaces, now)
            resetCaptureState()
            return
        }

        val validFaces = filterValidFaces(faces, mapping)
        if (validFaces.size == 1) {
            handleFaceCapture(validFaces.first(), imageProxy, rotationDegrees)
            return
        }

        // Single face was found, but it failed quality/position checks.
        // The specific feedback has already been emitted inside filterValidFaces.
        resetCaptureState()
    }

    private fun filterValidFaces(
        faces: List<Face>,
        mapping: CoordinateMapping
    ): List<Face> {
        val now = SystemClock.elapsedRealtime()

        return faces.filter { face ->
            val screenPosition = calculateScreenPosition(face, mapping)

            val isFaceCentered = isFaceInsideOval(
                screenPosition,
                face.boundingBox.width() * mapping.scale
            )
            if (!isFaceCentered) {
                emitDetection(Detection.CenterFace, now)
                return@filter false
            }

            val isFaceStraight = isFaceOrientationCorrect(face)
            if (!isFaceStraight) {
                emitDetection(Detection.LookStraight, now)
                return@filter false
            }

            true
        }
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

    private fun isFaceInsideOval(faceCenter: Offset, faceWidth: Float): Boolean {

        // We calculate the distance between the face and the center of the oval
        val horizontalDiff = abs(faceCenter.x - ovalCenter.x)
        val verticalDiff = abs(faceCenter.y - ovalCenter.y)

        // We check if it's near the oval based on our tolerance
        val isWithinPositionTolerance = horizontalDiff <= (ovalRadiusX * POSITION_TOLERANCE) &&
                verticalDiff <= (ovalRadiusY * POSITION_TOLERANCE)

        // We check if the face is near the camera
        val isFaceSizeCorrect = faceWidth > (ovalRadiusX * MIN_FACE_SIZE_RATIO)

        return isWithinPositionTolerance && isFaceSizeCorrect
    }

    private fun isFaceOrientationCorrect(face: Face): Boolean {
        return abs(face.headEulerAngleY) <= MAX_ROTATION_DEGREES && // Don't look sideways (left and right)
                abs(face.headEulerAngleX) <= MAX_ROTATION_DEGREES && // Don't look up or down
                abs(face.headEulerAngleZ) <= MAX_ROTATION_DEGREES // Don't have your face rolled
    }

    private fun handleFaceCapture(face: Face, imageProxy: ImageProxy, rotationDegrees: Int) {
        val currentTime = SystemClock.elapsedRealtime()

        // Requirements before starting capturing images
        // As ImageProxy to Bitmap is a heavy process
        // We don't want to capture every frame convert it to bitmap and then validate
        // Also it ensures our facenet model will perform better
        if (!isTrackingIdValid(face.trackingId)) {
            return
        }

        emitDetection(Detection.FaceDetected, currentTime)

        if (!hasReachedStability(currentTime)) {
            return
        }

        if (!isFaceMovementStable(face, currentTime)) {
            emitDetection(Detection.HoldStill, currentTime)
            stabilityStartTimeMs = null
            return
        }

        if (!areEyeLandmarksDetectable(face)) {
            emitDetection(Detection.MoveCloser, currentTime)
            return
        }

        if (shouldCaptureSample(currentTime)) {
            captureFaceSample(imageProxy, face, rotationDegrees, currentTime)
        }
    }

    private fun isTrackingIdValid(trackingId: Int?): Boolean {
        // Tracking id is provided by ML KIT to track if the current face is the same
        // An impossible scenario would be for a face to quickly get out of the screen and another face comes fast enough so our filterFaces function doesn't catch it
        // We want to be extra careful.
        // Because we save 4 bitmaps for a user, it would be a mistake saving 2 bitmaps for the first user and 2 for the other
        // Later at our facenet calculations the embeddings will be wrong. Also the average embedding would not match any user or may collapse with another one

        // Sometimes the Face Detector model doesn't give tracking Id
        // Also the function expects an Int? so we have to check
        if (trackingId == null) return true


        // Start tracking the person in the camera
        if (currentTrackingId == null) {
            currentTrackingId = trackingId
            stabilityStartTimeMs = null
            lastSampleTimeMs = 0L
            capturedBitmaps.clear()
            return false
        }

        // If the face is lost or a new one is found reset state and update the currentTrackingId
        if (trackingId != currentTrackingId) {
            resetCaptureState()
            currentTrackingId = trackingId
            return false
        }

        return true
    }

    private fun hasReachedStability(currentTime: Long): Boolean {

        // We want the face to be stable in the correct position for a fixed time (ms)
        // So we check if the face has been stable
        // In order to start capturing
        if (stabilityStartTimeMs == null) {
            stabilityStartTimeMs = currentTime
            return false
        }

        return currentTime - stabilityStartTimeMs!! >= STABILITY_DURATION_MS
    }

    private fun isFaceMovementStable(face: Face, currentTime: Long): Boolean {
        // We calculate if the user is moving
        // Even though he may be in the correct position
        // If he moves we will capture blur images or later our facenet model will not get the best embeddings
        // Even though we check for blur it's good to let the user know
        val centerX = face.boundingBox.exactCenterX()
        val centerY = face.boundingBox.exactCenterY()

        // So we check the center of the face how much has it moved
        val previousCenter = lastFaceCenter
        if (previousCenter == null) {
            lastFaceCenter = PointF(centerX, centerY)
            lastCenterUpdateTimeMs = currentTime
            return true
        }

        val timeDelta = (currentTime - lastCenterUpdateTimeMs).coerceAtLeast(1L)
        val deltaX = centerX - previousCenter.x
        val deltaY = centerY - previousCenter.y
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

        // We calculate at which speed the face is moving
        val speed = distance * 1000f / timeDelta.toFloat()

        lastFaceCenter = PointF(centerX, centerY)
        lastCenterUpdateTimeMs = currentTime

        // If it's moving faster return false
        return speed <= MAX_CENTER_SPEED_PX_PER_SECOND
    }

    // We rely on eye landmarks for alignment, so we need to verify they are detectable.
    private fun areEyeLandmarksDetectable(face: Face): Boolean {
        val eyeDistance = calculateEyeDistance(face) ?: return false
        return eyeDistance >= MIN_EYE_DISTANCE_PX
    }

    // We calculate the eye distance
    // It's useful if the face is a bit far
    private fun calculateEyeDistance(face: Face): Float? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null

        val deltaX = rightEye.x - leftEye.x
        val deltaY = rightEye.y - leftEye.y
        return sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    // We pause a bit after a successful capture
    // We don't immediately start capturing 4 pictures
    private fun shouldCaptureSample(currentTime: Long): Boolean {
        return currentTime - lastSampleTimeMs >= SAMPLE_INTERVAL_MS
    }


    private fun captureFaceSample(imageProxy: ImageProxy, face: Face, rotationDegrees: Int, currentTime: Long) {

        // Our bitmap
        val alignedFaceBitmap = extractAndAlignFace(imageProxy, face, rotationDegrees) ?: return

        // Check for blur
        // We don't accept blurry images as our facenet model will extract unstable embeddings
        val blurVariance = calculateBlurVariance(alignedFaceBitmap)
        if (blurVariance < BLUR_VARIANCE_THRESHOLD) {
            emitDetection(Detection.ImproveFocus, currentTime)
            return
        }

        // If it's not blur and all of the above requirements are correct
        // Add it to our bitmap list
        capturedBitmaps.add(alignedFaceBitmap)

        // Update the last time we took a sample with the current time
        lastSampleTimeMs = currentTime

        // If we reached the desired samples stop the process
        if (capturedBitmaps.size >= TARGET_SAMPLES) {
            isCaptureComplete = true
            onImagesCaptured(capturedBitmaps.toList())
        }
    }

    private fun extractAndAlignFace(imageProxy: ImageProxy, face: Face, rotationDegrees: Int): Bitmap? {

        // Convert ImageProxy to Bitmap
        val src = imageProxy.toBitmap()
        val upright = rotateBitmapIfNeeded(src, rotationDegrees)

        val aligned = alignFaceByEyes(upright, face)

        if (upright !== src) upright.recycle()
        src.recycle()
        return aligned
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        // The raw camera frame is first rotated to upright orientation and
        // then geometrically normalized using eye landmarks before embedding extraction
        if (rotationDegrees % 360 == 0) {
            return bitmap
        }

        val rotationMatrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val sourceBounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val destinationBounds = RectF(sourceBounds)

        rotationMatrix.mapRect(destinationBounds)
        rotationMatrix.postTranslate(-destinationBounds.left, -destinationBounds.top)

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotationMatrix, true)
    }

    private fun alignFaceByEyes(bitmap: Bitmap, face: Face): Bitmap? {
        // Align face based on the eyes
        val leftEyeLandmark = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEyeLandmark = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        if (leftEyeLandmark == null || rightEyeLandmark == null) {
            return null
        }

        val (leftX, leftY, rightX, rightY) = if (leftEyeLandmark.x <= rightEyeLandmark.x) {
            arrayOf(leftEyeLandmark.x, leftEyeLandmark.y, rightEyeLandmark.x, rightEyeLandmark.y)
        } else {
            arrayOf(rightEyeLandmark.x, rightEyeLandmark.y, leftEyeLandmark.x, leftEyeLandmark.y)
        }

        val eyeDistance = calculateDistanceBetweenPoints(leftX, leftY, rightX, rightY)
        if (eyeDistance < MIN_EYE_DISTANCE_FOR_ALIGNMENT) {
            return null
        }

        return createAlignedFaceBitmap(bitmap, leftX, leftY, rightX, rightY, eyeDistance)
    }

    private fun calculateDistanceBetweenPoints(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        // Euclidean distance
        val deltaX = x2 - x1
        val deltaY = y2 - y1
        return sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    private fun createAlignedFaceBitmap(bitmap: Bitmap, leftEyeX: Float, leftEyeY: Float, rightEyeX: Float, rightEyeY: Float, eyeDistance: Float): Bitmap {
        // We take the bitmap and we apply a similarity transform
        // So the eyes will go on fixed points in our 112x112

        // It's useful because we will feed our facenet model with the same geometric faces
        // Each face will have it's eyes at specific points
        val targetLeftEye = PointF(TARGET_LEFT_EYE_X, TARGET_LEFT_EYE_Y)
        val targetRightEye = PointF(TARGET_RIGHT_EYE_X, TARGET_RIGHT_EYE_Y)
        val targetEyeDistance = targetRightEye.x - targetLeftEye.x

        val scaleFactor = targetEyeDistance / eyeDistance

        val deltaX = rightEyeX - leftEyeX
        val deltaY = rightEyeY - leftEyeY
        val rotationAngleRadians = atan2(deltaY, deltaX)
        val rotationAngleDegrees = rotationAngleRadians * (180f / Math.PI.toFloat())

        val eyesMidpointX = (leftEyeX + rightEyeX) / 2f
        val eyesMidpointY = (leftEyeY + rightEyeY) / 2f

        val targetMidpointX = (targetLeftEye.x + targetRightEye.x) / 2f
        val targetMidpointY = (targetLeftEye.y + targetRightEye.y) / 2f

        val transformationMatrix = Matrix().apply {
            postTranslate(-eyesMidpointX, -eyesMidpointY)
            postRotate(-rotationAngleDegrees)
            postScale(scaleFactor, scaleFactor)
            postTranslate(targetMidpointX, targetMidpointY)
        }

        val alignedBitmap = createBitmap(FACE_SIZE, FACE_SIZE)
        val canvas = Canvas(alignedBitmap)
        canvas.drawBitmap(bitmap, transformationMatrix, Paint(Paint.FILTER_BITMAP_FLAG))

        return alignedBitmap
    }

    private fun calculateBlurVariance(bitmap112: Bitmap): Double {
        val grayscale = convertToGrayscale(bitmap112)

        // Calculate if the image is blur using Laplacian
        val lap = applyLaplacianOperator(grayscale)
        return calculateVariance(lap)
    }

    private fun convertToGrayscale(bitmap: Bitmap): DoubleArray {
        // Convert the bitmap to grayscale (Black-White)
        // Because Laplacian works better with grayscale images
        val pixels = IntArray(FACE_PIXELS)
        bitmap.getPixels(pixels, 0, FACE_SIZE, 0, 0, FACE_SIZE, FACE_SIZE)

        return DoubleArray(FACE_PIXELS) { index ->
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            0.299 * red + 0.587 * green + 0.114 * blue
        }
    }


    private fun applyLaplacianOperator(grayscalePixels: DoubleArray): DoubleArray {
        val laplacianValues = DoubleArray(FACE_PIXELS)

        for (y in 1 until FACE_SIZE - 1) {
            for (x in 1 until FACE_SIZE - 1) {
                val centerIndex = y * FACE_SIZE + x
                val centerValue = grayscalePixels[centerIndex]

                val topValue = grayscalePixels[(y - 1) * FACE_SIZE + x]
                val leftValue = grayscalePixels[y * FACE_SIZE + (x - 1)]
                val rightValue = grayscalePixels[y * FACE_SIZE + (x + 1)]
                val bottomValue = grayscalePixels[(y + 1) * FACE_SIZE + x]

                laplacianValues[centerIndex] = topValue + leftValue - 4.0 * centerValue + rightValue + bottomValue
            }
        }

        return laplacianValues
    }

    private fun calculateVariance(laplacianValues: DoubleArray): Double {
        var sum = 0.0
        var count = 0

        for (y in 1 until FACE_SIZE - 1) {
            for (x in 1 until FACE_SIZE - 1) {
                sum += laplacianValues[y * FACE_SIZE + x]
                count++
            }
        }

        val mean = sum / count.toDouble()

        var varianceSum = 0.0
        for (y in 1 until FACE_SIZE - 1) {
            for (x in 1 until FACE_SIZE - 1) {
                val difference = laplacianValues[y * FACE_SIZE + x] - mean
                varianceSum += difference * difference
            }
        }

        return varianceSum / count.toDouble()
    }

    // Reset State helper
    private fun resetCaptureState() {
        currentTrackingId = null
        stabilityStartTimeMs = null
        lastSampleTimeMs = 0L
        capturedBitmaps.clear()
        lastFaceCenter = null
        lastCenterUpdateTimeMs = 0L
    }

    private fun emitDetection(feedback: Detection, nowMs: Long) {
        val previousFeedback = lastFeedback
        if (previousFeedback != null) {
            val elapsedMs = nowMs - lastFeedbackTimeMs
            if (feedback == previousFeedback && elapsedMs < SAME_FEEDBACK_COOLDOWN_MS) return
            if (feedback != previousFeedback && elapsedMs < FEEDBACK_SWITCH_COOLDOWN_MS) return
        }

        lastFeedback = feedback
        lastFeedbackTimeMs = nowMs
        onDetectionFeedback(feedback)
    }

    // Data class for our mapping
    private data class CoordinateMapping(
        val scale: Float,
        val translationX: Float,
        val translationY: Float
    )
}
