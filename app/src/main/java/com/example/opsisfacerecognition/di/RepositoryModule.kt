package com.example.opsisfacerecognition.di

import com.example.opsisfacerecognition.data.repository.UserRepositoryImpl
import com.example.opsisfacerecognition.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// This module binds interfaces to their respective implementations using @Binds.
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindFaceRepository(
        impl: UserRepositoryImpl
    ): UserRepository

}