package com.example.opsisfacerecognition.core.biometrics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.scale
import kotlin.math.exp

// Code converted from KeylessTech
@Singleton
class LivenessDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : AutoCloseable {
    companion object {
        private const val MODEL_INPUT_SIZE = 80 // Silentface models expect 80x80 input
        private const val LIVENESS_THRESHOLD = 0.94f // Minimum score to consider a face as live
        private val MODEL_FILES  = listOf("silentface40.onnx", "silentface27.onnx")
        private val CROP_SCALES  = floatArrayOf(4.0f, 2.7f)
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    // Load both ONNX models from assets at initialization — throws if any model is missing
    private val sessions: List<OrtSession> = MODEL_FILES.map { name ->
        val bytes = context.assets.open(name).readBytes()
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            // Use NNAPI hardware acceleration when available, fall back to CPU silently
            runCatching { addNnapi() }
        }
        env.createSession(bytes, opts)
    }

    // Runs both models and averages their live scores
    // upright: the full camera frame already rotated to correct orientation
    // boundingBox: the face bounding box from ML Kit
    fun check(upright: Bitmap, boundingBox: Rect): LivenessResult {
        var liveSum = 0f
        var validModels = 0
        sessions.forEachIndexed { index, session ->
            // Crop the face region at the given scale and resize to 80x80
            val scale = CROP_SCALES[index]
            val crop = cropAndScale(upright, boundingBox, scale) ?: return@forEachIndexed
            val score = runCatching { infer(session, crop) }.getOrDefault(0f)
            crop.recycle()
            liveSum += score
            validModels++
        }

        // If no model could produce a crop, reject — do not grant access
        if (validModels == 0) {
            return LivenessResult(isLive = false, score = 0f)
        }

        // Average the live score from models that succeeded
        val avg = liveSum / validModels
        return LivenessResult(isLive = avg >= LIVENESS_THRESHOLD, score = avg)
    }

    private fun infer(session: OrtSession, bitmap: Bitmap): Float {
        // Runs a single ONNX model on the cropped face bitmap
        // Returns the probability of the face being live (index 1 after softmax)
        val inputName = session.inputNames.iterator().next()
        val shape = longArrayOf(1L, 3L, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(bitmapToNCHW(bitmap)), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val logits = (result.get(0).value as Array<*>)[0] as FloatArray
                return softmax(logits)[1] // index 1 = live class
            }
        }
    }

    private fun cropAndScale(bitmap: Bitmap, box: Rect, scale: Float): Bitmap? {
        // Crops the face region from the full frame at the given scale
        // Scale > 1 means we take a larger area around the face (more context)
        // The crop is centered on the face bounding box

        // Clamp scale so we don't exceed image boundaries
        val srcW = bitmap.width
        val srcH = bitmap.height
        val boxW = box.width()
        val boxH = box.height()
        if (boxW <= 0 || boxH <= 0) return null

        // Shift crop window if it goes outside image bounds
        val s = minOf(scale, (srcH - 1f) / boxH, (srcW - 1f) / boxW)
        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        var l = cx - boxW * s / 2f
        var t = cy - boxH * s / 2f
        var r = cx + boxW * s / 2f
        var b = cy + boxH * s / 2f

        if (l < 0)        { r -= l;              l = 0f }
        if (t < 0)        { b -= t;              t = 0f }
        if (r > srcW - 1) { l -= r - (srcW - 1f); r = srcW - 1f }
        if (b > srcH - 1) { t -= b - (srcH - 1f); b = srcH - 1f }

        val x1 = l.toInt().coerceAtLeast(0)
        val y1 = t.toInt().coerceAtLeast(0)
        val x2 = r.toInt().coerceAtMost(srcW)
        val y2 = b.toInt().coerceAtMost(srcH)
        if (x2 - x1 <= 0 || y2 - y1 <= 0) return null

        val cropped = Bitmap.createBitmap(bitmap, x1, y1, x2 - x1, y2 - y1)
        val scaled = cropped.scale(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    // Converts an ARGB_8888 bitmap to a CHW (Channel, Height, Width) float array
    // Raw pixel values are kept in [0, 255] range as expected by Silentface
    private fun bitmapToNCHW(bitmap: Bitmap): FloatArray {
        val n = MODEL_INPUT_SIZE * MODEL_INPUT_SIZE
        val pixels = IntArray(n)
        bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        val out = FloatArray(3 * n)
        for (i in pixels.indices) {
            val px = pixels[i]
            out[i]       = ((px shr 16) and 0xFF).toFloat() // R
            out[n + i]   = ((px shr 8)  and 0xFF).toFloat() // G
            out[2 * n + i] = (px        and 0xFF).toFloat() // B
        }
        return out
    }

    // Numerically stable softmax — subtracts max before exp to avoid overflow
    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exp = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
        val sum = exp.sum()
        return FloatArray(exp.size) { exp[it] / sum }
    }

    override fun close() {
        sessions.forEach { it.close() }
    }

    data class LivenessResult(val isLive: Boolean, val score: Float)
}
