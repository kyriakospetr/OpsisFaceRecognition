package com.example.opsisfacerecognition.core.biometrics.analysis

import com.google.mlkit.vision.face.Face

class BlinkLiveness(
    private val timeoutMs: Long,
    private val eyeOpenThreshold: Float,
    private val eyeClosedThreshold: Float
) {

    sealed interface Result {
        data object InProgress : Result
        data object Passed : Result
        data object Failed : Result
    }

    fun evaluate(face: Face, currentTime: Long, session: FaceCaptureSessionState): Result {
        // If we already passed liveness for this tracking id, no need to re-check.
        if (session.livenessPassed) return Result.Passed

        if (session.livenessStartTimeMs == null) {
            session.livenessStartTimeMs = currentTime
        }

        val elapsed = currentTime - (session.livenessStartTimeMs ?: currentTime)
        if (elapsed > timeoutMs) {
            session.resetLivenessState()
            return Result.Failed
        }

        val leftEyeOpen = face.leftEyeOpenProbability
        val rightEyeOpen = face.rightEyeOpenProbability
        if (leftEyeOpen == null || rightEyeOpen == null) {
            return Result.InProgress
        }

        // We use average eye probability for a simple blink challenge:
        // open -> closed -> reopened
        val eyeOpenScore = (leftEyeOpen + rightEyeOpen) / 2f
        return when (session.livenessStage) {
            LivenessStage.WAITING_FOR_OPEN_EYES -> {
                if (eyeOpenScore >= eyeOpenThreshold) {
                    session.livenessStage = LivenessStage.WAITING_FOR_CLOSED_EYES
                }
                Result.InProgress
            }

            LivenessStage.WAITING_FOR_CLOSED_EYES -> {
                if (eyeOpenScore <= eyeClosedThreshold) {
                    session.livenessStage = LivenessStage.WAITING_FOR_REOPENED_EYES
                }
                Result.InProgress
            }

            LivenessStage.WAITING_FOR_REOPENED_EYES -> {
                if (eyeOpenScore >= eyeOpenThreshold) {
                    session.livenessPassed = true
                    Result.Passed
                } else {
                    Result.InProgress
                }
            }
        }
    }
}
