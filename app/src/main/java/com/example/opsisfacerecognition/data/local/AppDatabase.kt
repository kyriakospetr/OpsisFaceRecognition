package com.example.opsisfacerecognition.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.opsisfacerecognition.data.entity.EmbeddingConverters
import com.example.opsisfacerecognition.data.entity.UserEntity
import com.example.opsisfacerecognition.data.storage.UserDao

@Database(entities = [UserEntity::class], version = 1)
@TypeConverters(EmbeddingConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
