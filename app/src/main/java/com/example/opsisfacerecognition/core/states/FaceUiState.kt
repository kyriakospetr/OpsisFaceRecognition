package com.example.opsisfacerecognition.core.states

sealed interface FaceUiState {
    object Idle : FaceUiState
    object Scanning : FaceUiState
    object MultipleFacesDetected : FaceUiState
    object Success : FaceUiState
    data class Error(val message: String) : FaceUiState
}