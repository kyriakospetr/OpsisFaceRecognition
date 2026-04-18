package com.example.opsisfacerecognition.domain.usecase

import com.example.opsisfacerecognition.core.biometrics.LiteRT
import com.example.opsisfacerecognition.data.local.entity.toUser
import com.example.opsisfacerecognition.domain.model.User
import com.example.opsisfacerecognition.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class VerifyUserUseCase @Inject constructor(
    private val repository: UserRepository,
    private val liteRT: LiteRT
) {
    companion object {
        private const val VERIFICATION_THRESHOLD = 0.82f
    }

    suspend operator fun invoke(embedding: FloatArray): User? = withContext(Dispatchers.Default) {
        // Retrieve all enrolled users
        val users = repository.list()
        if (users.isEmpty()) return@withContext null

        // Compute cosine similarity for each user
        val allScores = users
            .map { user ->
                user to liteRT.cosineSimilarity(embedding, user.embedding)
            }

        val (bestUser, bestScore) = allScores.maxByOrNull { it.second } ?: return@withContext null

        // Accept only if similarity is high enough
        if (bestScore >= VERIFICATION_THRESHOLD) {
            bestUser.toUser()
        } else {
            null
        }
    }
}
