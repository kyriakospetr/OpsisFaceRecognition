package com.example.opsisfacerecognition.core.states

sealed interface FaceUiState {
    data object Idle : FaceUiState

    data object Loading : FaceUiState

    sealed interface Detection : FaceUiState {
        data object FaceDetected : Detection

        data object NoFace : Detection
        data object MultipleFaces : Detection

        data object MoveCloser : Detection
        data object HoldStill : Detection
        data object LookStraight : Detection
        data object CenterFace : Detection

        data object ImproveFocus : Detection
    }

    sealed interface Enroll : FaceUiState {
        data object FullNameConflict: Enroll
        data object CaptureProcessed : Enroll
        data object Completed : Enroll
    }

    sealed interface Verify : FaceUiState {
        data object Verified: Verify
        data object VerificationFailed : Verify
    }

    data class Error(val message: String) : FaceUiState
}
