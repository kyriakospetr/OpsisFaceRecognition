package com.example.opsisfacerecognition.core.states

sealed interface FaceUiState {
    data object Idle : FaceUiState
    data object Loading : FaceUiState
    sealed interface Detection : FaceUiState {
        data object FaceDetected : Detection
        data object NoFace : Detection
        data object MultipleFaces : Detection
        data object MoveCloser : Detection
        data object TooFar : Detection
        data object HoldStill : Detection
        data object LookStraight : Detection
        data object LookStraightAhead : Detection
        data object DontTiltHead : Detection
        data object CenterFace : Detection
        data object ImproveFocus : Detection
        data object EyesNotOpen : Detection
        data object WearingGlasses : Detection
        data object WearingHat : Detection
        data object TooClose : Detection
        data object SpoofDetected : Detection
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
