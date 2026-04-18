package com.example.opsisfacerecognition.core.biometrics

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.TensorImage
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import org.tensorflow.lite.DataType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class LiteRT @Inject constructor(
    @ApplicationContext context: Context
) : AutoCloseable {
    private val model: CompiledModel = CompiledModel.create(
        context.assets,
        "mobilefacenet.tflite",
        CompiledModel.Options(Accelerator.CPU)
    )

    // Buffers used to pass data in and out of the C++ LiteRT environment efficiently
    private val inputBuffers = model.createInputBuffers()
    private val outputBuffers = model.createOutputBuffers()

    // Prepares the image to match the exact input tensor shape the model was trained on
    private val imageProcessor = ImageProcessor.Builder()
        //Resize the face crop to exactly 112x112 pixels
        .add(ResizeOp(112, 112, ResizeOp.ResizeMethod.BILINEAR))
        // Normalize pixel values from [0, 255] to roughly [-1.0, 1.0].
        .add(NormalizeOp(127.5f, 128.0f))
        .build()
    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        // TensorFlow Lite requires the ARGB_8888 config. We create a copy only if necessary
        val needsCopy = faceBitmap.config != Bitmap.Config.ARGB_8888
        val bmp = if (needsCopy) faceBitmap.copy(Bitmap.Config.ARGB_8888, false) else faceBitmap

        try {
            // Convert Android Bitmap into a TensorImage for processing
            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bmp)
            tensorImage = imageProcessor.process(tensorImage)

            // Write the processed image data into the model's input buffer
            inputBuffers[0].writeFloat(tensorImage.tensorBuffer.floatArray)
            model.run(inputBuffers, outputBuffers)

            // Read the resulting 1D array (the embedding vector) from the output buffer
            return outputBuffers[0].readFloat()
        } finally {
            // Prevent memory leaks by recycling the temporary bitmap
            if (needsCopy) {
                bmp.recycle()
            }
        }
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        // v_normalized = v / ||v||
        var sum = 0f
        for (v in vector) {
            sum += v * v
        }

        val norm = sqrt(sum)

        // Prevent division by zero if an empty or zero-vector is passed
        if (norm < 1e-10f) return vector.copyOf()

        // Divide each element by the magnitude
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }

    fun averageEmbeddings(list: List<FloatArray>): FloatArray {
        val size = list.first().size
        val avg = FloatArray(size)

        // Sum all corresponding elements across the list of vectors
        for (emb in list) {
            for (i in 0 until size) {
                avg[i] += emb[i]
            }
        }

        // Divide by the number of vectors to get the mean
        for (i in 0 until size) {
            avg[i] /= list.size
        }
        return avg
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        // Standard Cosine Similarity formula: cos(θ) = (A • B) / (||A|| * ||B||)
        // Because we strictly use L2-normalized vectors, ||A|| = 1 and ||B|| = 1
        // So dot product: A • B
        var dot = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return dot
    }

    override fun close() {
        model.close()
    }
}
