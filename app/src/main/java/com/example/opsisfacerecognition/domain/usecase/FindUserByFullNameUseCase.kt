package com.example.opsisfacerecognition.domain.usecase

import com.example.opsisfacerecognition.data.entity.toUser
import com.example.opsisfacerecognition.domain.model.User
import com.example.opsisfacerecognition.domain.repository.UserRepository
import javax.inject.Inject

class FindUserByFullNameUseCase @Inject constructor(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(fullName: String): User? {
        val userEntity = repository.findByFullName(fullName)
        return userEntity?.toUser()
    }
}