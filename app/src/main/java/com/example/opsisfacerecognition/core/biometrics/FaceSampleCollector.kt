package com.example.opsisfacerecognition.core.biometrics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.sqrt

@ViewModelScoped
class FaceSampleCollector @Inject constructor() {

    companion object {
        private const val TARGET_SAMPLES = 3
        private const val SAMPLE_INTERVAL_MS = 350L
        private const val BLUR_VARIANCE_THRESHOLD = 160.0
        private const val MIN_EYE_DISTANCE_FOR_ALIGNMENT = 10f
        private const val FACE_SIZE = 112
        private const val TARGET_LEFT_EYE_X = 38f
        private const val TARGET_LEFT_EYE_Y = 40f
        private const val TARGET_RIGHT_EYE_X = 74f
        private const val TARGET_RIGHT_EYE_Y = 40f
    }
    private val facePixels = FACE_SIZE * FACE_SIZE

    // We pause a bit after a successful capture.
    // We do not immediately start capturing all samples.
    fun shouldCaptureSample(currentTime: Long, session: FaceCaptureSession): Boolean {
        return currentTime - session.lastSampleTimeMs >= SAMPLE_INTERVAL_MS
    }

    fun captureSample(uprightBitmap: Bitmap?, face: Face, currentTime: Long, session: FaceCaptureSession): CaptureResult {
        // Align the pre-decoded upright bitmap to produce the face crop
        if(uprightBitmap == null) return CaptureResult.Skipped
        val alignedFaceBitmap = alignFaceByEyes(uprightBitmap, face) ?: return CaptureResult.Skipped

        // Check for blur
        // We do not accept blurry images because the facenet model will extract unstable embeddings
        val blurVariance = calculateBlurVariance(alignedFaceBitmap)
        if (blurVariance < BLUR_VARIANCE_THRESHOLD) {
            return CaptureResult.Blurry
        }

        // If it is not blurry and all requirements are correct
        // Add it to our bitmap list
        session.capturedBitmaps.add(alignedFaceBitmap)

        // Update the last time we took a sample with the current time
        session.lastSampleTimeMs = currentTime

        // If we reached the desired samples stop the process
        if (session.capturedBitmaps.size >= TARGET_SAMPLES) {
            session.isCaptureComplete = true
            return CaptureResult.Completed(session.capturedBitmaps.toList())
        }

        return CaptureResult.Added
    }

    private fun alignFaceByEyes(bitmap: Bitmap, face: Face): Bitmap? {
        // Align face based on the eyes
        val leftEyeLandmark = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEyeLandmark = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        if (leftEyeLandmark == null || rightEyeLandmark == null) {
            return null
        }

        val orderedEyes = if (leftEyeLandmark.x <= rightEyeLandmark.x) {
            EyePair(leftEyeLandmark.x, leftEyeLandmark.y, rightEyeLandmark.x, rightEyeLandmark.y)
        } else {
            EyePair(rightEyeLandmark.x, rightEyeLandmark.y, leftEyeLandmark.x, leftEyeLandmark.y)
        }

        val eyeDistance = calculateDistanceBetweenPoints(
            orderedEyes.leftX,
            orderedEyes.leftY,
            orderedEyes.rightX,
            orderedEyes.rightY
        )
        if (eyeDistance < MIN_EYE_DISTANCE_FOR_ALIGNMENT) {
            return null
        }

        return createAlignedFaceBitmap(
            bitmap = bitmap,
            leftEyeX = orderedEyes.leftX,
            leftEyeY = orderedEyes.leftY,
            rightEyeX = orderedEyes.rightX,
            rightEyeY = orderedEyes.rightY,
            eyeDistance = eyeDistance
        )
    }

    private fun calculateDistanceBetweenPoints(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        // Euclidean distance
        val deltaX = x2 - x1
        val deltaY = y2 - y1
        return sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    private fun createAlignedFaceBitmap(bitmap: Bitmap, leftEyeX: Float, leftEyeY: Float, rightEyeX: Float, rightEyeY: Float, eyeDistance: Float): Bitmap {
        // We take the bitmap, and we apply a similarity transform
        // So the eyes will go on fixed points in our 112x112

        // It is useful because we will feed our facenet model with the same geometric faces
        // Each face will have its eyes at specific points
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

    private fun calculateBlurVariance(bitmap: Bitmap): Double {
        val pixels = IntArray(facePixels)
        bitmap.getPixels(pixels, 0, FACE_SIZE, 0, 0, FACE_SIZE, FACE_SIZE)

        // Convert to grayscale
        val gray = DoubleArray(facePixels) { i ->
            val px = pixels[i]
            0.299 * ((px shr 16) and 0xFF) + 0.587 * ((px shr 8) and 0xFF) + 0.114 * (px and 0xFF)
        }

        // Single-pass Laplacian variance: accumulate sum and sum-of-squares together
        // variance = E[x²] - E[x]²  avoids a second full loop over laplacian values
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in 1 until FACE_SIZE - 1) {
            for (x in 1 until FACE_SIZE - 1) {
                val c = y * FACE_SIZE + x
                val lap = gray[(y - 1) * FACE_SIZE + x] + gray[y * FACE_SIZE + (x - 1)] -
                        4.0 * gray[c] +
                        gray[y * FACE_SIZE + (x + 1)] + gray[(y + 1) * FACE_SIZE + x]
                sum += lap
                sumSq += lap * lap
                count++
            }
        }

        val mean = sum / count
        return sumSq / count - mean * mean
    }

    private data class EyePair(
        val leftX: Float,
        val leftY: Float,
        val rightX: Float,
        val rightY: Float
    )

    sealed interface CaptureResult {
        data object Skipped : CaptureResult
        data object Added : CaptureResult
        data object Blurry : CaptureResult
        data class Completed(val bitmaps: List<Bitmap>) : CaptureResult
    }
}