package com.example.opsisfacerecognition.core.biometrics

import android.graphics.Bitmap
import android.graphics.PointF
import com.example.opsisfacerecognition.core.states.FaceUiState.Detection

class FaceCaptureSession {
    companion object {
        private const val WARNING_FAILURES_REQUIRED = 4
        private const val RECOVERY_SUCCESSES_REQUIRED = 2
    }

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

    var lastLivenessCheckTimeMs: Long = 0L
    var lastLivenessResult = LivenessDetector.LivenessResult(isLive = true, score = 1f)
    var lastQualityCheckTimeMs: Long = 0L
    var lastQualityIssue: FaceQualityIssue = FaceQualityIssue.NONE

    var consecutiveGlassesFailures: Int = 0
    var consecutiveHatFailures: Int = 0
    var consecutiveGlassesSuccesses: Int = 0
    var consecutiveHatSuccesses: Int = 0
    var isGlassesWarningActive: Boolean = false
    var isHatWarningActive: Boolean = false
    var consecutiveLivenessFailures: Int = 0
    var consecutiveSevereLivenessFailures: Int = 0
    var consecutiveLivenessSuccesses: Int = 0
    var isLivenessWarningActive: Boolean = false
    var consecutiveLowLightFailures: Int = 0
    var consecutiveHighLightFailures: Int = 0
    var consecutiveBlurFailures: Int = 0
    var consecutiveQualitySuccesses: Int = 0
    var isLowLightWarningActive: Boolean = false
    var isHighLightWarningActive: Boolean = false
    var isBlurWarningActive: Boolean = false

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
        lastAttributeResult = FaceAttributeClassifier.FaceAttributeResult(hasGlasses = false, hasHat = false)
        lastLivenessResult = LivenessDetector.LivenessResult(isLive = true, score = 1f)
        lastQualityCheckTimeMs = 0L
        lastQualityIssue = FaceQualityIssue.NONE

        consecutiveGlassesFailures = 0
        consecutiveHatFailures = 0
        consecutiveGlassesSuccesses = 0
        consecutiveHatSuccesses = 0
        isGlassesWarningActive = false
        isHatWarningActive = false
        consecutiveLivenessFailures = 0
        consecutiveSevereLivenessFailures = 0
        consecutiveLivenessSuccesses = 0
        isLivenessWarningActive = false
        consecutiveLowLightFailures = 0
        consecutiveHighLightFailures = 0
        consecutiveBlurFailures = 0
        consecutiveQualitySuccesses = 0
        isLowLightWarningActive = false
        isHighLightWarningActive = false
        isBlurWarningActive = false
    }

    fun updateAttributeState(result: FaceAttributeClassifier.FaceAttributeResult) {
        lastAttributeResult = result

        if (result.hasGlasses) {
            consecutiveGlassesFailures++
            consecutiveGlassesSuccesses = 0
            if (consecutiveGlassesFailures >= WARNING_FAILURES_REQUIRED) {
                isGlassesWarningActive = true
            }
        } else {
            consecutiveGlassesSuccesses++
            consecutiveGlassesFailures = 0
            if (consecutiveGlassesSuccesses >= RECOVERY_SUCCESSES_REQUIRED) {
                isGlassesWarningActive = false
            }
        }

        if (result.hasHat) {
            consecutiveHatFailures++
            consecutiveHatSuccesses = 0
            if (consecutiveHatFailures >= WARNING_FAILURES_REQUIRED) {
                isHatWarningActive = true
            }
        } else {
            consecutiveHatSuccesses++
            consecutiveHatFailures = 0
            if (consecutiveHatSuccesses >= RECOVERY_SUCCESSES_REQUIRED) {
                isHatWarningActive = false
            }
        }
    }

    fun getAttributeFeedback(): Detection? =
        when {
            isGlassesWarningActive -> Detection.WearingGlasses
            isHatWarningActive -> Detection.WearingHat
            else -> null
        }

    fun updateLivenessState(result: LivenessDetector.LivenessResult) {
        lastLivenessResult = result

        if (result.isLive) {
            consecutiveLivenessSuccesses++
            consecutiveLivenessFailures = 0
            consecutiveSevereLivenessFailures = 0

            if (consecutiveLivenessSuccesses >= RECOVERY_SUCCESSES_REQUIRED) {
                isLivenessWarningActive = false
            }
            return
        }

        consecutiveLivenessSuccesses = 0
        consecutiveLivenessFailures++

        if (consecutiveLivenessFailures >= WARNING_FAILURES_REQUIRED) {
            isLivenessWarningActive = true
        }
    }

    fun getLivenessFeedback(): Detection? =
        when {
            isLivenessWarningActive -> Detection.SpoofDetected
            else -> null
        }

    fun updateQualityState(issue: FaceQualityIssue) {
        lastQualityIssue = issue

        when (issue) {
            FaceQualityIssue.LOW_LIGHT -> {
                consecutiveLowLightFailures++
                consecutiveHighLightFailures = 0
                consecutiveBlurFailures = 0
                consecutiveQualitySuccesses = 0
                if (consecutiveLowLightFailures >= WARNING_FAILURES_REQUIRED) {
                    isLowLightWarningActive = true
                    isHighLightWarningActive = false
                    isBlurWarningActive = false
                }
            }
            FaceQualityIssue.HIGH_LIGHT -> {
                consecutiveHighLightFailures++
                consecutiveLowLightFailures = 0
                consecutiveBlurFailures = 0
                consecutiveQualitySuccesses = 0
                if (consecutiveHighLightFailures >= WARNING_FAILURES_REQUIRED) {
                    isHighLightWarningActive = true
                    isLowLightWarningActive = false
                    isBlurWarningActive = false
                }
            }
            FaceQualityIssue.BLUR -> {
                consecutiveLowLightFailures = 0
                consecutiveHighLightFailures = 0

                if (isLowLightWarningActive || isHighLightWarningActive) {
                    consecutiveBlurFailures = 0
                    consecutiveQualitySuccesses++
                    if (consecutiveQualitySuccesses >= RECOVERY_SUCCESSES_REQUIRED) {
                        isLowLightWarningActive = false
                        isHighLightWarningActive = false
                    }
                } else {
                    consecutiveBlurFailures++
                    consecutiveQualitySuccesses = 0
                    if (consecutiveBlurFailures >= WARNING_FAILURES_REQUIRED) {
                        isBlurWarningActive = true
                    }
                }
            }
            FaceQualityIssue.NONE -> {
                consecutiveQualitySuccesses++
                consecutiveLowLightFailures = 0
                consecutiveHighLightFailures = 0
                consecutiveBlurFailures = 0
                if (consecutiveQualitySuccesses >= RECOVERY_SUCCESSES_REQUIRED) {
                    isLowLightWarningActive = false
                    isHighLightWarningActive = false
                    isBlurWarningActive = false
                }
            }
        }
    }

    fun getQualityFeedback(): Detection? =
        when {
            isLowLightWarningActive -> Detection.ImproveLighting
            isHighLightWarningActive -> Detection.ReduceLighting
            isBlurWarningActive -> Detection.ImproveFocus
            else -> null
        }

    fun isLatestCheckClean(): Boolean =
        lastLivenessResult.isLive &&
            !lastAttributeResult.hasGlasses &&
            !lastAttributeResult.hasHat &&
            lastQualityIssue == FaceQualityIssue.NONE
}
