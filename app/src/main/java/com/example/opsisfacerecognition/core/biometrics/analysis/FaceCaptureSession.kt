package com.example.opsisfacerecognition.core.biometrics.analysis

import android.graphics.Bitmap
import android.graphics.PointF

data class FaceCaptureSessionState(
    var currentTrackingId: Int? = null,
    var stabilityStartTimeMs: Long? = null,
    var lastSampleTimeMs: Long = 0L,
    var lastFaceCenter: PointF? = null,
    var lastCenterUpdateTimeMs: Long = 0L,
    var isCaptureComplete: Boolean = false,
    var livenessStage: LivenessStage = LivenessStage.WAITING_FOR_OPEN_EYES,
    var livenessStartTimeMs: Long? = null,
    var livenessPassed: Boolean = false,
    val capturedBitmaps: MutableList<Bitmap> = mutableListOf()
) {

    // Reset State helper
    // Use this when we lose the face, detect multiple faces or tracking id changes.
    fun resetCaptureState() {
        currentTrackingId = null
        stabilityStartTimeMs = null
        lastSampleTimeMs = 0L
        capturedBitmaps.clear()
        lastFaceCenter = null
        lastCenterUpdateTimeMs = 0L
        resetLivenessState()
    }

    fun resetLivenessState() {
        livenessStage = LivenessStage.WAITING_FOR_OPEN_EYES
        livenessStartTimeMs = null
        livenessPassed = false
    }
}

enum class LivenessStage {
    WAITING_FOR_OPEN_EYES,
    WAITING_FOR_CLOSED_EYES,
    WAITING_FOR_REOPENED_EYES
}
