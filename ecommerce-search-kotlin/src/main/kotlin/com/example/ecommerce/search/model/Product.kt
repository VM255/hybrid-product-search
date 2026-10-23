package com.example.ecommerce.search.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Product domain entity implemented as an idiomatic Kotlin data class.
 *
 * Demonstrates:
 * - Default arguments for clean construction and Jackson deserialization
 * - Immutability / properties with automatic equals(), hashCode(), copy()
 * - Dense vector embedding integration for k-NN search
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Product(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var category: String = "",
    var tags: List<String> = emptyList(),
    var price: Double = 0.0,
    var rating: Double = 0.0,
    var inStock: Boolean = true,
    var createdDate: String = "",
    var embedding: List<Float>? = null
) {
    override fun toString(): String =
        "[$id] $name | Category: $category | Price: $${"%.2f".format(price)} | Rating: $rating | InStock: $inStock"
}
