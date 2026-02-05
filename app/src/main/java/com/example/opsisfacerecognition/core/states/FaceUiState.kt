package com.example.opsisfacerecognition.core.states

sealed interface FaceUiState {
    object Idle : FaceUiState

    object Loading : FaceUiState

    sealed interface Capture : FaceUiState {
        object Scanning : Capture
        object MultipleFacesDetected : Capture
        object DetectedSuccessfully : Capture
    }

    sealed interface Enroll : FaceUiState {
        object ConflictFullName: Enroll
        object Processed : Enroll
        object Completed : Enroll
    }

    sealed interface EnrollMasked : FaceUiState {
        object Processed : EnrollMasked
        object Completed : EnrollMasked
    }

    sealed interface Verify : FaceUiState {
        object Verified: Verify
        object NotVerified : Verify
    }

    data class Error(val message: String) : FaceUiState
}
