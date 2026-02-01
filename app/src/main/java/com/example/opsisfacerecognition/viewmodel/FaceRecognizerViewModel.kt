package com.example.opsisfacerecognition.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.example.opsisfacerecognition.core.states.FaceDetectionUiState
import com.example.opsisfacerecognition.domain.usecase.EnrollFaceUseCase
import com.example.opsisfacerecognition.domain.usecase.VerifyFaceUseCase
import com.google.mlkit.vision.face.Face
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class FaceRecognizerViewModel @Inject constructor(
    private val enrollFaceUseCase: EnrollFaceUseCase,
    private val verifyFaceUseCase: VerifyFaceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<FaceDetectionUiState>(FaceDetectionUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // We get the faces from our FaceDetector
    // We handle different states
    fun onFacesDetected(faces: List<Face>) {
        when {
            faces.isEmpty() -> {
                _uiState.value = FaceDetectionUiState.Scanning
            }
            faces.size > 1 -> {
                // We need only one face
                _uiState.value = FaceDetectionUiState.MultipleFacesDetected
            }
            else -> {
                _uiState.value = FaceDetectionUiState.Success
                val face = faces.first()
                enrollFace(face)
            }
        }
    }

    fun onEnrollmentImagesCaptured(bitmapList: List<Bitmap>) {
        // TODO: tensorflow-lite-support
    }

    private fun enrollFace(face: Face) {
        //
    }
}