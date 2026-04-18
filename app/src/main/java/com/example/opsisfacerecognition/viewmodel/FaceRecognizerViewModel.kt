package com.example.opsisfacerecognition.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.opsisfacerecognition.core.biometrics.FaceAnalyzerFactory
import com.example.opsisfacerecognition.core.states.FaceFlowMode
import com.example.opsisfacerecognition.core.states.FaceUiState
import com.example.opsisfacerecognition.domain.model.User
import com.example.opsisfacerecognition.domain.usecase.ComputeEmbeddingUseCase
import com.example.opsisfacerecognition.domain.usecase.EnrollUserUseCase
import com.example.opsisfacerecognition.domain.usecase.FindUserByFullNameUseCase
import com.example.opsisfacerecognition.domain.usecase.VerifyUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FaceRecognizerViewModel @Inject constructor(
    private val computeEmbeddingUseCase: ComputeEmbeddingUseCase,
    private val findUserByFullNameUseCase: FindUserByFullNameUseCase,
    private val enrollUserUseCase: EnrollUserUseCase,
    private val verifyUserUseCase: VerifyUserUseCase,
    val faceAnalyzerFactory: FaceAnalyzerFactory,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FaceUiState>(FaceUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _pendingUser = MutableStateFlow<User?>(null)
    val pendingUser = _pendingUser.asStateFlow()

    fun onDetectionFeedback(d: FaceUiState.Detection) {
        if (shouldIgnoreDetectionFeedback(_uiState.value)) return
        _uiState.value = d
    }

    private fun shouldIgnoreDetectionFeedback(currentState: FaceUiState): Boolean =
        when (currentState) {
            FaceUiState.Loading,
            FaceUiState.Enroll.CaptureProcessed,
            FaceUiState.Enroll.Completed,
            FaceUiState.Enroll.FullNameConflict,
            FaceUiState.Verify.Verified,
            FaceUiState.Verify.VerificationFailed,
            is FaceUiState.Error -> true
            else -> false
        }

    fun onImagesCaptured(bitmaps: List<Bitmap>, mode: FaceFlowMode) {
        // Just use standard viewModelScope.launch!
        viewModelScope.launch {
            try {
                _uiState.value = FaceUiState.Loading

                // This is now safe because the UseCase internally uses Dispatchers.Default
                val embedding = computeEmbeddingUseCase(bitmaps)

                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault())
                    .format(java.util.Date())

                val newUser = User(fullName = "User_$timestamp", embedding = embedding)

                when (mode) {
                    FaceFlowMode.ENROLL -> {
                        _pendingUser.value = newUser
                        _uiState.value = FaceUiState.Enroll.CaptureProcessed
                    }
                    FaceFlowMode.VERIFY -> {
                        _pendingUser.value = newUser
                        verifyUser()
                    }
                }
            } catch (e: Exception) {
                _uiState.value = FaceUiState.Error("Failed to process capture: ${e.message}")
            }
        }
    }


    fun enrollUser(fullName: String) {
        val currentUser = _pendingUser.value
        if (currentUser == null) {
            _uiState.value = FaceUiState.Error("No pending user found.")
            return
        }

        viewModelScope.launch {
            try {
                // Room is naturally main-safe, no IO dispatcher needed!
                val conflictUser = findUserByFullNameUseCase(fullName)
                if(conflictUser != null) {
                    _uiState.value = FaceUiState.Enroll.FullNameConflict
                    return@launch
                }

                val updatedUser = currentUser.copy(fullName = fullName)
                _pendingUser.value = updatedUser
                _uiState.value = FaceUiState.Loading

                // Room handles the IO thread internally here too
                enrollUserUseCase(updatedUser)

                _uiState.value = FaceUiState.Enroll.Completed
            } catch (e: Exception) {
                _uiState.value = FaceUiState.Error("Failed to save user: ${e.message}")
            }
        }
    }

    fun verifyUser() {
        val currentUser = _pendingUser.value
        if (currentUser == null) {
            _uiState.value = FaceUiState.Error("No pending user found. Please rescan.")
            return
        }

        val embedding = currentUser.embedding

        viewModelScope.launch {
            try {
                _uiState.value = FaceUiState.Loading
                val user = verifyUserUseCase(embedding)

                _pendingUser.value = user
                _uiState.value = if (user != null) {
                    FaceUiState.Verify.Verified
                } else {
                    FaceUiState.Verify.VerificationFailed
                }
            } catch (e: Exception) {
                _uiState.value = FaceUiState.Error("Failed to get user: ${e.message}")
            }
        }
    }
}
