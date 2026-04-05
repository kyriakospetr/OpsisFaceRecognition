package com.example.opsisfacerecognition.domain.usecase

import android.graphics.Bitmap
import com.example.opsisfacerecognition.core.biometrics.MobileFaceNetLiteRT
import javax.inject.Inject

class ComputeEmbeddingUseCase @Inject constructor(
    private val faceNetLiteRT: MobileFaceNetLiteRT
) {
    operator fun invoke(images: List<Bitmap>): FloatArray {
        // For each bitmap, get the embeddings then get the average embedding and then normalize them with L2
        // We normalize each embedding first so every sample contributes equally regardless of magnitude
        val embeddings = images.map { faceNetLiteRT.l2Normalize(faceNetLiteRT.getEmbedding(it)) }
        val avg = faceNetLiteRT.averageEmbeddings(embeddings)
        val normalized = faceNetLiteRT.l2Normalize(avg)

        return normalized
    }
}