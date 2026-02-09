package com.example.opsisfacerecognition.data.repository

import com.example.opsisfacerecognition.data.entity.UserEntity
import com.example.opsisfacerecognition.data.entity.toEntity
import com.example.opsisfacerecognition.data.local.dao.UserDao
import com.example.opsisfacerecognition.domain.model.User
import com.example.opsisfacerecognition.domain.repository.UserRepository
import javax.inject.Inject


class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao
): UserRepository {
    override suspend fun insert(user: User) {
        val entity = user.toEntity()
        userDao.insert(entity)
    }

    override suspend fun list(): List<UserEntity> {
        val users = userDao.getAll()
        return users
    }

    override suspend fun findByFullName(fullName: String): UserEntity? {
        val user = userDao.getByFullName(fullName)
        return user
    }

    override suspend fun deleteByLocalId(localId: Long) {
        userDao.deleteByLocalId(localId)
    }

    override suspend fun deleteAll() {
        userDao.deleteAll()
    }
}
