package com.example.opsisfacerecognition.core.states

sealed interface FaceDetectionUiState {
    object Idle : FaceDetectionUiState
    object Scanning : FaceDetectionUiState
    object MultipleFacesDetected : FaceDetectionUiState
    object Success : FaceDetectionUiState
    data class Error(val message: String) : FaceDetectionUiState
}