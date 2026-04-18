package com.example.opsisfacerecognition.core.biometrics

import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.face.Face
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlin.math.abs

@ViewModelScoped
class FaceValidation @Inject constructor() {

    companion object {
        private const val POSITION_TOLERANCE = 0.5f
        private const val MIN_FACE_SIZE_RATIO = 0.45f
        private const val MAX_FACE_SIZE_RATIO = 2.20f

        private const val MAX_ROTATION_DEGREES = 15f
        private const val MAX_PITCH_DEGREES = 20f
    }

    fun checkFaceInsideOval(faceCenter: Offset, faceWidth: Float, ovalCenter: Offset, ovalRadiusX: Float, ovalRadiusY: Float): OvalCheckResult {
        // Check if the face is centered
        val horizontalDiff = abs(faceCenter.x - ovalCenter.x)
        val verticalDiff = abs(faceCenter.y - ovalCenter.y)

        val isWithinPositionTolerance = horizontalDiff <= (ovalRadiusX * POSITION_TOLERANCE) &&
                verticalDiff <= (ovalRadiusY * POSITION_TOLERANCE)

        if (!isWithinPositionTolerance) return OvalCheckResult.NOT_CENTERED

        // Check if the face is Too Far
        if (faceWidth < (ovalRadiusX * MIN_FACE_SIZE_RATIO)) {
            return OvalCheckResult.TOO_FAR
        }

        // Check if the face is Too Close
        if (faceWidth > (ovalRadiusX * MAX_FACE_SIZE_RATIO)) {
            return OvalCheckResult.TOO_CLOSE
        }
        return OvalCheckResult.OK
    }

    fun checkFaceOrientation(face: Face): OrientationCheckResult {
        // Yaw: looking left or right
        if (abs(face.headEulerAngleY) > MAX_ROTATION_DEGREES) return OrientationCheckResult.LOOK_STRAIGHT
        // Pitch: looking up or down (more lenient as users naturally look slightly down at their phone)
        if (abs(face.headEulerAngleX) > MAX_PITCH_DEGREES) return OrientationCheckResult.LOOK_STRAIGHT_AHEAD
        // Roll: head tilted sideways
        if (abs(face.headEulerAngleZ) > MAX_ROTATION_DEGREES) return OrientationCheckResult.DONT_TILT
        return OrientationCheckResult.OK
    }

    enum class OvalCheckResult { OK, NOT_CENTERED, TOO_FAR, TOO_CLOSE }

    enum class OrientationCheckResult { OK, LOOK_STRAIGHT, LOOK_STRAIGHT_AHEAD, DONT_TILT }
}
