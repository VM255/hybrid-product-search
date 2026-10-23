package com.example.ecommerce.search

import co.elastic.clients.elasticsearch.core.search.Hit
import com.example.ecommerce.search.config.ElasticsearchClientConfig
import com.example.ecommerce.search.model.Product
import com.example.ecommerce.search.service.AggregationService
import com.example.ecommerce.search.service.DocumentService
import com.example.ecommerce.search.service.EmbeddingService
import com.example.ecommerce.search.service.IndexService
import com.example.ecommerce.search.service.SearchService

private const val INDEX_NAME = "products"

fun main() {
    println("=================================================================")
    println("     E-COMMERCE SEARCH & ANALYTICS ENGINE (Kotlin & ES 8+)       ")
    println("=================================================================\n")

    ElasticsearchClientConfig.createDefault().use { config ->
        val client = config.client

        // Cluster Information & Health Check
        val info = client.info()
        println("Connected to Cluster : ${info.clusterName()}")
        println("Elasticsearch Version: ${info.version().number()} (Lucene ${info.version().luceneVersion()})")
        println("Tagline              : \"${info.tagline()}\"\n")

        val indexService = IndexService(client)
        val documentService = DocumentService(client)
        val searchService = SearchService(client)
        val aggregationService = AggregationService(client)
        val embeddingService = EmbeddingService()

        // STEP 1: CREATE INDEX & DEFINE MAPPINGS
        printStepHeader("STEP 1: Index Creation & Explicit Schema Mapping (Kotlin)")
        println("Concept: Inverted Index, BM25 Similarity & Dense Vectors")
        println("- Custom BM25 similarity: k1=1.2, b=0.75.")
        println("- 'dense_vector' field: 8 dimensions with cosine similarity for k-NN search.")
        println("- 'name' multi-field (name is text, name.keyword is keyword).")

        val created = indexService.createProductIndex(INDEX_NAME)
        println("Index '$INDEX_NAME' created successfully: $created\n")

        // STEP 2: BULK INDEXING DOCUMENTS WITH DENSE VECTORS
        printStepHeader("STEP 2: Bulk Indexing Sample Catalog with Dense Embeddings")
        println("Concept: Bulk API batches multiple documents in a single HTTP request.")
        println("Concept: Kotlin data classes serialized automatically via Jackson Kotlin module.")

        val catalog = getSampleProducts(embeddingService)
        val indexedCount = documentService.bulkIndexProducts(INDEX_NAME, catalog)
        println("Indexed $indexedCount products with embeddings into '$INDEX_NAME'\n")

        // STEP 3: CRUD OPERATIONS (GET, UPDATE, DELETE)
        printStepHeader("STEP 3: Real-Time CRUD Operations")
        println("Concept: Get by ID is a real-time read directly from primary shard/translog.")

        val p1 = documentService.getProductById(INDEX_NAME, "prod-1")
        p1?.let { println("Fetched by ID: $it") }

        println("\nApplying partial update to prod-1: price $1299.99 -> $1199.00...")
        val updated = documentService.updateProductPrice(INDEX_NAME, "prod-1", 1199.00, true)
        println("Update status: ${if (updated) "SUCCESS" else "FAILED"}")

        val p1Updated = documentService.getProductById(INDEX_NAME, "prod-1")
        p1Updated?.let { println("Updated Product: $it") }

        // STEP 4: STANDARD QUERY DSL (MATCH, TERM, BOOL, FUZZY, HIGHLIGHTING)
        printStepHeader("STEP 4: Query DSL (Full-Text, Term, Bool, Fuzzy, Highlighting)")

        println("\n--- [4a. Full-Text Match Query] ---")
        println("Query: Match description with 'wireless noise cancelling'")
        val matchHits = searchService.searchByText(INDEX_NAME, "description", "wireless noise cancelling")
        printHits(matchHits)

        println("\n--- [4b. Exact Term Query] ---")
        println("Query: Term category = 'Audio' (exact match on keyword field)")
        val termHits = searchService.searchByExactCategory(INDEX_NAME, "Audio")
        printHits(termHits)

        println("\n--- [4c. Compound Bool Query] ---")
        println("Query: MUST match 'apple pro' in name/description, FILTER price between $500 & $2000 and inStock=true, SHOULD boost rating >= 4.5")
        val boolHits = searchService.searchCompoundBool(INDEX_NAME, "apple pro", 500.0, 2000.0)
        printHits(boolHits)

        println("\n--- [4d. Fuzzy Query (Typo Tolerance)] ---")
        println("Query: Fuzzy search for misspelled 'lptop' on name field")
        val fuzzyHits = searchService.searchFuzzy(INDEX_NAME, "name", "lptop")
        printHits(fuzzyHits)

        println("\n--- [4e. Search with Term Highlighting] ---")
        println("Query: Multi-match on 'display' with highlighted snippets in <mark>...</mark>")
        val highlightResponse = searchService.searchWithHighlighting(INDEX_NAME, "display")
        for (hit in highlightResponse.hits().hits()) {
            val p = hit.source() ?: continue
            val score = hit.score() ?: 0.0
            System.out.printf("Hit: %s (Score: %.2f)%n", p.name, score)
            hit.highlight().forEach { (f, snippets) ->
                for (snippet in snippets) {
                    println("   -> Field [$f]: $snippet")
                }
            }
        }

        // STEP 5: BM25 RELEVANCE STRATEGY & EXPLAIN
        printStepHeader("STEP 5: BM25 Relevance Scoring & Lucene Explanation")
        println("Concept: Okapi BM25 balances TF, IDF, and Document Length Normalization.")

        println("\n--- [5a. BM25 Search with Field Boost (name^3, description)] ---")
        println("Query: 'wireless headphones' (title matches receive 3x boost over description)")
        val bm25Hits = searchService.searchBm25(INDEX_NAME, "wireless headphones")
        printHits(bm25Hits)

        println("\n--- [5b. Explain API: Inspecting BM25 Math Breakdown] ---")
        searchService.explainBm25Score(INDEX_NAME, "prod-3", "headphones")

        // STEP 6: APPROXIMATE k-NN VECTOR SEARCH (SEMANTIC)
        printStepHeader("STEP 6: Approximate k-NN Vector Search (Dense Vectors)")
        println("Concept: k-NN performs semantic search via HNSW graph traversal in vector space.")
        println("It finds conceptual matches even when keywords do NOT overlap!")

        val semanticQuery = "portable audio travel gear"
        println("Semantic User Query: \"$semanticQuery\"")
        val queryVector = embeddingService.generateEmbedding(semanticQuery)
        println("Encoded 8D Query Vector: ${formatVector(queryVector)}\n")

        println("Top 3 Nearest Neighbors (Cosine Similarity):")
        val knnHits = searchService.searchKnnVector(INDEX_NAME, queryVector, 3)
        printHits(knnHits)

        // STEP 7: HYBRID SEARCH (BM25 + k-NN COMBINED)
        printStepHeader("STEP 7: Hybrid Search (BM25 Lexical + k-NN Semantic)")
        println("Concept: Modern search engines combine exact lexical BM25 precision with semantic recall.")
        println("Score = Lexical BM25 Score + (k-NN Cosine Similarity * Boost)")

        val hybridText = "Apple pro workstation"
        val hybridVector = embeddingService.generateEmbedding(hybridText)
        println("Hybrid Search for: \"$hybridText\"")

        val hybridHits = searchService.searchHybrid(INDEX_NAME, hybridText, hybridVector, 3)
        printHits(hybridHits)

        // STEP 8: AGGREGATIONS & REAL-TIME ANALYTICS
        printStepHeader("STEP 8: Aggregations & Analytics")
        println("Concept: Aggregations execute in parallel across shards without moving raw records.")

        aggregationService.printPriceStats(INDEX_NAME)
        aggregationService.printCategoryDistribution(INDEX_NAME)
        aggregationService.printCategoryWithAveragePrice(INDEX_NAME)
        aggregationService.printPriceTierDistribution(INDEX_NAME)

        println("\n=================================================================")
        println("     ALL ELASTICSEARCH DEMONSTRATIONS EXECUTED SUCCESSFULLY!     ")
        println("=================================================================\n")
    }
}

