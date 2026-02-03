package com.example.opsisfacerecognition.di

import com.example.opsisfacerecognition.core.biometrics.MobileFaceNetLiteRT
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FaceEmbeddingModule {
    @Provides
    @Singleton
    fun provideMobileFaceNetLiteRT(
        mobileFaceNet: MobileFaceNetLiteRT
    ): MobileFaceNetLiteRT = mobileFaceNet
}