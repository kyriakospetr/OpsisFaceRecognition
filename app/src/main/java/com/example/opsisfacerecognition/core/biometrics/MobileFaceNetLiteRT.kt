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

class MobileFaceNetLiteRT @Inject constructor(
    @ApplicationContext context: Context
) : AutoCloseable {
    private val model: CompiledModel = CompiledModel.create(
        context.assets,
        "mobilefacenet.tflite",
        CompiledModel.Options(Accelerator.CPU)
    )
    private val inputBuffers = model.createInputBuffers()
    private val outputBuffers = model.createOutputBuffers()

    // Model expects a 112x112
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(112, 112, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(127.5f, 128.0f))
        .build()
    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        val bmp = if (faceBitmap.config == Bitmap.Config.ARGB_8888) faceBitmap
                  else faceBitmap.copy(Bitmap.Config.ARGB_8888, false)

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bmp)
        tensorImage = imageProcessor.process(tensorImage)

        inputBuffers[0].writeFloat(tensorImage.tensorBuffer.floatArray)
        model.run(inputBuffers, outputBuffers)

        val embedding = outputBuffers[0].readFloat()

        return embedding
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0f
        for (v in vector) {
            sum += v * v
        }

        val norm = kotlin.math.sqrt(sum)
        if (norm < 1e-10f) return vector

        for (i in vector.indices) {
            vector[i] /= norm
        }
        return vector
    }

    override fun close() {
        model.close()
    }

    fun averageEmbeddings(list: List<FloatArray>): FloatArray {
        val size = list.first().size
        val avg = FloatArray(size)

        for (emb in list) {
            for (i in 0 until size) {
                avg[i] += emb[i]
            }
        }

        for (i in 0 until size) {
            avg[i] /= list.size
        }
        return avg
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        // We use dot because both the embeddings are L2 normalized
        var dot = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return dot
    }
}
