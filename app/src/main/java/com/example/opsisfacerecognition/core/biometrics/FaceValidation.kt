package com.example.opsisfacerecognition.core.biometrics

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.sqrt

class FaceValidation(
    private val positionTolerance: Float,
    private val minFaceSizeRatio: Float,
    private val maxRotationDegrees: Float,
    private val minEyeDistancePx: Float,
    private val maxCenterSpeedPxPerSecond: Float
) {

    enum class OvalCheckResult { OK, NOT_CENTERED, TOO_FAR }

    fun checkFaceInsideOval(faceCenter: Offset, faceWidth: Float, ovalCenter: Offset, ovalRadiusX: Float, ovalRadiusY: Float): OvalCheckResult {
        // We calculate the distance between the face and the center of the oval
        val horizontalDiff = abs(faceCenter.x - ovalCenter.x)
        val verticalDiff = abs(faceCenter.y - ovalCenter.y)

        // We check if it is near the oval based on our tolerance
        val isWithinPositionTolerance = horizontalDiff <= (ovalRadiusX * positionTolerance) &&
            verticalDiff <= (ovalRadiusY * positionTolerance)

        if (!isWithinPositionTolerance) return OvalCheckResult.NOT_CENTERED

        // We check if the face is near enough to the camera
        val isFaceSizeCorrect = faceWidth > (ovalRadiusX * minFaceSizeRatio)

        return if (isFaceSizeCorrect) OvalCheckResult.OK else OvalCheckResult.TOO_FAR
    }

    enum class OrientationCheckResult { OK, LOOK_STRAIGHT, LOOK_STRAIGHT_AHEAD, DONT_TILT }

    fun checkFaceOrientation(face: Face): OrientationCheckResult {
        // Yaw: looking left or right
        if (abs(face.headEulerAngleY) > maxRotationDegrees) return OrientationCheckResult.LOOK_STRAIGHT
        // Pitch: looking up or down
        if (abs(face.headEulerAngleX) > maxRotationDegrees) return OrientationCheckResult.LOOK_STRAIGHT_AHEAD
        // Roll: head tilted sideways
        if (abs(face.headEulerAngleZ) > maxRotationDegrees) return OrientationCheckResult.DONT_TILT
        return OrientationCheckResult.OK
    }

    // We rely on eye landmarks for alignment, so we need to verify they are detectable.
    fun areEyeLandmarksDetectable(face: Face): Boolean {
        val eyeDistance = calculateEyeDistance(face) ?: return false
        return eyeDistance >= minEyeDistancePx
    }

    fun isFaceMovementStable(face: Face, currentTime: Long, session: FaceCaptureSessionState): Boolean {
        // We calculate if the user is moving
        // Even though he may be in the correct position
        // If he moves we will capture blurry images or later our facenet model will not get the best embeddings
        // Even though we check for blur it is good to let the user know
        val centerX = face.boundingBox.exactCenterX()
        val centerY = face.boundingBox.exactCenterY()

        // So we check the center of the face how much it has moved
        val previousCenter = session.lastFaceCenter
        if (previousCenter == null) {
            session.lastFaceCenter = PointF(centerX, centerY)
            session.lastCenterUpdateTimeMs = currentTime
            return true
        }

        val timeDelta = (currentTime - session.lastCenterUpdateTimeMs).coerceAtLeast(1L)
        val deltaX = centerX - previousCenter.x
        val deltaY = centerY - previousCenter.y
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

        // We calculate at which speed the face is moving
        val speed = distance * 1000f / timeDelta.toFloat()

        session.lastFaceCenter = PointF(centerX, centerY)
        session.lastCenterUpdateTimeMs = currentTime

        // If it is moving faster return false
        return speed <= maxCenterSpeedPxPerSecond
    }

    // We calculate the eye distance.
    // It is useful if the face is a bit far.
    private fun calculateEyeDistance(face: Face): Float? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null

        val deltaX = rightEye.x - leftEye.x
        val deltaY = rightEye.y - leftEye.y
        return sqrt(deltaX * deltaX + deltaY * deltaY)
    }
}
