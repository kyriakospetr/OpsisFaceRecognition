package com.example.opsisfacerecognition.domain.usecase

import com.example.opsisfacerecognition.core.biometrics.MobileFaceNetLiteRT
import com.example.opsisfacerecognition.data.entity.toUser
import com.example.opsisfacerecognition.domain.model.User
import com.example.opsisfacerecognition.domain.repository.UserRepository
import javax.inject.Inject

class VerifyUserUseCase @Inject constructor(
    private val repository: UserRepository,
    private val faceNetLiteRT: MobileFaceNetLiteRT
) {
    suspend operator fun invoke(embedding: FloatArray): User? {
        // Retrieve all enrolled users
        val users = repository.list()
        if (users.isEmpty()) return null

        // Compute cosine similarity for each user
        val (bestUser, bestScore) = users
            .map { user ->
                user to faceNetLiteRT.cosineSimilarity(embedding, user.embedding)
            }
            .maxByOrNull { it.second } ?: return null

        // Verification threshold
        val threshold = 0.80f

        // Accept only if similarity is high enough
        return if (bestScore >= threshold) {
            bestUser.toUser()
        } else {
            null
        }
    }
}

