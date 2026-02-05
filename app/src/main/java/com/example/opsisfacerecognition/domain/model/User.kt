package com.example.opsisfacerecognition.domain.model

import java.util.UUID

data class User(
    val id: String = UUID.randomUUID().toString(),
    val fullName: String,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false

        if (id != other.id) return false
        if (fullName != other.fullName) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + fullName.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}