# E-Commerce Search & Analytics Engine (Elasticsearch 8+ Multi-Module: Java 21 & Kotlin)

This repository provides a hands-on, production-grade **E-Commerce Search & Analytics Engine** organized as a Maven multi-module architecture:
1. **`ecommerce-search-java`**: Built with **Modern Java 21 LTS**, utilizing **Records**, **Functional Pipelines** (Streams, DoubleStreams, Optionals), immutable defensive copies, and **Text Blocks**.
2. **`ecommerce-search-kotlin`**: Built with **Kotlin 2.0+**, utilizing **Data Classes**, **Jackson Kotlin Module**, extension functions, and null-safety.

---

## 🚀 Quick Start

### 1. Start Elasticsearch (Docker)
Ensure Docker is running, then run:
```bash
docker compose up -d
```
Verify cluster health:
```bash
curl http://localhost:9200
```

### 2. Run Either Implementation
```bash
# Run Modern Java 21 module
./run.sh java

# Run Kotlin module
./run.sh kotlin

# Run both sequentially
./run.sh all
```

---

## 📚 Core Elasticsearch Concepts Explained

### 1. Architecture & Lucene Under the Hood
* **Cluster**: A collection of one or more connected Elasticsearch nodes sharing the same cluster name.
* **Node**: A single running instance of Elasticsearch server.
* **Index**: A logical namespace representing a collection of documents (analogous to a SQL Table or MongoDB Collection).
* **Shard**: A low-level worker unit. An Elasticsearch index is horizontally partitioned into one or more **Primary Shards**. Each shard is a standalone **Apache Lucene** instance.
* **Replica Shards**: Copies of primary shards. They serve two vital purposes:
  1. **High Availability**: If the node hosting a primary shard crashes, a replica is promoted to primary.
  2. **Read Scalability**: Search queries execute in parallel across both primary and replica shards.
* **Routing Formula**:
  $$\text{Shard ID} = |\text{hash}(routing\_id)| \pmod{\text{number\_of\_primary\_shards}}$$
  *(This is why primary shard count cannot be changed without reindexing).*

---

### 2. The Inverted Index vs B-Tree
Traditional relational databases (PostgreSQL, MySQL) use **B-Trees**. B-Trees are great for finding row $X$ where `id = 5` or `price BETWEEN 10 AND 20`, but terrible for searching: *"Find all documents containing the words 'wireless' and 'cancelling'"*.

Elasticsearch solves this with an **Inverted Index**:
Instead of mapping `Document -> Words`, it maps `Word -> List of Document IDs (Posting List)`:

| Term | Posting List (Document IDs) & Positions |
| :--- | :--- |
| **apple** | Doc 1 [pos: 1], Doc 4 [pos: 1] |
| **cancelling** | Doc 3 [pos: 5], Doc 4 [pos: 3] |
| **laptop** | Doc 1 [pos: 4], Doc 2 [pos: 4] |
| **wireless** | Doc 3 [pos: 3], Doc 4 [pos: 4], Doc 7 [pos: 2] |

When you search for `"wireless cancelling"`, Elasticsearch finds the intersection of posting lists in microseconds!

---

### 3. Mappings: `text` vs `keyword`
This is the **#1 concept** every Elasticsearch developer must master:

| Property | `text` Field | `keyword` Field |
| :--- | :--- | :--- |
| **Analysis** | Analyzed (Tokenized, lowercased, stemmed) | Stored verbatim as a single token |
| **Target Use-Case** | Full-text search (Descriptions, articles, titles) | Exact matches, status codes, IDs, emails |
| **Supported Queries** | `match`, `multi_match`, `match_phrase` | `term`, `terms`, prefix, wildcard |
| **Aggregations & Sorting** | ❌ Disabled by default (high memory cost) | ✅ Fast via `doc_values` |
| **Multi-field pattern** | You can index a field as both: `name` (text) and `name.keyword` (keyword). |

---

