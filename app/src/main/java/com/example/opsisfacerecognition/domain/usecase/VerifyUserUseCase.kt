package com.example.opsisfacerecognition.domain.usecase

import android.util.Log
import com.example.opsisfacerecognition.data.local.entity.toUser
import com.example.opsisfacerecognition.domain.model.FaceEmbeddingTemplates
import com.example.opsisfacerecognition.domain.model.User
import com.example.opsisfacerecognition.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

class VerifyUserUseCase @Inject constructor(
    private val repository: UserRepository,
    private val faceTemplateMatcher: FaceTemplateMatcher
) {
    companion object {
        private const val TAG = "FaceVerification"
        private const val VERIFICATION_THRESHOLD = 0.74f
    }

    suspend operator fun invoke(probeTemplates: FaceEmbeddingTemplates): User? = withContext(Dispatchers.Default) {
        // Retrieve all enrolled users
        val users = repository.list()
        if (users.isEmpty()) {
            Log.d(TAG, "Verification failed: no enrolled users.")
            return@withContext null
        }

        val matches = faceTemplateMatcher.rankUsers(probeTemplates, users)

        val bestMatch = matches.firstOrNull() ?: return@withContext null
        val scoreSummary = matches
            .take(3)
            .joinToString(separator = ", ") { match ->
                "localId=${match.user.localId}:${match.score.formatScore()}"
            }
        Log.d(
            TAG,
            "Verification bestLocalId=${bestMatch.user.localId}, score=${bestMatch.score.formatScore()}, " +
                "threshold=${VERIFICATION_THRESHOLD.formatScore()}, accepted=${bestMatch.score >= VERIFICATION_THRESHOLD}, " +
                "probeTemplates=${probeTemplates.vectors.size}, storedTemplates=${bestMatch.user.embeddingTemplates.vectors.size}, " +
                "bestTemplate=${bestMatch.templateMatch.label}, " +
                "candidates=${users.size}, top=[$scoreSummary]"
        )
        Log.d(TAG, "Template score matrix: ${faceTemplateMatcher.formatTemplateMatrix(probeTemplates, bestMatch.user.embeddingTemplates)}")

        // Accept only if similarity is high enough
        if (bestMatch.score >= VERIFICATION_THRESHOLD) {
            bestMatch.user.toUser()
        } else {
            null
        }
    }

    private fun Float.formatScore(): String = String.format(Locale.US, "%.4f", this)
}
