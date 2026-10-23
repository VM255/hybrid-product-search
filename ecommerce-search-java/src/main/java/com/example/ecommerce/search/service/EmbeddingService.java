package com.example.ecommerce.search.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Functional 8-Dimensional Semantic Vector Encoder.
 *
 * Demonstrates:
 * - Functional predicate composition
 * - Stream-based vector normalization with DoubleStream and IntStream
 * - Immutable collection mapping with Stream::toList
 */
public final class EmbeddingService {

    public static final int VECTOR_DIMENSIONS = 8;

    private record SemanticFeature(int index, float weight, List<String> keywords) {
        boolean matches(String text) {
            return keywords.stream().anyMatch(text::contains);
        }
    }

    private static final List<SemanticFeature> FEATURES = List.of(
            new SemanticFeature(0, 0.85f, List.of("macbook", "laptop", "dell", "intel", "m3", "workstation", "ssd", "memory", "processor", "notebook")),
            new SemanticFeature(1, 0.90f, List.of("audio", "sound", "headphone", "earbud", "airpod", "noise", "cancelling", "acoustic", "music", "clarity", "spatial")),
            new SemanticFeature(2, 0.90f, List.of("gaming", "keyboard", "rgb", "cherry", "switches", "polling", "mechanical", "gamer", "fps")),
            new SemanticFeature(3, 0.88f, List.of("smartphone", "phone", "galaxy", "pixel", "android", "camera", "titanium", "cellular", "ios")),
            new SemanticFeature(4, 0.80f, List.of("battery", "portable", "charger", "power", "travel", "waterproof", "powerbank", "compact", "lightweight")),
            new SemanticFeature(5, 0.92f, List.of("reading", "read", "book", "kindle", "glare", "paperwhite", "e-reader", "ebook")),
            new SemanticFeature(6, 0.85f, List.of("mouse", "ergonomic", "scrolling", "dpi", "office", "desk", "logitech", "multitasking", "work")),
            new SemanticFeature(7, 0.75f, List.of("pro", "max", "ultra", "oled", "flagship", "retina", "liquid", "luxury", "titanium", "premium"))
    );

    /**
     * Encodes text into a normalized 8D dense vector using functional pipelines.
     */
    public List<Float> generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return uniformVector();
        }

        final var lower = text.toLowerCase(Locale.ROOT);
        final var raw = new double[VECTOR_DIMENSIONS];

        FEATURES.stream()
                .filter(feature -> feature.matches(lower))
                .forEach(feature -> raw[feature.index()] += feature.weight());

        final var norm = Math.sqrt(DoubleStream.of(raw).map(v -> v * v).sum());

        if (norm < 1e-6) {
            return uniformVector();
        }

        return DoubleStream.of(raw)
                .map(v -> v / norm)
                .mapToObj(v -> (float) v)
                .toList();
    }

    private List<Float> uniformVector() {
        final var val = (float) (1.0 / Math.sqrt(VECTOR_DIMENSIONS));
        return IntStream.range(0, VECTOR_DIMENSIONS)
                .mapToObj(i -> val)
                .toList();
    }
}
