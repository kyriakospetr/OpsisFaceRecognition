package com.example.opsisfacerecognition.data.repository

import android.graphics.Bitmap
import com.example.opsisfacerecognition.domain.repository.FaceRepository
import javax.inject.Inject
import com.google.mlkit.vision.face.FaceDetector

class FaceRepositoryImpl @Inject constructor(
    private val detector: FaceDetector
): FaceRepository {
    override suspend fun enroll(frame: Bitmap) {
        TODO("Not yet implemented")
    }

    override suspend fun verify(frame: Bitmap) {
        TODO("Not yet implemented")
    }
}