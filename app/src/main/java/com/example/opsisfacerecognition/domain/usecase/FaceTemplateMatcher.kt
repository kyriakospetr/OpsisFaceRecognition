package com.example.opsisfacerecognition.domain.usecase

import com.example.opsisfacerecognition.core.biometrics.LiteRT
import com.example.opsisfacerecognition.data.local.entity.UserEntity
import com.example.opsisfacerecognition.domain.model.FaceEmbeddingTemplates
import java.util.Locale
import javax.inject.Inject

class FaceTemplateMatcher @Inject constructor(
    private val liteRT: LiteRT
) {
    fun rankUsers(probe: FaceEmbeddingTemplates, users: List<UserEntity>): List<UserTemplateMatch> {
        return users
            .map { user ->
                UserTemplateMatch(
                    user = user,
                    templateMatch = bestTemplateMatch(probe, user.embeddingTemplates)
                )
            }
            .sortedByDescending { it.score }
    }

    fun bestTemplateMatch(probe: FaceEmbeddingTemplates, stored: FaceEmbeddingTemplates): TemplateMatch {
        var bestMatch = TemplateMatch(score = Float.NEGATIVE_INFINITY, label = "none")
        for (probeIndex in probe.vectors.indices) {
            for (storedIndex in stored.vectors.indices) {
                val score = liteRT.cosineSimilarity(probe.vectors[probeIndex], stored.vectors[storedIndex])
                if (score > bestMatch.score) {
                    bestMatch = TemplateMatch(
                        score = score,
                        label = "${probe.labels[probeIndex]}->${stored.labels[storedIndex]}"
                    )
                }
            }
        }
        return bestMatch
    }

    fun formatTemplateMatrix(probe: FaceEmbeddingTemplates, stored: FaceEmbeddingTemplates): String {
        return probe.vectors.indices.joinToString(separator = " | ") { probeIndex ->
            val scores = stored.vectors.indices.joinToString(separator = ", ") { storedIndex ->
                "${stored.labels[storedIndex]}=${liteRT.cosineSimilarity(probe.vectors[probeIndex], stored.vectors[storedIndex]).formatScore()}"
            }
            "${probe.labels[probeIndex]}:[$scores]"
        }
    }

    private fun Float.formatScore(): String = String.format(Locale.US, "%.4f", this)
}

data class UserTemplateMatch(
    val user: UserEntity,
    val templateMatch: TemplateMatch
) {
    val score: Float = templateMatch.score
}

data class TemplateMatch(
    val score: Float,
    val label: String
)
