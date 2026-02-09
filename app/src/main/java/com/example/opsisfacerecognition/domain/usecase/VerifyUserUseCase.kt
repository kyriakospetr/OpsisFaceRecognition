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
    companion object {
        private const val VERIFICATION_THRESHOLD = 0.82f
    }

    suspend operator fun invoke(embedding: FloatArray): User? {
        // Retrieve all enrolled users
        val users = repository.list()
        if (users.isEmpty()) return null

        // Compute cosine similarity for each user
        val allScores = users
            .map { user ->
                user to faceNetLiteRT.cosineSimilarity(embedding, user.embedding)
            }
        val (bestUser, bestScore) = allScores.maxByOrNull { it.second } ?: return null

        val verificationThreshold = VERIFICATION_THRESHOLD
        val isAccepted = bestScore >= verificationThreshold

        // Accept only if similarity is high enough
        return if (isAccepted) {
            bestUser.toUser()
        } else {
            null
        }
    }
}
