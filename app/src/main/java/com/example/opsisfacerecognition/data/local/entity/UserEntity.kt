package com.example.opsisfacerecognition.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.opsisfacerecognition.domain.model.FaceEmbeddingTemplates
import com.example.opsisfacerecognition.domain.model.User
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val userId: String,
    val fullName: String,
    val embeddingTemplates: FaceEmbeddingTemplates
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserEntity

        if (localId != other.localId) return false
        if (userId != other.userId) return false
        if (fullName != other.fullName) return false
        if (embeddingTemplates != other.embeddingTemplates) return false

        return true
    }

    override fun hashCode(): Int {
        var result = localId.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + fullName.hashCode()
        result = 31 * result + embeddingTemplates.hashCode()
        return result
    }
}

fun User.toEntity() = UserEntity(
    userId = id,
    fullName = fullName,
    embeddingTemplates = embeddingTemplates
)

fun UserEntity.toUser() = User(
    id = userId,
    fullName = fullName,
    embeddingTemplates = embeddingTemplates
)

// Room DB can't store embedding template objects directly.
// We need a converter both ways
class EmbeddingConverters {
    companion object {
        private const val INT_BYTES = 4
        private const val FLOAT_BYTES = 4
    }

    @TypeConverter
    fun embeddingTemplatesToByteArray(value: FaceEmbeddingTemplates?): ByteArray? {
        if (value == null) return null
        val templateCount = value.vectors.size
        val embeddingSize = value.embeddingSize
        val bb = ByteBuffer.allocate(INT_BYTES + INT_BYTES + templateCount * embeddingSize * FLOAT_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(templateCount)
        bb.putInt(embeddingSize)
        value.vectors.forEach { template ->
            template.forEach { bb.putFloat(it) }
        }
        return bb.array()
    }

    @TypeConverter
    fun byteArrayToEmbeddingTemplates(value: ByteArray?): FaceEmbeddingTemplates? {
        if (value == null) return null
        val bb = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
        val templateCount = bb.int
        val embeddingSize = bb.int
        require(templateCount > 0) { "Embedding template count must be positive." }
        require(embeddingSize > 0) { "Embedding size must be positive." }
        require(bb.remaining() == templateCount * embeddingSize * FLOAT_BYTES) {
            "Invalid embedding template payload size."
        }
        val templates = List(templateCount) {
            FloatArray(embeddingSize) { bb.getFloat() }
        }
        val labels = if (templateCount == FaceEmbeddingTemplates.EXPOSURE_TEMPLATE_LABELS.size) {
            FaceEmbeddingTemplates.EXPOSURE_TEMPLATE_LABELS
        } else {
            List(templateCount) { "template_$it" }
        }
        return FaceEmbeddingTemplates(templates, labels)
    }
}
