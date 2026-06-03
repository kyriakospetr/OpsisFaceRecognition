package com.example.opsisfacerecognition.domain.usecase

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.opsisfacerecognition.core.biometrics.LiteRT
import com.example.opsisfacerecognition.domain.model.FaceEmbeddingTemplates
import java.util.Locale
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

class FaceEmbeddingTemplateBuilder @Inject constructor(
    private val liteRT: LiteRT
) {
    companion object {
        private const val TAG = "FaceEmbedding"
        private const val TARGET_LUMA_MEAN = 128f
        private const val TARGET_LUMA_STD = 52f
        private const val MIN_CONTRAST_SCALE = 0.65f
        private const val MAX_CONTRAST_SCALE = 1.65f
        private const val DARK_GAMMA = 2.2f
    }

    fun build(images: List<Bitmap>): FaceEmbeddingTemplates {
        val originalEmbeddings = images.map { image ->
            liteRT.l2Normalize(liteRT.getEmbedding(image))
        }
        logCaptureConsistency(originalEmbeddings)

        val templates = buildExposureTemplates(images, originalEmbeddings)
        Log.d(TAG, "Embedding templates=${templates.size}, originalSamples=${images.size}")

        return FaceEmbeddingTemplates(templates, FaceEmbeddingTemplates.EXPOSURE_TEMPLATE_LABELS)
    }

    private fun buildExposureTemplates(images: List<Bitmap>, originalEmbeddings: List<FloatArray>): List<FloatArray> {
        val originalTemplate = averageNormalized(originalEmbeddings)
        val normalizedEmbeddings = mutableListOf<FloatArray>()
        val gammaDarkEmbeddings = mutableListOf<FloatArray>()
        val equalizedEmbeddings = mutableListOf<FloatArray>()

        for (image in images) {
            val normalizedImage = normalizeExposure(image)
            val gammaDarkImage = applyGamma(image, DARK_GAMMA)
            val equalizedImage = equalizeLuma(image)
            try {
                normalizedEmbeddings += liteRT.l2Normalize(liteRT.getEmbedding(normalizedImage))
                gammaDarkEmbeddings += liteRT.l2Normalize(liteRT.getEmbedding(gammaDarkImage))
                equalizedEmbeddings += liteRT.l2Normalize(liteRT.getEmbedding(equalizedImage))
            } finally {
                normalizedImage.recycle()
                gammaDarkImage.recycle()
                equalizedImage.recycle()
            }
        }

        return listOf(
            originalTemplate,
            averageNormalized(normalizedEmbeddings),
            averageNormalized(gammaDarkEmbeddings),
            averageNormalized(equalizedEmbeddings)
        )
    }

    private fun averageNormalized(embeddings: List<FloatArray>): FloatArray {
        return liteRT.l2Normalize(liteRT.averageEmbeddings(embeddings))
    }

    private fun logCaptureConsistency(embeddings: List<FloatArray>) {
        if (embeddings.size < 2) {
            Log.d(TAG, "Embedding computed from ${embeddings.size} sample.")
            return
        }

        val pairScores = mutableListOf<Float>()
        for (i in embeddings.indices) {
            for (j in i + 1 until embeddings.size) {
                pairScores += liteRT.cosineSimilarity(embeddings[i], embeddings[j])
            }
        }

        val minScore = pairScores.minOrNull() ?: return
        val avgScore = pairScores.average().toFloat()
        Log.d(
            TAG,
            "Embedding samples=${embeddings.size}, pairwiseMin=${minScore.formatScore()}, " +
                "pairwiseAvg=${avgScore.formatScore()}"
        )
    }

    private fun normalizeExposure(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var sum = 0.0
        var sumSquares = 0.0
        for (pixel in pixels) {
            val luma = pixel.luma()
            sum += luma
            sumSquares += luma * luma
        }

        val count = pixels.size.coerceAtLeast(1)
        val mean = (sum / count).toFloat()
        val variance = (sumSquares / count - mean * mean).coerceAtLeast(0.0)
        val std = sqrt(variance).toFloat()
        val contrastScale = if (std < 1f) {
            1f
        } else {
            (TARGET_LUMA_STD / std).coerceIn(MIN_CONTRAST_SCALE, MAX_CONTRAST_SCALE)
        }
        val brightnessOffset = TARGET_LUMA_MEAN - mean * contrastScale

        val adjustedPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = Color.alpha(pixel)
            val red = adjustChannel(Color.red(pixel), contrastScale, brightnessOffset)
            val green = adjustChannel(Color.green(pixel), contrastScale, brightnessOffset)
            val blue = adjustChannel(Color.blue(pixel), contrastScale, brightnessOffset)
            adjustedPixels[i] = Color.argb(alpha, red, green, blue)
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(adjustedPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun applyGamma(source: Bitmap, gamma: Float): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val adjustedPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            adjustedPixels[i] = Color.argb(
                Color.alpha(pixel),
                gammaCorrect(Color.red(pixel), gamma),
                gammaCorrect(Color.green(pixel), gamma),
                gammaCorrect(Color.blue(pixel), gamma)
            )
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(adjustedPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun equalizeLuma(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val histogram = IntArray(256)
        val lumas = IntArray(pixels.size)
        for (i in pixels.indices) {
            val luma = pixels[i].luma().toInt().coerceIn(0, 255)
            lumas[i] = luma
            histogram[luma]++
        }

        val cdf = IntArray(256)
        var running = 0
        for (i in histogram.indices) {
            running += histogram[i]
            cdf[i] = running
        }
        val cdfMin = cdf.firstOrNull { it > 0 } ?: 0
        val denominator = (pixels.size - cdfMin).coerceAtLeast(1)

        val adjustedPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val oldLuma = lumas[i].coerceAtLeast(1)
            val newLuma = ((cdf[oldLuma] - cdfMin) * 255f / denominator).toInt().coerceIn(0, 255)
            val scale = (newLuma / oldLuma.toFloat()).coerceIn(0.35f, 2.4f)
            adjustedPixels[i] = Color.argb(
                Color.alpha(pixel),
                (Color.red(pixel) * scale).toInt().coerceIn(0, 255),
                (Color.green(pixel) * scale).toInt().coerceIn(0, 255),
                (Color.blue(pixel) * scale).toInt().coerceIn(0, 255)
            )
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(adjustedPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun Int.luma(): Double {
        return 0.299 * Color.red(this) + 0.587 * Color.green(this) + 0.114 * Color.blue(this)
    }

    private fun adjustChannel(value: Int, contrastScale: Float, brightnessOffset: Float): Int {
        return (value * contrastScale + brightnessOffset).toInt().coerceIn(0, 255)
    }

    private fun gammaCorrect(value: Int, gamma: Float): Int {
        return (255f * (value / 255f).pow(gamma)).toInt().coerceIn(0, 255)
    }

    private fun Float.formatScore(): String = String.format(Locale.US, "%.4f", this)
}
