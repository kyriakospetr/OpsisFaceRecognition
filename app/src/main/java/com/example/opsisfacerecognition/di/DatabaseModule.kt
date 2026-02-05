package com.example.opsisfacerecognition.di

import android.content.Context
import androidx.room.Room
import com.example.opsisfacerecognition.data.local.AppDatabase
import com.example.opsisfacerecognition.data.storage.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "app.db"
        ).build()
    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
}
