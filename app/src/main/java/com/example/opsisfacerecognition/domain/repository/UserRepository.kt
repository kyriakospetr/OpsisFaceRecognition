package com.example.opsisfacerecognition.domain.repository

import com.example.opsisfacerecognition.data.entity.UserEntity
import com.example.opsisfacerecognition.domain.model.User

interface UserRepository {

    suspend fun insert(user: User)

    suspend fun list(): List<UserEntity>

    suspend fun findByFullName(fullName: String): UserEntity?

    suspend fun deleteByLocalId(localId: Long)

    suspend fun deleteAll()
}
