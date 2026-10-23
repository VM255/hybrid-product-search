package com.example.ecommerce.search;

import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.ecommerce.search.config.ElasticsearchClientConfig;
import com.example.ecommerce.search.model.Product;
import com.example.ecommerce.search.service.AggregationService;
import com.example.ecommerce.search.service.DocumentService;
import com.example.ecommerce.search.service.EmbeddingService;
import com.example.ecommerce.search.service.IndexService;
import com.example.ecommerce.search.service.SearchService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Modern Java 21 Orchestrator demonstrating:
 * - Immutable Records & Functional withers
 * - Stream pipelines & Optional chaining
 * - Text blocks & Local variable type inference (var)
 * - Okapi BM25 tuning, k-NN vector search, and Hybrid Search
 */
public final class App {

    private static final String INDEX_NAME = "products";

    public static void main(String[] args) {
        System.out.println("""
                =================================================================
                     E-COMMERCE SEARCH & ANALYTICS ENGINE (Modern Java 21)
                =================================================================
                """);

        try (var config = ElasticsearchClientConfig.createDefault()) {
            final var client = config.client();

            // Cluster Information & Health Check
            final var info = client.info();
            System.out.printf("Connected to Cluster : %s%n", info.clusterName());
            System.out.printf("Elasticsearch Version: %s (Lucene %s)%n",
                    info.version().number(), info.version().luceneVersion());
            System.out.printf("Tagline              : \"%s\"%n%n", info.tagline());

            final var indexService = new IndexService(client);
            final var documentService = new DocumentService(client);
            final var searchService = new SearchService(client);
            final var aggregationService = new AggregationService(client);
            final var embeddingService = new EmbeddingService();

            // STEP 1: CREATE INDEX & DEFINE MAPPINGS
            printStepHeader("STEP 1: Index Creation & Explicit Schema Mapping (Java 21)");
            System.out.println("""
                    Concept: Inverted Index, BM25 Similarity & Dense Vectors
                    - Custom BM25 similarity: k1=1.2, b=0.75.
                    - 'dense_vector' field: 8 dimensions with cosine similarity for k-NN search.
                    - 'name' multi-field (name is text, name.keyword is keyword).
                    """);

            final var created = indexService.createProductIndex(INDEX_NAME);
            System.out.printf("Index '%s' created successfully: %s%n%n", INDEX_NAME, created);

            // STEP 2: BULK INDEXING DOCUMENTS WITH DENSE VECTORS
            printStepHeader("STEP 2: Bulk Indexing Sample Catalog with Dense Embeddings");
            System.out.println("""
                    Concept: Bulk API batches multiple documents in a single HTTP request.
                    Concept: Immutable Java 21 records with attached 8D semantic dense vectors.
                    """);

            final var catalog = getSampleProducts(embeddingService);
            final var indexedCount = documentService.bulkIndexProducts(INDEX_NAME, catalog);
            System.out.printf("Indexed %d products with embeddings into '%s'%n%n", indexedCount, INDEX_NAME);

            // STEP 3: CRUD OPERATIONS (GET, UPDATE, DELETE)
            printStepHeader("STEP 3: Real-Time CRUD Operations");
            System.out.println("Concept: Get by ID is a real-time read directly from primary shard/translog.");

            documentService.getProductById(INDEX_NAME, "prod-1")
                    .ifPresent(p -> System.out.printf("Fetched by ID: %s%n", p));

            System.out.println("\nApplying partial update to prod-1: price $1299.99 -> $1199.00...");
            final var updated = documentService.updateProductPrice(INDEX_NAME, "prod-1", 1199.00, true);
            System.out.printf("Update status: %s%n", updated ? "SUCCESS" : "FAILED");

            documentService.getProductById(INDEX_NAME, "prod-1")
                    .ifPresent(p -> System.out.printf("Updated Product: %s%n", p));

            // STEP 4: STANDARD QUERY DSL (MATCH, TERM, BOOL, FUZZY, HIGHLIGHTING)
            printStepHeader("STEP 4: Query DSL (Full-Text, Term, Bool, Fuzzy, Highlighting)");

            System.out.println("\n--- [4a. Full-Text Match Query] ---");
            System.out.println("Query: Match description with 'wireless noise cancelling'");
            printHits(searchService.searchByText(INDEX_NAME, "description", "wireless noise cancelling"));

            System.out.println("\n--- [4b. Exact Term Query] ---");
            System.out.println("Query: Term category = 'Audio' (exact match on keyword field)");
            printHits(searchService.searchByExactCategory(INDEX_NAME, "Audio"));

            System.out.println("\n--- [4c. Compound Bool Query] ---");
            System.out.println("Query: MUST match 'apple pro' in name/description, FILTER price between $500 & $2000 and inStock=true, SHOULD boost rating >= 4.5");
            printHits(searchService.searchCompoundBool(INDEX_NAME, "apple pro", 500.0, 2000.0));

            System.out.println("\n--- [4d. Fuzzy Query (Typo Tolerance)] ---");
            System.out.println("Query: Fuzzy search for misspelled 'lptop' on name field");
            printHits(searchService.searchFuzzy(INDEX_NAME, "name", "lptop"));

            System.out.println("\n--- [4e. Search with Term Highlighting] ---");
            System.out.println("Query: Multi-match on 'display' with highlighted snippets in <mark>...</mark>");
            final var highlightResponse = searchService.searchWithHighlighting(INDEX_NAME, "display");
            for (var hit : highlightResponse.hits().hits()) {
                final var p = hit.source();
                if (p == null) continue;
                System.out.printf("Hit: %s (Score: %.2f)%n", p.name(), hit.score() != null ? hit.score() : 0.0);
                hit.highlight().forEach((f, snippets) ->
                        snippets.forEach(snippet -> System.out.printf("   -> Field [%s]: %s%n", f, snippet))
                );
            }

            // STEP 5: BM25 RELEVANCE STRATEGY & EXPLAIN
            printStepHeader("STEP 5: BM25 Relevance Scoring & Lucene Explanation");
            System.out.println("Concept: Okapi BM25 balances TF, IDF, and Document Length Normalization.");

            System.out.println("\n--- [5a. BM25 Search with Field Boost (name^3, description)] ---");
            System.out.println("Query: 'wireless headphones' (title matches receive 3x boost over description)");
            printHits(searchService.searchBm25(INDEX_NAME, "wireless headphones"));

            System.out.println("\n--- [5b. Explain API: Inspecting BM25 Math Breakdown] ---");
            searchService.explainBm25Score(INDEX_NAME, "prod-3", "headphones");

            // STEP 6: APPROXIMATE k-NN VECTOR SEARCH (SEMANTIC)
            printStepHeader("STEP 6: Approximate k-NN Vector Search (Dense Vectors)");
            System.out.println("Concept: k-NN performs semantic search via HNSW graph traversal in vector space.");
            System.out.println("It finds conceptual matches even when keywords do NOT overlap!");

            final var semanticQuery = "portable audio travel gear";
            System.out.printf("Semantic User Query: \"%s\"%n", semanticQuery);
            final var queryVector = embeddingService.generateEmbedding(semanticQuery);
            System.out.printf("Encoded 8D Query Vector: %s%n%n", formatVector(queryVector));

            System.out.println("Top 3 Nearest Neighbors (Cosine Similarity):");
            printHits(searchService.searchKnnVector(INDEX_NAME, queryVector, 3));

            // STEP 7: HYBRID SEARCH (BM25 + k-NN COMBINED)
            printStepHeader("STEP 7: Hybrid Search (BM25 Lexical + k-NN Semantic)");
            System.out.println("Concept: Modern search engines combine exact lexical BM25 precision with semantic recall.");
            System.out.println("Score = Lexical BM25 Score + (k-NN Cosine Similarity * Boost)");

            final var hybridText = "Apple pro workstation";
            final var hybridVector = embeddingService.generateEmbedding(hybridText);
            System.out.printf("Hybrid Search for: \"%s\"%n", hybridText);
            printHits(searchService.searchHybrid(INDEX_NAME, hybridText, hybridVector, 3));

            // STEP 8: AGGREGATIONS & REAL-TIME ANALYTICS
            printStepHeader("STEP 8: Aggregations & Analytics");
            System.out.println("Concept: Aggregations execute in parallel across shards without moving raw records.");

            aggregationService.printPriceStats(INDEX_NAME);
            aggregationService.printCategoryDistribution(INDEX_NAME);
            aggregationService.printCategoryWithAveragePrice(INDEX_NAME);
            aggregationService.printPriceTierDistribution(INDEX_NAME);

            System.out.println("""

                    =================================================================
                         ALL ELASTICSEARCH DEMONSTRATIONS EXECUTED SUCCESSFULLY!
                    =================================================================
                    """);

        } catch (Exception e) {
            System.err.println("Error running Elasticsearch learning application:");
            e.printStackTrace();
        }
    }

