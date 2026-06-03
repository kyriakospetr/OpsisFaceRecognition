package com.example.opsisfacerecognition.core.states

import com.example.opsisfacerecognition.data.local.entity.UserEntity

data class SettingsUiState(
    val users: List<UserEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)