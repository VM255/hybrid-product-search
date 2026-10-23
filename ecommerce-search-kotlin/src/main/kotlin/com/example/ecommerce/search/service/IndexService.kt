package com.example.ecommerce.search.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.mapping.Property
import co.elastic.clients.elasticsearch.indices.ExistsRequest

class IndexService(private val client: ElasticsearchClient) {

    fun indexExists(indexName: String): Boolean =
        client.indices().exists(ExistsRequest.of { it.index(indexName) }).value()

    fun deleteIndex(indexName: String): Boolean {
        if (indexExists(indexName)) {
            val response = client.indices().delete { it.index(indexName) }
            return response.acknowledged()
        }
        return false
    }

    fun createProductIndex(indexName: String): Boolean {
        if (indexExists(indexName)) {
            println("Index '$indexName' already exists. Deleting it for a clean start...")
            deleteIndex(indexName)
        }

        val properties = mutableMapOf<String, Property>()

        // Multi-field: text for search with custom BM25 similarity, keyword subfield for sorting/aggregations
        properties["name"] = Property.of { p ->
            p.text { t ->
                t.similarity("tuned_bm25")
                    .fields("keyword") { f -> f.keyword { k -> k.ignoreAbove(256) } }
            }
        }

        // Full-text analyzed field with custom BM25 similarity
        properties["description"] = Property.of { p ->
            p.text { t -> t.similarity("tuned_bm25") }
        }

        // Keyword field for exact filters and aggregations
        properties["category"] = Property.of { p -> p.keyword { it } }
        properties["tags"] = Property.of { p -> p.keyword { it } }

        // Numeric & boolean types
        properties["price"] = Property.of { p -> p.double_ { it } }
        properties["rating"] = Property.of { p -> p.double_ { it } }
        properties["inStock"] = Property.of { p -> p.boolean_ { it } }

        // Date field
        properties["createdDate"] = Property.of { p -> p.date { it } }

        // Dense Vector property for Approximate k-NN Vector Search
        properties["embedding"] = Property.of { p ->
            p.denseVector { dv ->
                dv.dims(EmbeddingService.VECTOR_DIMENSIONS)
                    .index(true)
                    .similarity("cosine")
            }
        }

        val response = client.indices().create { c ->
            c.index(indexName)
                .settings { s ->
                    s.numberOfShards("1")
                        .numberOfReplicas("0")
                        .similarity("tuned_bm25") { sim ->
                            sim.bm25 { bm ->
                                bm.k1(1.2)
                                    .b(0.75)
                                    .discountOverlaps(true)
                            }
                        }
                }
                .mappings { m -> m.properties(properties) }
        }

        return response.acknowledged()
    }
}