### 4. The Analysis Pipeline
When a `text` field is indexed or searched, it passes through an **Analyzer**:
$$\text{Raw String} \longrightarrow [\text{Char Filters}] \longrightarrow [\text{Tokenizer}] \longrightarrow [\text{Token Filters}] \longrightarrow \text{Inverted Index}$$

1. **Character Filters**: Strips HTML tags (`<p>Hello</p>` $\to$ `Hello`), converts `&` $\to$ `and`.
2. **Tokenizer**: Splits text into individual tokens (e.g., standard tokenizer splits on punctuation and whitespace).
3. **Token Filters**:
   - `lowercase`: Converts `Apple` $\to$ `apple`.
   - `stop`: Removes common words (`the`, `is`, `at`).
   - `stemmer`: Reduces words to their linguistic root (`running`, `runs` $\to$ `run`).
   - `synonym`: Expands synonyms (`laptop` $\to$ `notebook`, `computer`).

---

### 5. Document Lifecycle: Write & Read Paths
* **Near-Real-Time (NRT)**: When you index a document, it is written to an in-memory buffer and appended to a **Translog** (write-ahead log for crash durability).
* **Refresh (Default: every 1 second)**: The in-memory buffer is flushed to an OS page cache Lucene segment. Once in a segment, the document becomes **searchable**.
* **Flush**: Periodically, segments are fsynced to persistent disk storage and the translog is truncated.
* **Real-time GET**: Calling `GET /products/_doc/prod-1` queries the Translog directly, returning the latest data even before the 1-second refresh interval elapses.

---

### 6. Relevance Scoring Strategy (Okapi BM25 Deep Dive)
Elasticsearch ranks full-text search hits using **Okapi BM25**.

#### The BM25 Formula:
$$\text{score}(D, Q) = \sum_{i=1}^{N} \text{IDF}(q_i) \cdot \frac{f(q_i, D) \cdot (k_1 + 1)}{f(q_i, D) + k_1 \cdot \left(1 - b + b \cdot \frac{|D|}{\text{avgdl}}\right)}$$

Where:
* **$\text{IDF}(q_i) = \ln\left(1 + \frac{N - n(q_i) + 0.5}{n(q_i) + 0.5}\right)$**: Inverse Document Frequency. Rare terms carry exponentially higher scores than common words.
* **$f(q_i, D)$**: Term Frequency (occurrences in document $D$).
* **$|D| / \text{avgdl}$**: Ratio of document length to the average document length across the entire index.
* **Tuning Parameter $k_1$ (default $1.2$)**: Controls **term frequency saturation**.
  * Lower $k_1$ (e.g. $0.5$): score saturates quickly; 1 occurrence counts almost as much as 10.
  * Higher $k_1$ (e.g. $2.0$): multiple occurrences continue increasing the score significantly.
* **Tuning Parameter $b$ (default $0.75$)**: Controls **document length normalization**.
  * $b = 1.0$: Full penalty for long documents (favors concise titles).
  * $b = 0.0$: Completely disables length normalization (a match in a 5,000-word article scores identical to a match in a 5-word headline).

---

### 7. Approximate k-NN Vector Search (Dense Vectors & Semantic Search)
Traditional keyword search fails when users search by concept rather than exact words (e.g., searching *"portable audio travel gear"* for products titled *"AirPods"* or *"Headphones"*).

* **Dense Vector Mapping**:
  ```json
  "embedding": {
    "type": "dense_vector",
    "dims": 8,
    "index": true,
    "similarity": "cosine"
  }
  ```
* **HNSW (Hierarchical Navigable Small World)**:
  Elasticsearch indexes dense vectors into multi-layer proximity graphs. Finding the nearest neighbors is $O(\log N)$ instead of an exhaustive $O(N)$ brute-force comparison.
* **Similarity Metrics**:
  * **`cosine`**: Measures angular orientation (ignores vector magnitude; best for normalized text embeddings).
  * **`dot_product`**: Faster than cosine when vectors are pre-normalized to unit length ($\|\mathbf{v}\|_2 = 1$).
  * **`l2_norm`**: Euclidean distance (sensitive to magnitude).

