package com.example.opsisfacerecognition.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.opsisfacerecognition.core.states.FaceFlowMode
import com.example.opsisfacerecognition.core.states.FaceUiState
import com.example.opsisfacerecognition.domain.model.User
import com.example.opsisfacerecognition.domain.usecase.ComputeEmbeddingUseCase
import com.example.opsisfacerecognition.domain.usecase.EnrollUserUseCase
import com.example.opsisfacerecognition.domain.usecase.FindUserByFullNameUseCase
import com.example.opsisfacerecognition.domain.usecase.VerifyUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FaceRecognizerViewModel @Inject constructor(
    private val computeEmbeddingUseCase: ComputeEmbeddingUseCase,
    private val findUserByFullNameUseCase: FindUserByFullNameUseCase,
    private val enrollUserUseCase: EnrollUserUseCase,
    private val verifyUserUseCase: VerifyUserUseCase
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
        viewModelScope.launch(Dispatchers.Default) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = FaceUiState.Loading
                }

                val embedding = computeEmbeddingUseCase(bitmaps)

                val timestamp =
                    java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault())
                        .format(java.util.Date())

                val newUser = User(
                    fullName = "User_$timestamp",
                    embedding = embedding
                )

                when (mode) {
                    FaceFlowMode.ENROLL -> {
                        withContext(Dispatchers.Main) {
                            _pendingUser.value = newUser
                            _uiState.value = FaceUiState.Enroll.CaptureProcessed
                        }
                    }
                    FaceFlowMode.VERIFY -> {
                        withContext(Dispatchers.Main) {
                            _pendingUser.value = newUser
                        }
                        verifyUser()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = FaceUiState.Error("Failed to process capture: ${e.message}")
                }
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
                val conflictUser = findUserByFullNameUseCase(fullName)
                if(conflictUser != null) {
                    _uiState.value = FaceUiState.Enroll.FullNameConflict
                    return@launch
                }
                val updatedUser = currentUser.copy(fullName = fullName)
                _pendingUser.value = updatedUser
                _uiState.value = FaceUiState.Loading
                withContext(Dispatchers.IO) {
                    enrollUserUseCase(updatedUser)
                }
                _uiState.value = FaceUiState.Enroll.Completed
            } catch (e: Exception) {
                _uiState.value = FaceUiState.Error("Failed to save user: ${e.message}")
            }
        }
    }

    fun verifyUser() {
        //Check if the current user is null
        val currentUser = _pendingUser.value
        if (currentUser == null) {
            _uiState.value = FaceUiState.Error("No pending user found. Please rescan.")
            return
        }

        // Get the embedding from the current user
        val embedding: FloatArray = currentUser.embedding
        viewModelScope.launch {
            try {
                _uiState.value = FaceUiState.Loading
                // We don't use the main thread as calculating this is heavy resource
                val user: User? = withContext(Dispatchers.IO) {
                    verifyUserUseCase(embedding)
                }
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
