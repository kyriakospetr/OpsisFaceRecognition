package com.example.opsisfacerecognition.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.opsisfacerecognition.data.entity.UserEntity
import com.example.opsisfacerecognition.domain.usecase.DeleteAllUsersUseCase
import com.example.opsisfacerecognition.domain.usecase.DeleteUserByLocalIdUseCase
import com.example.opsisfacerecognition.domain.usecase.ListUsersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val users: List<UserEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val listUsersUseCase: ListUsersUseCase,
    private val deleteUserByLocalIdUseCase: DeleteUserByLocalIdUseCase,
    private val deleteAllUsersUseCase: DeleteAllUsersUseCase
) : ViewModel() {

    // Our state so our ui can access it
    private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    // Load all users
    fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // instead of try-catch we use run catching
            runCatching {
                withContext(Dispatchers.IO) {
                    listUsersUseCase()
                }
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
            // instead of try-catch we use run catching
            runCatching {
                withContext(Dispatchers.IO) {
                    deleteUserByLocalIdUseCase(localId)
                    listUsersUseCase()
                }
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
            // instead of try-catch we use run catching
            runCatching {
                withContext(Dispatchers.IO) {
                    deleteAllUsersUseCase()
                }
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
