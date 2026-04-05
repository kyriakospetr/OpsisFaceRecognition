package com.example.opsisfacerecognition.core.biometrics

import android.graphics.Bitmap
import android.graphics.PointF

class FaceCaptureSessionState(
    var currentTrackingId: Int? = null,
    var stabilityStartTimeMs: Long? = null,
    var lastSampleTimeMs: Long = 0L,
    var lastFaceCenter: PointF? = null,
    var lastCenterUpdateTimeMs: Long = 0L,
    var isCaptureComplete: Boolean = false,
    val capturedBitmaps: MutableList<Bitmap> = mutableListOf()
) {

    // Reset State helper
    // Use this when we lose the face, detect multiple faces or tracking id changes.
    // Callers must hold a synchronized(this) lock.
    fun resetCaptureState() {
        currentTrackingId = null
        stabilityStartTimeMs = null
        lastSampleTimeMs = 0L
        capturedBitmaps.forEach { it.recycle() }
        capturedBitmaps.clear()
        lastFaceCenter = null
        lastCenterUpdateTimeMs = 0L
    }
}
