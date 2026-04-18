package com.example.opsisfacerecognition.domain.usecase

import android.graphics.Bitmap
import com.example.opsisfacerecognition.core.biometrics.LiteRT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ComputeEmbeddingUseCase @Inject constructor(
    private val liteRT: LiteRT
) {
    suspend operator fun invoke(images: List<Bitmap>): FloatArray = withContext(Dispatchers.Default) {
        // For each bitmap, get the embeddings then get the average embedding and then normalize them with L2
        // We normalize each embedding first so every sample contributes equally regardless of magnitude
        val embeddings = images.map { liteRT.l2Normalize(liteRT.getEmbedding(it)) }
        val avg = liteRT.averageEmbeddings(embeddings)
        val normalized = liteRT.l2Normalize(avg)

        return@withContext normalized
    }
}