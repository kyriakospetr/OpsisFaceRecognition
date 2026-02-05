package com.example.opsisfacerecognition.domain.repository

import android.graphics.Bitmap
import com.example.opsisfacerecognition.data.entity.UserEntity
import com.example.opsisfacerecognition.domain.model.User

interface UserRepository {

    suspend fun enroll(user: User)

    suspend fun verify(queryEmbedding: FloatArray): List<UserEntity>

    suspend fun findByFullName(fullName: String): UserEntity?
}