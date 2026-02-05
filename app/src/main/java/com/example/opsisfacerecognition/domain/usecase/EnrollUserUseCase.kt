package com.example.opsisfacerecognition.domain.usecase

import com.example.opsisfacerecognition.domain.model.User
import com.example.opsisfacerecognition.domain.repository.UserRepository
import javax.inject.Inject

class EnrollUserUseCase @Inject constructor(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(user: User) {
        repository.enroll(user)
    }
}