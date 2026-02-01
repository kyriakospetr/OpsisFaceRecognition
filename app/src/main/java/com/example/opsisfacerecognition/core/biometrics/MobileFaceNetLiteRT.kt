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

        return outputBuffers[0].readFloat()
    }

    override fun close() {
        model.close()
    }
}