---

### 8. Hybrid Search Strategy (BM25 + k-NN)
Hybrid search combines the best of both worlds in a single query:
$$\text{Final Score} = \text{BM25 Lexical Score} + (\text{k-NN Cosine Similarity} \times \text{boost})$$

* **BM25 Lexical Search** provides pinpoint precision for exact part numbers, model names, brand acronyms ("M3 Max", "XPS 15", "OLED").
* **k-NN Vector Search** provides high recall for conceptual intent, synonyms, and natural language descriptions.

---

### 9. Query DSL Cheatsheet
* **`match`**: Analyzes the search query and searches against analyzed `text` fields.
* **`term`**: Exact match against `keyword` or numeric fields without analyzing the query.
* **`bool`**: Compound boolean logic:
  - `must`: Clauses that must match; adds to `_score`.
  - `filter`: Clauses that must match; **does NOT calculate score** ($\text{score} = 0$), and is **cached automatically** in memory by Elasticsearch.
  - `should`: Acts as an OR condition or boosts score if present.
  - `must_not`: Inverts match condition; acts as a filter.
* **`fuzzy`**: Matches strings within a Levenshtein edit distance (e.g., handles typos like `"lptop"` $\to$ `"laptop"`).
* **`knn`**: Approximate nearest neighbors vector search on dense vectors.
* **`match_phrase`**: Ensures words appear in the exact specified order (with optional `slop` distance).

---

### 10. Aggregations (Analytics Engine)
Aggregations run alongside search queries to compute real-time analytical summaries:
* **Metric Aggregations**: Calculate values over documents (e.g., `avg`, `min`, `max`, `sum`, `stats`, `cardinality`).
* **Bucket Aggregations**: Group documents into buckets (equivalent to SQL `GROUP BY`):
  - `terms`: Bucket per unique keyword term (e.g., category count).
  - `range`: Bucket into custom numerical intervals (e.g., price tiers).
  - `date_histogram`: Bucket across time (e.g., sales per month).
* **Sub-Aggregations**: Nest metric aggregations inside buckets (e.g., average price **per category**).

---

## 💻 Multi-Module Codebase Structure

```
elasticsearch-project/
├── pom.xml                                       # Root Maven parent POM (<modules>)
├── docker-compose.yml                            # Local Elasticsearch 8.15.0 single-node container
├── run.sh                                        # Interactive/automated multi-module runner
│
├── ecommerce-search-java/                        # Module 1: Modern Java 21 LTS
│   ├── pom.xml
│   └── src/main/java/com/example/ecommerce/search/
│       ├── App.java                              # Java 21 demo orchestrator
│       ├── config/ElasticsearchClientConfig.java # Modern client setup
│       ├── model/Product.java                    # Java 21 Record (immutable, functional withers)
│       └── service/
│           ├── EmbeddingService.java             # Functional Stream/DoubleStream vector encoder
│           ├── IndexService.java                 # Map.of immutable mappings, BM25 & dense vector
│           ├── DocumentService.java              # Functional CRUD & bulk batching with Optional
│           ├── SearchService.java                # Query DSL, BM25, k-NN, Hybrid & Explain
│           └── AggregationService.java           # Optional pipelines & stream analytics
│
└── ecommerce-search-kotlin/                      # Module 2: Kotlin 2.0+
    ├── pom.xml
    └── src/main/kotlin/com/example/ecommerce/search/
        ├── App.kt                                # Kotlin demo orchestrator
        ├── config/ElasticsearchClientConfig.kt   # Jackson Kotlin module registration
        ├── model/Product.kt                      # Kotlin data class with default values
        └── service/
            ├── EmbeddingService.kt               # Extension functions & Kotlin collections
            ├── IndexService.kt                   # Index management & schema mappings
            ├── DocumentService.kt                # Null-safe CRUD & Bulk API
            ├── SearchService.kt                  # Trailing lambdas for Query DSL & k-NN
            └── AggregationService.kt             # Bucket & metric aggregations
```
