package com.example.opsisfacerecognition.core.biometrics

import android.graphics.Bitmap
import android.graphics.PointF
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class FaceCaptureSession @Inject constructor() {
    // Existing session variables (tracking ID, bitmaps, etc.)
    var currentTrackingId: Int? = null
    var stabilityStartTimeMs: Long? = null
    var lastSampleTimeMs: Long = 0L
    val capturedBitmaps = mutableListOf<Bitmap>()
    var lastFaceCenter: PointF? = null
    var lastCenterUpdateTimeMs: Long = 0L

    @Volatile var isCaptureComplete: Boolean = false
    var lastAttributeCheckTimeMs: Long = 0L
    var lastAttributeResult = FaceAttributeClassifier.FaceAttributeResult(hasGlasses = false, hasHat = false)
    var lastLivenessResult = LivenessDetector.LivenessResult(isLive = true, score = 1f)

    var consecutiveGlassesFailures: Int = 0
    var consecutiveHatFailures: Int = 0
    var consecutiveLivenessFailures: Int = 0

    fun resetCaptureState() {
        currentTrackingId = null
        stabilityStartTimeMs = null
        lastSampleTimeMs = 0L
        capturedBitmaps.forEach { it.recycle() }
        capturedBitmaps.clear()
        lastFaceCenter = null
        lastCenterUpdateTimeMs = 0L

        // Reset the moved counters
        lastAttributeCheckTimeMs = 0L
        consecutiveGlassesFailures = 0
        consecutiveHatFailures = 0
        consecutiveLivenessFailures = 0
    }
}
