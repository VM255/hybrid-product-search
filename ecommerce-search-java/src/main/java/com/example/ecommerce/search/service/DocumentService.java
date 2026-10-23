package com.example.ecommerce.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.example.ecommerce.search.model.Product;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Functional Document Service for Elasticsearch CRUD & Bulk operations.
 */
public final class DocumentService {

    private final ElasticsearchClient client;

    public DocumentService(ElasticsearchClient client) {
        this.client = client;
    }

    public String indexProduct(String indexName, Product product) throws IOException {
        var response = client.index(i -> i
                .index(indexName)
                .id(product.id())
                .document(product)
                .refresh(Refresh.True)
        );
        return response.result().jsonValue();
    }

    public int bulkIndexProducts(String indexName, List<Product> products) throws IOException {
        var br = new BulkRequest.Builder();

        products.forEach(product -> br.operations(op -> op
                .index(idx -> idx
                        .index(indexName)
                        .id(product.id())
                        .document(product)
                )
        ));

        br.refresh(Refresh.True);
        var result = client.bulk(br.build());

        if (result.errors()) {
            System.err.println("Bulk indexing had errors!");
            result.items().stream()
                    .filter(item -> item.error() != null)
                    .forEach(item -> System.err.printf("Error on doc %s: %s%n", item.id(), item.error().reason()));
        }

        return result.items().size();
    }

    public Optional<Product> getProductById(String indexName, String id) throws IOException {
        var response = client.get(g -> g.index(indexName).id(id), Product.class);
        return response.found() && response.source() != null
                ? Optional.of(response.source())
                : Optional.empty();
    }

    public boolean updateProductPrice(String indexName, String id, Double newPrice, Boolean inStock) throws IOException {
        var docUpdates = new HashMap<String, Object>();
        docUpdates.put("price", newPrice);
        Optional.ofNullable(inStock).ifPresent(stock -> docUpdates.put("inStock", stock));

        var response = client.update(u -> u
                .index(indexName)
                .id(id)
                .doc(docUpdates)
                .refresh(Refresh.True),
                Product.class
        );

        return "updated".equalsIgnoreCase(response.result().jsonValue());
    }

    public boolean deleteProduct(String indexName, String id) throws IOException {
        var response = client.delete(d -> d.index(indexName).id(id).refresh(Refresh.True));
        return "deleted".equalsIgnoreCase(response.result().jsonValue());
    }
}
