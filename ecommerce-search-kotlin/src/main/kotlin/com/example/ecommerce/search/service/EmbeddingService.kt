package com.example.ecommerce.search.service

import java.util.Locale
import kotlin.math.sqrt

/**
 * Kotlin implementation of the 8D semantic vector encoder.
 */
class EmbeddingService {

    companion object {
        const val VECTOR_DIMENSIONS = 8
    }

    fun generateEmbedding(text: String?): List<Float> {
        val vector = FloatArray(VECTOR_DIMENSIONS)
        if (text.isNullOrBlank()) {
            return normalize(vector)
        }

        val lower = text.lowercase(Locale.ROOT)

        // [0] Computing / Laptop
        if (lower.containsAny("macbook", "laptop", "dell", "intel", "m3", "workstation", "ssd", "memory", "processor", "notebook")) {
            vector[0] += 0.85f
        }

        // [1] Audio / Music
        if (lower.containsAny("audio", "sound", "headphone", "earbud", "airpod", "noise", "cancelling", "acoustic", "music", "clarity", "spatial")) {
            vector[1] += 0.90f
        }

        // [2] Gaming
        if (lower.containsAny("gaming", "keyboard", "rgb", "cherry", "switches", "polling", "mechanical", "gamer", "fps")) {
            vector[2] += 0.90f
        }

        // [3] Smartphone / Mobile
        if (lower.containsAny("smartphone", "phone", "galaxy", "pixel", "android", "camera", "titanium", "cellular", "ios")) {
            vector[3] += 0.88f
        }

        // [4] Portability / Battery
        if (lower.containsAny("battery", "portable", "charger", "power", "travel", "waterproof", "powerbank", "compact", "lightweight")) {
            vector[4] += 0.80f
        }

        // [5] Reading / E-Readers
        if (lower.containsAny("reading", "read", "book", "kindle", "glare", "paperwhite", "e-reader", "ebook")) {
            vector[5] += 0.92f
        }

        // [6] Productivity / Office
        if (lower.containsAny("mouse", "ergonomic", "scrolling", "dpi", "office", "desk", "logitech", "multitasking", "work")) {
            vector[6] += 0.85f
        }

        // [7] Premium / Flagship
        if (lower.containsAny("pro", "max", "ultra", "oled", "flagship", "retina", "liquid", "luxury", "titanium", "premium")) {
            vector[7] += 0.75f
        }

        return normalize(vector)
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }

    private fun normalize(raw: FloatArray): List<Float> {
        val sumSq = raw.sumOf { (it * it).toDouble() }
        val norm = sqrt(sumSq)

        if (norm < 1e-6) {
            val uniform = (1.0 / sqrt(VECTOR_DIMENSIONS.toDouble())).toFloat()
            return List(VECTOR_DIMENSIONS) { uniform }
        }

        return raw.map { (it / norm).toFloat() }
    }
}
