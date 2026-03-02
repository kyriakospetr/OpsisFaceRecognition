package com.example.opsisfacerecognition.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.opsisfacerecognition.data.local.entity.EmbeddingConverters
import com.example.opsisfacerecognition.data.local.entity.UserEntity
import com.example.opsisfacerecognition.data.local.dao.UserDao

@Database(entities = [UserEntity::class], version = 1)
@TypeConverters(EmbeddingConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
