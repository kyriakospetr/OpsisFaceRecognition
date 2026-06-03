package com.example.opsisfacerecognition.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.opsisfacerecognition.core.states.SettingsUiState
import com.example.opsisfacerecognition.domain.usecase.DeleteAllUsersUseCase
import com.example.opsisfacerecognition.domain.usecase.DeleteUserByLocalIdUseCase
import com.example.opsisfacerecognition.domain.usecase.ListUsersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val listUsersUseCase: ListUsersUseCase,
    private val deleteUserByLocalIdUseCase: DeleteUserByLocalIdUseCase,
    private val deleteAllUsersUseCase: DeleteAllUsersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    // Load all users
    fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                listUsersUseCase()
            }.onSuccess { users ->
                _uiState.value = SettingsUiState(users = users, isLoading = false)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Failed to load users."
                    )
                }
            }
        }
    }

    // Delete by id
    fun deleteUser(localId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                deleteUserByLocalIdUseCase(localId)
                listUsersUseCase() // Immediately fetch the updated list
            }.onSuccess { users ->
                _uiState.value = SettingsUiState(users = users, isLoading = false)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Failed to delete user."
                    )
                }
            }
        }
    }

    // Erase all
    fun eraseAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                deleteAllUsersUseCase()
            }.onSuccess {
                _uiState.value = SettingsUiState(users = emptyList(), isLoading = false)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Failed to erase data."
                    )
                }
            }
        }
    }
}