package com.example.opsisfacerecognition.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.opsisfacerecognition.data.entity.UserEntity

@Dao
interface UserDao {

    @Query("SELECT * FROM users ORDER BY localId ASC")
    fun getAll(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.Companion.ABORT)
    suspend fun insert(user: UserEntity)

    @Query("SELECT * FROM users WHERE fullName = :fullName LIMIT 1")
    suspend fun getByFullName(fullName: String): UserEntity?

    @Query("DELETE FROM users WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Long)

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}
