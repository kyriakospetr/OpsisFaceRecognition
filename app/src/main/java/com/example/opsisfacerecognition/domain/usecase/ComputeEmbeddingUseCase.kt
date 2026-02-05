package com.example.opsisfacerecognition.domain.usecase

import android.graphics.Bitmap
import com.example.opsisfacerecognition.core.biometrics.MobileFaceNetLiteRT
import javax.inject.Inject

class ComputeEmbeddingUseCase @Inject constructor(
    private val faceNetLiteRT: MobileFaceNetLiteRT
) {
    operator fun invoke(images: List<Bitmap>): FloatArray {
        require(images.size >= 3) { "Not enough enrollment images" }

        // For each bitmap, get the embeddings then get the average embedding and then normalize them with L2
        // We get 4 images for each enrollment because we don't want to rely on a "lucky" embedding
        val embeddings = images.map { faceNetLiteRT.getEmbedding(it) }
        val avg = faceNetLiteRT.averageEmbeddings(embeddings)
        val normalized = faceNetLiteRT.l2Normalize(avg)

        return normalized
    }
}