    private static List<Product> getSampleProducts(EmbeddingService embeddingService) {
        final var rawProducts = List.of(
                new Product("prod-1", "Apple MacBook Pro 16",
                        "High performance laptop with M3 Max chip, Liquid Retina XDR display, 36GB unified memory, and 1TB SSD.",
                        "Laptops", List.of("apple", "laptop", "m3", "workstation"), 2499.00, 4.9, true, "2024-01-10T09:00:00Z"),

                new Product("prod-2", "Dell XPS 15 Laptop",
                        "Ultra-thin premium laptop with Intel Core i9, stunning OLED 3.5K infinity-edge display, and NVIDIA RTX graphics.",
                        "Laptops", List.of("dell", "laptop", "oled", "intel"), 1899.00, 4.6, true, "2024-02-15T10:30:00Z"),

                new Product("prod-3", "Sony WH-1000XM5 Wireless Headphones",
                        "Industry-leading wireless noise cancelling over-ear headphones with exceptional sound clarity and 30-hour battery.",
                        "Audio", List.of("sony", "audio", "noise-cancelling", "wireless"), 399.99, 4.8, true, "2024-03-01T12:00:00Z"),

                new Product("prod-4", "Apple AirPods Pro (2nd Gen)",
                        "Active noise cancelling wireless earbuds with transparency mode, personalized spatial audio, and USB-C MagSafe case.",
                        "Audio", List.of("apple", "earbuds", "wireless", "audio"), 249.00, 4.7, true, "2024-01-20T14:15:00Z"),

                new Product("prod-5", "Samsung Galaxy S24 Ultra",
                        "Flagship smartphone with Galaxy AI features, titanium frame, 200MP camera, and 6.8 inch dynamic AMOLED display.",
                        "Smartphones", List.of("samsung", "smartphone", "android", "ai"), 1299.99, 4.7, true, "2024-01-25T11:00:00Z"),

                new Product("prod-6", "Google Pixel 8 Pro",
                        "Smart and powerful Google Tensor G3 phone with pro triple camera system and super actua display.",
                        "Smartphones", List.of("google", "pixel", "android", "camera"), 999.00, 4.5, false, "2023-11-10T08:00:00Z"),

                new Product("prod-7", "Logitech MX Master 3S Mouse",
                        "Performance wireless mouse with ultra-fast scrolling, 8K DPI sensor on glass, and quiet click technology.",
                        "Accessories", List.of("logitech", "mouse", "ergonomic", "wireless"), 99.99, 4.8, true, "2023-09-05T16:00:00Z"),

                new Product("prod-8", "Corsair K70 RGB PRO Mechanical Gaming Keyboard",
                        "Durable aluminum frame mechanical gaming keyboard with CHERRY MX switches, per-key RGB backlighting, and 8000Hz hyper-polling.",
                        "Gaming", List.of("corsair", "keyboard", "gaming", "mechanical"), 169.99, 4.6, true, "2023-12-01T13:45:00Z"),

                new Product("prod-9", "Kindle Paperwhite (16 GB)",
                        "6.8-inch glare-free display with adjustable warm light, up to 10 weeks battery life, and waterproof design.",
                        "E-Readers", List.of("amazon", "kindle", "books", "reading"), 149.99, 4.7, true, "2024-02-01T10:00:00Z"),

                new Product("prod-10", "Anker 737 Power Bank (PowerCore 24K)",
                        "Ultra-powerful 140W portable charger with smart digital display, capable of charging laptops and phones rapidly.",
                        "Accessories", List.of("anker", "charger", "battery", "usb-c"), 109.99, 4.6, false, "2024-03-10T17:20:00Z")
        );

        // Functional stream pipeline to attach embeddings via functional wither
        return rawProducts.stream()
                .map(product -> {
                    var combinedText = product.name() + " " + product.description() + " " + String.join(" ", product.tags());
                    return product.withEmbedding(embeddingService.generateEmbedding(combinedText));
                })
                .toList();
    }

    private static String formatVector(List<Float> vector) {
        return vector.stream()
                .map(v -> String.format("%.2f", v))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static void printHits(List<Hit<Product>> hits) {
        if (hits == null || hits.isEmpty()) {
            System.out.println("  (No matching documents found)");
            return;
        }
        hits.forEach(hit -> {
            var p = hit.source();
            if (p != null) {
                var score = hit.score() != null ? hit.score() : 0.0;
                System.out.printf("  [Score: %5.2f] %s ($%.2f) - Cat: %s%n", score, p.name(), p.price(), p.category());
            }
        });
    }

    private static void printStepHeader(String title) {
        System.out.println("\n-----------------------------------------------------------------");
        System.out.println(title);
        System.out.println("-----------------------------------------------------------------");
    }
}
