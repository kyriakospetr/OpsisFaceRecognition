package com.example.opsisfacerecognition.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.opsisfacerecognition.domain.model.User
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val userId: String,
    val fullName: String,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserEntity

        if (localId != other.localId) return false
        if (userId != other.userId) return false
        if (fullName != other.fullName) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = localId.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + fullName.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

fun User.toEntity() = UserEntity(
    userId = id,
    fullName = fullName,
    embedding = embedding
)

fun UserEntity.toUser() = User(
    id = userId,
    fullName = fullName,
    embedding = embedding
)

// Room DB can't store Float Array
// We need a converter both ways
class EmbeddingConverters {
    @TypeConverter
    fun floatArrayToByteArray(value: FloatArray?): ByteArray? {
        if (value == null) return null
        val bb = ByteBuffer.allocate(value.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        value.forEach { bb.putFloat(it) }
        return bb.array()
    }

    @TypeConverter
    fun byteArrayToFloatArray(value: ByteArray?): FloatArray? {
        if (value == null) return null
        val bb = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(value.size / 4)
        for (i in floats.indices) floats[i] = bb.getFloat()
        return floats
    }
}