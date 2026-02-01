package com.example.opsisfacerecognition.domain.repository

import android.graphics.Bitmap

interface FaceRepository {

    suspend fun enroll(
        frame: Bitmap
    )

    suspend fun verify(
        frame: Bitmap
    )
}