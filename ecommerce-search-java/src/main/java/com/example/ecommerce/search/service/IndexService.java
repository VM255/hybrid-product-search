package com.example.ecommerce.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;

import java.io.IOException;
import java.util.Map;

/**
 * Modern Java 21 Index Lifecycle Service.
 *
 * Demonstrates:
 * - Functional Map.ofEntries for explicit schema mapping
 * - Custom BM25 similarity settings configuration (k1, b)
 * - Dense vector HNSW index configuration for k-NN vector search
 */
public final class IndexService {

    private final ElasticsearchClient client;

    public IndexService(ElasticsearchClient client) {
        this.client = client;
    }

    public boolean indexExists(String indexName) throws IOException {
        return client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
    }

    public boolean deleteIndex(String indexName) throws IOException {
        if (indexExists(indexName)) {
            var response = client.indices().delete(d -> d.index(indexName));
            return response.acknowledged();
        }
        return false;
    }

    public boolean createProductIndex(String indexName) throws IOException {
        if (indexExists(indexName)) {
            System.out.printf("Index '%s' already exists. Deleting it for a clean start...%n", indexName);
            deleteIndex(indexName);
        }

        // Functional, immutable mapping definition
        final var properties = Map.of(
                "name", Property.of(p -> p.text(t -> t
                        .similarity("tuned_bm25")
                        .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))
                )),
                "description", Property.of(p -> p.text(t -> t.similarity("tuned_bm25"))),
                "category", Property.of(p -> p.keyword(k -> k)),
                "tags", Property.of(p -> p.keyword(k -> k)),
                "price", Property.of(p -> p.double_(d -> d)),
                "rating", Property.of(p -> p.double_(d -> d)),
                "inStock", Property.of(p -> p.boolean_(b -> b)),
                "createdDate", Property.of(p -> p.date(d -> d)),
                "embedding", Property.of(p -> p.denseVector(dv -> dv
                        .dims(EmbeddingService.VECTOR_DIMENSIONS)
                        .index(true)
                        .similarity("cosine")
                ))
        );

        final var response = client.indices().create(c -> c
                .index(indexName)
                .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                        .similarity("tuned_bm25", sim -> sim
                                .bm25(bm -> bm
                                        .k1(1.2)
                                        .b(0.75)
                                        .discountOverlaps(true)
                                )
                        )
                )
                .mappings(m -> m.properties(properties))
        );

        return response.acknowledged();
    }
}
