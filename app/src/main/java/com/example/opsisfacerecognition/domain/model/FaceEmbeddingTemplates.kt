package com.example.opsisfacerecognition.domain.model

class FaceEmbeddingTemplates(
    val vectors: List<FloatArray>,
    val labels: List<String> = List(vectors.size) { "template_$it" }
) {
    val embeddingSize: Int = vectors.firstOrNull()?.size ?: 0

    init {
        require(vectors.isNotEmpty()) { "At least one embedding template is required." }
        require(labels.size == vectors.size) { "Every embedding template needs one label." }
        require(embeddingSize > 0) { "Embedding templates cannot be empty." }
        require(vectors.all { it.size == embeddingSize }) {
            "Every embedding template must have the same dimension."
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceEmbeddingTemplates) return false
        if (vectors.size != other.vectors.size) return false
        if (labels != other.labels) return false
        return vectors.indices.all { vectors[it].contentEquals(other.vectors[it]) }
    }

    override fun hashCode(): Int {
        var result = labels.hashCode()
        for (vector in vectors) {
            result = 31 * result + vector.contentHashCode()
        }
        return result
    }

    companion object {
        val EXPOSURE_TEMPLATE_LABELS = listOf(
            "original",
            "exposure_norm",
            "gamma_dark",
            "luma_equalized"
        )
    }
}
