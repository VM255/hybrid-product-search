package com.example.ecommerce.search.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Modern Java 21 Record representing an immutable E-Commerce Product entity.
 *
 * Java 21 Features:
 * - Immutable Record component architecture
 * - Compact constructor with defensive copies & null-safety
 * - Functional wither methods for non-destructive updates
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Product(
        String id,
        String name,
        String description,
        String category,
        List<String> tags,
        Double price,
        Double rating,
        Boolean inStock,
        String createdDate,
        List<Float> embedding
) {
    /**
     * Compact constructor providing defensive copying and immutable defaults.
     */
    public Product {
        tags = tags == null ? List.of() : List.copyOf(tags);
        embedding = embedding == null ? List.of() : List.copyOf(embedding);
        price = price == null ? 0.0 : price;
        rating = rating == null ? 0.0 : rating;
        inStock = inStock == null ? true : inStock;
    }

    /**
     * Convenience constructor without embedding.
     */
    public Product(String id, String name, String description, String category,
                   List<String> tags, Double price, Double rating, Boolean inStock, String createdDate) {
        this(id, name, description, category, tags, price, rating, inStock, createdDate, List.of());
    }

    /**
     * Functional wither for price and inStock (returns a new immutable instance).
     */
    public Product withPriceAndStock(Double newPrice, Boolean newInStock) {
        return new Product(
                this.id,
                this.name,
                this.description,
                this.category,
                this.tags,
                newPrice != null ? newPrice : this.price,
                this.rating,
                newInStock != null ? newInStock : this.inStock,
                this.createdDate,
                this.embedding
        );
    }

    /**
     * Functional wither for attaching a dense vector embedding.
     */
    public Product withEmbedding(List<Float> newEmbedding) {
        return new Product(
                this.id,
                this.name,
                this.description,
                this.category,
                this.tags,
                this.price,
                this.rating,
                this.inStock,
                this.createdDate,
                newEmbedding
        );
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | Category: %s | Price: $%.2f | Rating: %.1f | InStock: %s",
                id, name, category, price, rating, inStock);
    }
}
