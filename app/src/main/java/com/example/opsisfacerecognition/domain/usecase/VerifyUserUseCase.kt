package com.example.opsisfacerecognition.domain.usecase

import com.example.opsisfacerecognition.core.biometrics.MobileFaceNetLiteRT
import com.example.opsisfacerecognition.data.entity.UserEntity
import com.example.opsisfacerecognition.data.entity.toUser
import com.example.opsisfacerecognition.domain.model.User
import com.example.opsisfacerecognition.domain.repository.UserRepository
import javax.inject.Inject

class VerifyUserUseCase @Inject constructor(
    private val repository: UserRepository,
    private val faceNetLiteRT: MobileFaceNetLiteRT
) {

    suspend operator fun invoke(embedding: FloatArray): User? {
        val users = repository.verify(embedding)
        if (users.isEmpty()) return null


        var bestUser: UserEntity? = null
        var bestScore = -1f

        // Get the user with the best score
        // Compare with Cosine Similarity
        for (user in users) {
            val score = faceNetLiteRT.cosineSimilarity(embedding, user.embedding)
            if (score > bestScore) {
                bestScore = score
                bestUser = user
            }
        }

        // Our Threshold
        val threshold = 0.70f

        // If the score is greater than threshold return the User Model
        return if (bestScore >= threshold) {
            bestUser?.toUser()
        }
        else null
    }
}