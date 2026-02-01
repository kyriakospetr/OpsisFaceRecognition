package com.example.opsisfacerecognition.domain.usecase

import android.graphics.Bitmap
import com.example.opsisfacerecognition.domain.repository.FaceRepository
import javax.inject.Inject

class EnrollFaceUseCase @Inject constructor(
    private val repository: FaceRepository
) {
    suspend operator fun invoke(image: Bitmap) {
        repository.enroll(image)
    }
}