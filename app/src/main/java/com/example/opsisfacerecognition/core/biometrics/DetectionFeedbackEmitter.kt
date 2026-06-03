package com.example.opsisfacerecognition.core.biometrics

import com.example.opsisfacerecognition.core.states.FaceUiState.Detection
import javax.inject.Inject

class DetectionFeedbackEmitter @Inject constructor() {
    companion object {
        private const val FEEDBACK_SWITCH_COOLDOWN_MS = 800L
    }
    private var lastFeedback: Detection? = null
    private var lastFeedbackTimeMs: Long = 0L

    fun emit(feedback: Detection, nowMs: Long, emitter: (Detection) -> Unit) {
        val previousFeedback = lastFeedback
        // Check if the previous feedback is the same as the current
        // We have a feedback cooldown to not overload the ui
        // So if both conditions are false, then update the feedback
        if (previousFeedback != null) {
            val elapsedMs = nowMs - lastFeedbackTimeMs
            if (feedback == previousFeedback) return
            if (feedback != previousFeedback && elapsedMs < FEEDBACK_SWITCH_COOLDOWN_MS) return
        }

        lastFeedback = feedback
        lastFeedbackTimeMs = nowMs
        emitter(feedback)
    }
}