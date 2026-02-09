package com.example.opsisfacerecognition.core.biometrics.analysis

import com.example.opsisfacerecognition.core.states.FaceUiState.Detection

class DetectionFeedbackEmitter(
    private val sameFeedbackCooldownMs: Long,
    private val feedbackSwitchCooldownMs: Long
) {
    private var lastFeedback: Detection? = null
    private var lastFeedbackTimeMs: Long = 0L

    fun emit(feedback: Detection, nowMs: Long, emitter: (Detection) -> Unit) {
        val previousFeedback = lastFeedback
        if (previousFeedback != null) {
            val elapsedMs = nowMs - lastFeedbackTimeMs
            if (feedback == previousFeedback && elapsedMs < sameFeedbackCooldownMs) return
            if (feedback != previousFeedback && elapsedMs < feedbackSwitchCooldownMs) return
        }

        lastFeedback = feedback
        lastFeedbackTimeMs = nowMs
        emitter(feedback)
    }
}
