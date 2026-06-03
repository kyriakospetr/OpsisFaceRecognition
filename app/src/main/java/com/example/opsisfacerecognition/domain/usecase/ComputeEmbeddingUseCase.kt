package com.example.opsisfacerecognition.domain.usecase

import android.graphics.Bitmap
import com.example.opsisfacerecognition.domain.model.FaceEmbeddingTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ComputeEmbeddingUseCase @Inject constructor(
    private val faceEmbeddingTemplateBuilder: FaceEmbeddingTemplateBuilder
) {
    suspend operator fun invoke(images: List<Bitmap>): FaceEmbeddingTemplates = withContext(Dispatchers.Default) {
        faceEmbeddingTemplateBuilder.build(images)
    }
}
