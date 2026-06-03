package com.example.opsisfacerecognition.domain.usecase

import com.example.opsisfacerecognition.domain.repository.UserRepository
import javax.inject.Inject

class DeleteUserByLocalIdUseCase @Inject constructor(
    private val repository: UserRepository
) {
    suspend operator fun invoke(localId: Long) {
        repository.deleteByLocalId(localId)
    }
}
