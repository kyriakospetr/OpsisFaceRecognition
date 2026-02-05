package com.example.opsisfacerecognition.data.storage

import androidx.room.*
import com.example.opsisfacerecognition.data.entity.UserEntity
@Dao
interface UserDao {

    @Query("SELECT * FROM users")
    fun getAll(): List<UserEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity)

    @Query("SELECT * FROM users WHERE fullName = :fullName LIMIT 1")
    suspend fun getByFullName(fullName: String): UserEntity?
}
