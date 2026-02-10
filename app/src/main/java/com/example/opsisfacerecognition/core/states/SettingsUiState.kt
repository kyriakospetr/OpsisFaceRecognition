package com.example.opsisfacerecognition.core.states

import com.example.opsisfacerecognition.data.entity.UserEntity

data class SettingsUiState(
    val users: List<UserEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)