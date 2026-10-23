package com.example.ecommerce.search.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch.core.SearchResponse
import co.elastic.clients.elasticsearch.core.explain.ExplanationDetail
import co.elastic.clients.elasticsearch.core.search.Hit
import com.example.ecommerce.search.model.Product

class SearchService(private val client: ElasticsearchClient) {

    fun searchByText(indexName: String, field: String, textQuery: String): List<Hit<Product>> {
        val response = client.search({ s ->
            s.index(indexName)
                .query { q ->
                    q.match { m ->
                        m.field(field).query(textQuery)
                    }
                }
        }, Product::class.java)
        return response.hits().hits()
    }

    fun searchByExactCategory(indexName: String, category: String): List<Hit<Product>> {
        val response = client.search({ s ->
            s.index(indexName)
                .query { q ->
                    q.term { t ->
                        t.field("category").value(category)
                    }
                }
        }, Product::class.java)
        return response.hits().hits()
    }

    fun searchCompoundBool(indexName: String, keyword: String, minPrice: Double, maxPrice: Double): List<Hit<Product>> {
        val response = client.search({ s ->
            s.index(indexName)
                .query { q ->
                    q.bool { b ->
                        b.must { m ->
                            m.multiMatch { mm ->
                                mm.fields("name", "description").query(keyword)
                            }
                        }.filter { f ->
                            f.range { r ->
                                r.number { n ->
                                    n.field("price").gte(minPrice).lte(maxPrice)
                                }
                            }
                        }.filter { f ->
                            f.term { t -> t.field("inStock").value(true) }
                        }.should { sh ->
                            sh.range { r ->
                                r.number { n ->
                                    n.field("rating").gte(4.5).boost(2.0f)
                                }
                            }
                        }
                    }
                }
                .sort { sort -> sort.score { sc -> sc.order(SortOrder.Desc) } }
        }, Product::class.java)
        return response.hits().hits()
    }

    fun searchFuzzy(indexName: String, field: String, misspelledQuery: String): List<Hit<Product>> {
        val response = client.search({ s ->
            s.index(indexName)
                .query { q ->
                    q.fuzzy { f ->
                        f.field(field).value(misspelledQuery).fuzziness("AUTO")
                    }
                }
        }, Product::class.java)
        return response.hits().hits()
    }

    fun searchMatchPhrase(indexName: String, field: String, phrase: String): List<Hit<Product>> {
        val response = client.search({ s ->
            s.index(indexName)
                .query { q ->
                    q.matchPhrase { mp ->
                        mp.field(field).query(phrase).slop(1)
                    }
                }
        }, Product::class.java)
        return response.hits().hits()
    }

    fun searchWithHighlighting(indexName: String, queryText: String): SearchResponse<Product> {
        return client.search({ s ->
            s.index(indexName)
                .query { q ->
                    q.multiMatch { mm ->
                        mm.fields("name", "description").query(queryText)
                    }
                }
                .highlight { h ->
                    h.fields("name") { hf -> hf }
                        .fields("description") { hf ->
                            hf.preTags("<mark>").postTags("</mark>")
                        }
                }
        }, Product::class.java)
    }

    fun searchBm25(indexName: String, textQuery: String): List<Hit<Product>> {
        val response = client.search({ s ->
            s.index(indexName)
                .query { q ->
                    q.multiMatch { mm ->
                        mm.fields("name^3", "description").query(textQuery)
                    }
                }
        }, Product::class.java)
        return response.hits().hits()
    }

    fun explainBm25Score(indexName: String, docId: String, queryTerm: String) {
        val response = client.explain({ e ->
            e.index(indexName)
                .id(docId)
                .query { q ->
                    q.match { m -> m.field("description").query(queryTerm) }
                }
        }, Product::class.java)

        if (response.matched() && response.explanation() != null) {
            println("Explain BM25 for Doc [$docId] matching '$queryTerm':")
            printExplanationTree(response.explanation()!!, 0)
        } else {
            println("Doc [$docId] does NOT match '$queryTerm'")
        }
    }

    private fun printExplanationTree(detail: ExplanationDetail, depth: Int) {
        if (depth > 2) return
        val indent = "  ".repeat(depth + 1)
        System.out.printf("%s[Score: %.4f] %s%n", indent, detail.value(), detail.description())
        for (child in detail.details()) {
            printExplanationTree(child, depth + 1)
        }
    }

    fun searchKnnVector(indexName: String, queryVector: List<Float>, topK: Int): List<Hit<Product>> {
        val response = client.search({ s ->
            s.index(indexName)
                .knn { k ->
                    k.field("embedding")
                        .queryVector(queryVector)
                        .k(topK)
                        .numCandidates(50)
                }
        }, Product::class.java)
        return response.hits().hits()
    }

    fun searchHybrid(indexName: String, textQuery: String, queryVector: List<Float>, topK: Int): List<Hit<Product>> {
        val response = client.search({ s ->
            s.index(indexName)
                .query { q ->
                    q.multiMatch { mm ->
                        mm.fields("name^2", "description").query(textQuery)
                    }
                }
                .knn { k ->
                    k.field("embedding")
                        .queryVector(queryVector)
                        .k(topK)
                        .numCandidates(50)
                        .boost(1.2f)
                }
        }, Product::class.java)
        return response.hits().hits()
    }
}
