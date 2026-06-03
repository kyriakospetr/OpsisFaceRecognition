package com.example.opsisfacerecognition.core.biometrics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import java.util.Locale
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.sqrt

class FaceSampleCollector @Inject constructor() {

    companion object {
        private const val TAG = "FaceSampleCollector"
        private const val TARGET_SAMPLES = 3
        private const val SAMPLE_INTERVAL_MS = 350L
        private const val MIN_EYE_DISTANCE_FOR_ALIGNMENT = 10f
        private const val FACE_SIZE = 112
        private const val TARGET_LEFT_EYE_X = 38f
        private const val TARGET_LEFT_EYE_Y = 40f
        private const val TARGET_RIGHT_EYE_X = 74f
        private const val TARGET_RIGHT_EYE_Y = 40f
    }
    // We pause a bit after a successful capture.
    // We do not immediately start capturing all samples.
    fun shouldCaptureSample(currentTime: Long, session: FaceCaptureSession): Boolean {
        return currentTime - session.lastSampleTimeMs >= SAMPLE_INTERVAL_MS
    }

    fun captureSample(uprightBitmap: Bitmap, face: Face, currentTime: Long, session: FaceCaptureSession): CaptureResult {
        // Align the pre-decoded upright bitmap to produce the face crop
        val alignedFaceBitmap = alignFaceByEyes(uprightBitmap, face) ?: return CaptureResult.Skipped("alignment failed")

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
            Log.d(
                TAG,
                "Sample skipped: eye landmarks missing. leftEye=${leftEyeLandmark != null}, " +
                    "rightEye=${rightEyeLandmark != null}."
            )
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
            Log.d(
                TAG,
                "Sample skipped: eye distance=${eyeDistance.formatScore()}px, " +
                    "minimum=${MIN_EYE_DISTANCE_FOR_ALIGNMENT.formatScore()}px."
            )
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


    private data class EyePair(
        val leftX: Float,
        val leftY: Float,
        val rightX: Float,
        val rightY: Float
    )

    private fun Float.formatScore(): String = String.format(Locale.US, "%.2f", this)

}
