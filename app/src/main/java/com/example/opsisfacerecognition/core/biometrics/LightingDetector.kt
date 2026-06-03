package com.example.opsisfacerecognition.core.biometrics

import android.graphics.Bitmap
import android.util.Log
import java.util.Locale
import javax.inject.Inject

class LightingDetector @Inject constructor() {

    companion object {
        private const val TAG = "FaceQuality"
        const val LOW_LIGHT_LUMINANCE_THRESHOLD = 95.0
        const val HIGH_LIGHT_LUMINANCE_THRESHOLD = 160.0
        const val BLUR_VARIANCE_THRESHOLD = 90.0
    }

    fun analyze(bitmap: Bitmap): FaceQualityResult {
        val metrics = calculateMetrics(bitmap)
        val issue = metrics.toIssue()
        Log.d(
            TAG,
            "Quality estimate: luminance=${metrics.meanLuminance.formatScore()}, " +
                "blurVariance=${metrics.blurVariance.formatScore()}, issue=$issue."
        )
        return FaceQualityResult(
            issue = issue,
            metrics = metrics
        )
    }

    private fun calculateMetrics(bitmap: Bitmap): FaceQualityMetrics {
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var luminanceSum = 0.0
        val gray = DoubleArray(pixelCount) { index ->
            val pixel = pixels[index]
            val luminance = 0.299 * ((pixel shr 16) and 0xFF) +
                0.587 * ((pixel shr 8) and 0xFF) +
                0.114 * (pixel and 0xFF)
            luminanceSum += luminance
            luminance
        }

        return FaceQualityMetrics(
            meanLuminance = luminanceSum / pixelCount,
            blurVariance = calculateLaplacianVariance(gray, width, height)
        )
    }

    private fun calculateLaplacianVariance(gray: DoubleArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0

        var sum = 0.0
        var sumSquared = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = y * width + x
                val laplacian = gray[(y - 1) * width + x] +
                    gray[y * width + (x - 1)] -
                    4.0 * gray[center] +
                    gray[y * width + (x + 1)] +
                    gray[(y + 1) * width + x]
                sum += laplacian
                sumSquared += laplacian * laplacian
                count++
            }
        }

        val mean = sum / count
        return sumSquared / count - mean * mean
    }

    private fun FaceQualityMetrics.toIssue(): FaceQualityIssue =
        when {
            meanLuminance < LOW_LIGHT_LUMINANCE_THRESHOLD -> FaceQualityIssue.LOW_LIGHT
            meanLuminance > HIGH_LIGHT_LUMINANCE_THRESHOLD -> FaceQualityIssue.HIGH_LIGHT
            blurVariance < BLUR_VARIANCE_THRESHOLD -> FaceQualityIssue.BLUR
            else -> FaceQualityIssue.NONE
        }

    private fun Double.formatScore(): String = String.format(Locale.US, "%.2f", this)
}

data class FaceQualityResult(
    val issue: FaceQualityIssue,
    val metrics: FaceQualityMetrics
)

data class FaceQualityMetrics(
    val meanLuminance: Double,
    val blurVariance: Double
)

enum class FaceQualityIssue {
    NONE,
    LOW_LIGHT,
    HIGH_LIGHT,
    BLUR
}
