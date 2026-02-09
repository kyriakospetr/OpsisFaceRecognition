package com.example.opsisfacerecognition.domain.usecase

import com.example.opsisfacerecognition.domain.repository.UserRepository
import javax.inject.Inject

class DeleteAllUsersUseCase @Inject constructor(
    private val repository: UserRepository
) {
    suspend operator fun invoke() {
        repository.deleteAll()
    }
}
