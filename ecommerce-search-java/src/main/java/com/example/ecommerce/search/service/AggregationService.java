package com.example.ecommerce.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import com.example.ecommerce.search.model.Product;

import java.io.IOException;
import java.util.Optional;

/**
 * Functional Aggregation Service using Java 21 features.
 */
public final class AggregationService {

    private final ElasticsearchClient client;

    public AggregationService(ElasticsearchClient client) {
        this.client = client;
    }

    public void printPriceStats(String indexName) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .size(0)
                .aggregations("price_stats", a -> a.stats(st -> st.field("price"))),
                Product.class
        );

        Optional.ofNullable(response.aggregations().get("price_stats"))
                .filter(Aggregate::isStats)
                .map(Aggregate::stats)
                .ifPresent(stats -> {
                    System.out.println("\n--- [Metric Aggregation: Price Stats] ---");
                    System.out.printf("Count: %d | Min: $%.2f | Max: $%.2f | Avg: $%.2f | Sum: $%.2f%n",
                            stats.count(), stats.min(), stats.max(), stats.avg(), stats.sum());
                });
    }

    public void printCategoryDistribution(String indexName) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .size(0)
                .aggregations("by_category", a -> a.terms(t -> t.field("category").size(10))),
                Product.class
        );

        Optional.ofNullable(response.aggregations().get("by_category"))
                .filter(Aggregate::isSterms)
                .map(Aggregate::sterms)
                .ifPresent(sterms -> {
                    System.out.println("\n--- [Bucket Aggregation: Category Distribution] ---");
                    sterms.buckets().array().forEach(bucket ->
                            System.out.printf("Category: %-15s -> Count: %d products%n", bucket.key().stringValue(), bucket.docCount())
                    );
                });
    }

    public void printCategoryWithAveragePrice(String indexName) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .size(0)
                .aggregations("categories", a -> a
                        .terms(t -> t.field("category"))
                        .aggregations("avg_price", sub -> sub.avg(av -> av.field("price")))
                ),
                Product.class
        );

        Optional.ofNullable(response.aggregations().get("categories"))
                .filter(Aggregate::isSterms)
                .map(Aggregate::sterms)
                .ifPresent(categories -> {
                    System.out.println("\n--- [Sub-Aggregation: Average Price Per Category] ---");
                    for (StringTermsBucket bucket : categories.buckets().array()) {
                        var avgPrice = Optional.ofNullable(bucket.aggregations().get("avg_price"))
                                .filter(Aggregate::isAvg)
                                .map(a -> a.avg().value())
                                .orElse(0.0);
                        System.out.printf("Category: %-15s | Count: %2d | Avg Price: $%.2f%n",
                                bucket.key().stringValue(), bucket.docCount(), avgPrice);
                    }
                });
    }

    public void printPriceTierDistribution(String indexName) throws IOException {
        var response = client.search(s -> s
                .index(indexName)
                .size(0)
                .aggregations("price_tiers", a -> a
                        .range(r -> r
                                .field("price")
                                .ranges(
                                        AggregationRange.of(rg -> rg.key("Budget (<$100)").to(100.0)),
                                        AggregationRange.of(rg -> rg.key("Mid-range ($100-$500)").from(100.0).to(500.0)),
                                        AggregationRange.of(rg -> rg.key("Premium (>$500)").from(500.0))
                                )
                        )
                ),
                Product.class
        );

        Optional.ofNullable(response.aggregations().get("price_tiers"))
                .filter(Aggregate::isRange)
                .map(Aggregate::range)
                .ifPresent(priceTiers -> {
                    System.out.println("\n--- [Bucket Aggregation: Price Tiers] ---");
                    priceTiers.buckets().array().forEach(bucket ->
                            System.out.printf("Tier: %-22s -> %d products%n", bucket.key(), bucket.docCount())
                    );
                });
    }
}
