package com.example.opsisfacerecognition.domain.usecase

import com.example.opsisfacerecognition.data.entity.UserEntity
import com.example.opsisfacerecognition.domain.repository.UserRepository
import javax.inject.Inject

class ListUsersUseCase @Inject constructor(
    private val repository: UserRepository
) {
    suspend operator fun invoke(): List<UserEntity> {
        return repository.list()
    }
}
