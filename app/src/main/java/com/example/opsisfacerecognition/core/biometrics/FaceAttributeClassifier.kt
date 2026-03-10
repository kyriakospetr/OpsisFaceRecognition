package com.example.opsisfacerecognition.core.biometrics

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import javax.inject.Inject
import javax.inject.Singleton

data class FaceAttributeResult(val hasGlasses: Boolean, val hasHat: Boolean)

@Singleton
class FaceAttributeClassifier @Inject constructor(
    @ApplicationContext context: Context
) : AutoCloseable {

    companion object {
        //If the probability is higher than the threshold, we assume the person wears glasses or hat
        private const val GLASSES_THRESHOLD = 0.6f
        private const val HAT_THRESHOLD = 0.6f
        const val MODEL_INPUT_SIZE = 96 // MobileNetV2 Model is trained on 96x96
    }

    private val model: CompiledModel = runCatching {
        CompiledModel.create(context.assets, "face_attributes.tflite", CompiledModel.Options(Accelerator.GPU))
    }.getOrElse {
        CompiledModel.create(context.assets, "face_attributes.tflite", CompiledModel.Options(Accelerator.CPU))
    }

    // Buffers for model's input/output (performance)
    private val inputBuffers = model.createInputBuffers()
    private val outputBuffers = model.createOutputBuffers()

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f)) // Normalize pixels [0,255] -> [0,1]
        .build()

    fun classify(faceBitmap: Bitmap): FaceAttributeResult {
        val m = model
        val ib = inputBuffers
        val ob = outputBuffers

        // Verify bitmap is ARGB_8888 format
        val bmp = if (faceBitmap.config == Bitmap.Config.ARGB_8888) faceBitmap
                  else faceBitmap.copy(Bitmap.Config.ARGB_8888, false)

        // Load bitmap to Tensor Image, resize + normalize
        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bmp)
        tensorImage = imageProcessor.process(tensorImage)

        // Run the model
        ib[0].writeFloat(tensorImage.tensorBuffer.floatArray)
        m.run(ib, ob)

        // Model trained with sigmoid activation so the values are at [0,1]
        val output = ob[0].readFloat()

        // index 0 = glasses probability, index 1 = hat probability
        val glassesProb = if (output.isNotEmpty()) output[0] else 0f
        val hatProb = if (output.size > 1) output[1] else 0f

        return FaceAttributeResult(
            hasGlasses = glassesProb >= GLASSES_THRESHOLD,
            hasHat = hatProb >= HAT_THRESHOLD
        )
    }

    override fun close() {
        model.close()
    }
}
