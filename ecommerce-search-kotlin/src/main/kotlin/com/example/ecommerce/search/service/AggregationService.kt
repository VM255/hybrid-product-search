package com.example.ecommerce.search.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange
import com.example.ecommerce.search.model.Product

class AggregationService(private val client: ElasticsearchClient) {

    fun printPriceStats(indexName: String) {
        val response = client.search({ s ->
            s.index(indexName)
                .size(0)
                .aggregations("price_stats") { a ->
                    a.stats { st -> st.field("price") }
                }
        }, Product::class.java)

        val priceStats = response.aggregations()["price_stats"]
        if (priceStats != null && priceStats.isStats) {
            val stats = priceStats.stats()
            println("\n--- [Metric Aggregation: Price Stats] ---")
            System.out.printf(
                "Count: %d | Min: $%.2f | Max: $%.2f | Avg: $%.2f | Sum: $%.2f%n",
                stats.count(), stats.min(), stats.max(), stats.avg(), stats.sum()
            )
        }
    }

    fun printCategoryDistribution(indexName: String) {
        val response = client.search({ s ->
            s.index(indexName)
                .size(0)
                .aggregations("by_category") { a ->
                    a.terms { t -> t.field("category").size(10) }
                }
        }, Product::class.java)

        val byCategory = response.aggregations()["by_category"]
        if (byCategory != null && byCategory.isSterms) {
            println("\n--- [Bucket Aggregation: Category Distribution] ---")
            for (bucket in byCategory.sterms().buckets().array()) {
                System.out.printf("Category: %-15s -> Count: %d products%n", bucket.key().stringValue(), bucket.docCount())
            }
        }
    }

    fun printCategoryWithAveragePrice(indexName: String) {
        val response = client.search({ s ->
            s.index(indexName)
                .size(0)
                .aggregations("categories") { a ->
                    a.terms { t -> t.field("category") }
                        .aggregations("avg_price") { sub ->
                            sub.avg { av -> av.field("price") }
                        }
                }
        }, Product::class.java)

        val categories = response.aggregations()["categories"]
        if (categories != null && categories.isSterms) {
            println("\n--- [Sub-Aggregation: Average Price Per Category] ---")
            for (bucket in categories.sterms().buckets().array()) {
                val avgPriceAgg = bucket.aggregations()["avg_price"]
                val avgPrice = if (avgPriceAgg != null && avgPriceAgg.isAvg) avgPriceAgg.avg().value() else 0.0
                System.out.printf(
                    "Category: %-15s | Count: %2d | Avg Price: $%.2f%n",
                    bucket.key().stringValue(), bucket.docCount(), avgPrice
                )
            }
        }
    }

    fun printPriceTierDistribution(indexName: String) {
        val response = client.search({ s ->
            s.index(indexName)
                .size(0)
                .aggregations("price_tiers") { a ->
                    a.range { r ->
                        r.field("price")
                            .ranges(
                                AggregationRange.of { rg -> rg.key("Budget (<$100)").to(100.0) },
                                AggregationRange.of { rg -> rg.key("Mid-range ($100-$500)").from(100.0).to(500.0) },
                                AggregationRange.of { rg -> rg.key("Premium (>$500)").from(500.0) }
                            )
                    }
                }
        }, Product::class.java)

        val priceTiers = response.aggregations()["price_tiers"]
        if (priceTiers != null && priceTiers.isRange) {
            println("\n--- [Bucket Aggregation: Price Tiers] ---")
            for (bucket in priceTiers.range().buckets().array()) {
                System.out.printf("Tier: %-22s -> %d products%n", bucket.key(), bucket.docCount())
            }
        }
    }
}
