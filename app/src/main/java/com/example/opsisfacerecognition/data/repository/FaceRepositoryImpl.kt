package com.example.opsisfacerecognition.data.repository

import android.graphics.Bitmap
import com.example.opsisfacerecognition.domain.repository.FaceRepository
import javax.inject.Inject

class FaceRepositoryImpl @Inject constructor(
): FaceRepository {
    override suspend fun enroll(frame: Bitmap) {
        TODO("Not yet implemented")
    }

    override suspend fun verify(frame: Bitmap) {
        TODO("Not yet implemented")
    }
}