private fun getSampleProducts(embeddingService: EmbeddingService): List<Product> {
    val products = listOf(
        Product(
            id = "prod-1",
            name = "Apple MacBook Pro 16",
            description = "High performance laptop with M3 Max chip, Liquid Retina XDR display, 36GB unified memory, and 1TB SSD.",
            category = "Laptops",
            tags = listOf("apple", "laptop", "m3", "workstation"),
            price = 2499.00,
            rating = 4.9,
            inStock = true,
            createdDate = "2024-01-10T09:00:00Z"
        ),
        Product(
            id = "prod-2",
            name = "Dell XPS 15 Laptop",
            description = "Ultra-thin premium laptop with Intel Core i9, stunning OLED 3.5K infinity-edge display, and NVIDIA RTX graphics.",
            category = "Laptops",
            tags = listOf("dell", "laptop", "oled", "intel"),
            price = 1899.00,
            rating = 4.6,
            inStock = true,
            createdDate = "2024-02-15T10:30:00Z"
        ),
        Product(
            id = "prod-3",
            name = "Sony WH-1000XM5 Wireless Headphones",
            description = "Industry-leading wireless noise cancelling over-ear headphones with exceptional sound clarity and 30-hour battery.",
            category = "Audio",
            tags = listOf("sony", "audio", "noise-cancelling", "wireless"),
            price = 399.99,
            rating = 4.8,
            inStock = true,
            createdDate = "2024-03-01T12:00:00Z"
        ),
        Product(
            id = "prod-4",
            name = "Apple AirPods Pro (2nd Gen)",
            description = "Active noise cancelling wireless earbuds with transparency mode, personalized spatial audio, and USB-C MagSafe case.",
            category = "Audio",
            tags = listOf("apple", "earbuds", "wireless", "audio"),
            price = 249.00,
            rating = 4.7,
            inStock = true,
            createdDate = "2024-01-20T14:15:00Z"
        ),
        Product(
            id = "prod-5",
            name = "Samsung Galaxy S24 Ultra",
            description = "Flagship smartphone with Galaxy AI features, titanium frame, 200MP camera, and 6.8 inch dynamic AMOLED display.",
            category = "Smartphones",
            tags = listOf("samsung", "smartphone", "android", "ai"),
            price = 1299.99,
            rating = 4.7,
            inStock = true,
            createdDate = "2024-01-25T11:00:00Z"
        ),
        Product(
            id = "prod-6",
            name = "Google Pixel 8 Pro",
            description = "Smart and powerful Google Tensor G3 phone with pro triple camera system and super actua display.",
            category = "Smartphones",
            tags = listOf("google", "pixel", "android", "camera"),
            price = 999.00,
            rating = 4.5,
            inStock = false,
            createdDate = "2023-11-10T08:00:00Z"
        ),
        Product(
            id = "prod-7",
            name = "Logitech MX Master 3S Mouse",
            description = "Performance wireless mouse with ultra-fast scrolling, 8K DPI sensor on glass, and quiet click technology.",
            category = "Accessories",
            tags = listOf("logitech", "mouse", "ergonomic", "wireless"),
            price = 99.99,
            rating = 4.8,
            inStock = true,
            createdDate = "2023-09-05T16:00:00Z"
        ),
        Product(
            id = "prod-8",
            name = "Corsair K70 RGB PRO Mechanical Gaming Keyboard",
            description = "Durable aluminum frame mechanical gaming keyboard with CHERRY MX switches, per-key RGB backlighting, and 8000Hz hyper-polling.",
            category = "Gaming",
            tags = listOf("corsair", "keyboard", "gaming", "mechanical"),
            price = 169.99,
            rating = 4.6,
            inStock = true,
            createdDate = "2023-12-01T13:45:00Z"
        ),
        Product(
            id = "prod-9",
            name = "Kindle Paperwhite (16 GB)",
            description = "6.8-inch glare-free display with adjustable warm light, up to 10 weeks battery life, and waterproof design.",
            category = "E-Readers",
            tags = listOf("amazon", "kindle", "books", "reading"),
            price = 149.99,
            rating = 4.7,
            inStock = true,
            createdDate = "2024-02-01T10:00:00Z"
        ),
        Product(
            id = "prod-10",
            name = "Anker 737 Power Bank (PowerCore 24K)",
            description = "Ultra-powerful 140W portable charger with smart digital display, capable of charging laptops and phones rapidly.",
            category = "Accessories",
            tags = listOf("anker", "charger", "battery", "usb-c"),
            price = 109.99,
            rating = 4.6,
            inStock = false,
            createdDate = "2024-03-10T17:20:00Z"
        )
    )

    for (product in products) {
        val combinedText = "${product.name} ${product.description} ${product.tags.joinToString(" ")}"
        product.embedding = embeddingService.generateEmbedding(combinedText)
    }

    return products
}

private fun formatVector(vector: List<Float>): String =
    vector.joinToString(prefix = "[", postfix = "]", separator = ", ") { "%.2f".format(it) }

private fun printHits(hits: List<Hit<Product>>?) {
    if (hits.isNullOrEmpty()) {
        println("  (No matching documents found)")
        return
    }
    for (hit in hits) {
        val p = hit.source() ?: continue
        val score = hit.score() ?: 0.0
        System.out.printf("  [Score: %5.2f] %s ($%.2f) - Cat: %s%n", score, p.name, p.price, p.category)
    }
}

private fun printStepHeader(title: String) {
    println("\n-----------------------------------------------------------------")
    println(title)
    println("-----------------------------------------------------------------")
}
