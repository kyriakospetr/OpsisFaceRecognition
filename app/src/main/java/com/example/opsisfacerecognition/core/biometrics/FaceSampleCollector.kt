package com.example.opsisfacerecognition.core.biometrics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.atan2
import kotlin.math.sqrt

class FaceSampleCollector(
    private val sampleIntervalMs: Long,
    private val blurVarianceThreshold: Double,
    private val targetSamples: Int,
    private val faceSize: Int,
    private val minEyeDistanceForAlignment: Float,
    private val targetLeftEyeX: Float,
    private val targetLeftEyeY: Float,
    private val targetRightEyeX: Float,
    private val targetRightEyeY: Float
) {

    sealed interface CaptureResult {
        data object Skipped : CaptureResult
        data object Added : CaptureResult
        data object Blurry : CaptureResult
        data class Completed(val bitmaps: List<Bitmap>) : CaptureResult
    }

    private val facePixels = faceSize * faceSize

    // We pause a bit after a successful capture.
    // We do not immediately start capturing 4 pictures.
    fun shouldCaptureSample(currentTime: Long, session: FaceCaptureSessionState): Boolean {
        return currentTime - session.lastSampleTimeMs >= sampleIntervalMs
    }

    fun captureSample(uprightBitmap: Bitmap, face: Face, currentTime: Long, session: FaceCaptureSessionState): CaptureResult {
        // Align the pre-decoded upright bitmap to produce the face crop
        val alignedFaceBitmap = alignFaceByEyes(uprightBitmap, face) ?: return CaptureResult.Skipped

        // Check for blur
        // We do not accept blurry images as our facenet model will extract unstable embeddings
        val blurVariance = calculateBlurVariance(alignedFaceBitmap)
        if (blurVariance < blurVarianceThreshold) {
            return CaptureResult.Blurry
        }

        // If it is not blurry and all requirements are correct
        // Add it to our bitmap list
        session.capturedBitmaps.add(alignedFaceBitmap)

        // Update the last time we took a sample with the current time
        session.lastSampleTimeMs = currentTime

        // If we reached the desired samples stop the process
        if (session.capturedBitmaps.size >= targetSamples) {
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
        if (eyeDistance < minEyeDistanceForAlignment) {
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
        val targetLeftEye = PointF(targetLeftEyeX, targetLeftEyeY)
        val targetRightEye = PointF(targetRightEyeX, targetRightEyeY)
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

        val alignedBitmap = createBitmap(faceSize, faceSize)
        val canvas = Canvas(alignedBitmap)
        canvas.drawBitmap(bitmap, transformationMatrix, Paint(Paint.FILTER_BITMAP_FLAG))

        return alignedBitmap
    }

    private fun calculateBlurVariance(bitmap: Bitmap): Double {
        val pixels = IntArray(facePixels)
        bitmap.getPixels(pixels, 0, faceSize, 0, 0, faceSize, faceSize)

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

        for (y in 1 until faceSize - 1) {
            for (x in 1 until faceSize - 1) {
                val c = y * faceSize + x
                val lap = gray[(y - 1) * faceSize + x] + gray[y * faceSize + (x - 1)] -
                        4.0 * gray[c] +
                        gray[y * faceSize + (x + 1)] + gray[(y + 1) * faceSize + x]
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
}
