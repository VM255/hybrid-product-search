package com.example.ecommerce.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.explain.ExplanationDetail;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.ecommerce.search.model.Product;

import java.io.IOException;
import java.util.List;

/**
 * Functional Search Service demonstrating Elasticsearch Query DSL,
 * BM25 Relevance Scoring, Approximate k-NN Vector Search, and Hybrid Search.
 */
public final class SearchService {

    private final ElasticsearchClient client;

    public SearchService(ElasticsearchClient client) {
        this.client = client;
    }

    public List<Hit<Product>> searchByText(String indexName, String field, String textQuery) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .query(q -> q.match(m -> m.field(field).query(textQuery))),
                Product.class
        );
        return response.hits().hits();
    }

    public List<Hit<Product>> searchByExactCategory(String indexName, String category) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .query(q -> q.term(t -> t.field("category").value(category))),
                Product.class
        );
        return response.hits().hits();
    }

    public List<Hit<Product>> searchCompoundBool(String indexName, String keyword, double minPrice, double maxPrice) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .query(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm.fields("name", "description").query(keyword)))
                        .filter(f -> f.range(r -> r.number(n -> n.field("price").gte(minPrice).lte(maxPrice))))
                        .filter(f -> f.term(t -> t.field("inStock").value(true)))
                        .should(sh -> sh.range(r -> r.number(n -> n.field("rating").gte(4.5).boost(2.0f))))
                ))
                .sort(sort -> sort.score(sc -> sc.order(SortOrder.Desc))),
                Product.class
        );
        return response.hits().hits();
    }

    public List<Hit<Product>> searchFuzzy(String indexName, String field, String misspelledQuery) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .query(q -> q.fuzzy(f -> f.field(field).value(misspelledQuery).fuzziness("AUTO"))),
                Product.class
        );
        return response.hits().hits();
    }

    public List<Hit<Product>> searchMatchPhrase(String indexName, String field, String phrase) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .query(q -> q.matchPhrase(mp -> mp.field(field).query(phrase).slop(1))),
                Product.class
        );
        return response.hits().hits();
    }

    public SearchResponse<Product> searchWithHighlighting(String indexName, String queryText) throws IOException {
        return client.search(s -> s
                .index(indexName)
                .query(q -> q.multiMatch(mm -> mm.fields("name", "description").query(queryText)))
                .highlight(h -> h
                        .fields("name", hf -> hf)
                        .fields("description", hf -> hf.preTags("<mark>").postTags("</mark>"))
                ),
                Product.class
        );
    }

    public List<Hit<Product>> searchBm25(String indexName, String textQuery) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .query(q -> q.multiMatch(mm -> mm.fields("name^3", "description").query(textQuery))),
                Product.class
        );
        return response.hits().hits();
    }

    public void explainBm25Score(String indexName, String docId, String queryTerm) throws IOException {
        var response = client.explain(e -> e
                .index(indexName)
                .id(docId)
                .query(q -> q.match(m -> m.field("description").query(queryTerm))),
                Product.class
        );

        if (response.matched() && response.explanation() != null) {
            System.out.printf("Explain BM25 for Doc [%s] matching '%s':%n", docId, queryTerm);
            printExplanation(response.explanation(), 0);
        } else {
            System.out.printf("Doc [%s] does NOT match '%s'%n", docId, queryTerm);
        }
    }

    private void printExplanation(ExplanationDetail detail, int depth) {
        if (depth > 2) return;
        var indent = "  ".repeat(depth + 1);
        System.out.printf("%s[Score: %.4f] %s%n", indent, detail.value(), detail.description());
        detail.details().forEach(child -> printExplanation(child, depth + 1));
    }

    public List<Hit<Product>> searchKnnVector(String indexName, List<Float> queryVector, int topK) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .knn(k -> k
                        .field("embedding")
                        .queryVector(queryVector)
                        .k(topK)
                        .numCandidates(50)
                ),
                Product.class
        );
        return response.hits().hits();
    }

    public List<Hit<Product>> searchHybrid(String indexName, String textQuery, List<Float> queryVector, int topK) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .query(q -> q.multiMatch(mm -> mm.fields("name^2", "description").query(textQuery)))
                .knn(k -> k
                        .field("embedding")
                        .queryVector(queryVector)
                        .k(topK)
                        .numCandidates(50)
                        .boost(1.2f)
                ),
                Product.class
        );
        return response.hits().hits();
    }
}
