package com.example.opsisfacerecognition.di

import com.example.opsisfacerecognition.core.biometrics.DetectionFeedbackEmitter
import com.example.opsisfacerecognition.core.biometrics.FaceAttributeClassifier
import com.example.opsisfacerecognition.core.biometrics.FaceSampleCollector
import com.example.opsisfacerecognition.core.biometrics.FaceValidation
import com.example.opsisfacerecognition.core.biometrics.LivenessDetector
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BiometricsModule {

    @Provides
    @Singleton
    fun provideFaceDetector(): FaceDetector {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .enableTracking()
            .build()

        return FaceDetection.getClient(options)
    }
}