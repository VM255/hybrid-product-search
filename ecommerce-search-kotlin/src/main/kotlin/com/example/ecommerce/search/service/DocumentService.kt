package com.example.ecommerce.search.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.elasticsearch.core.BulkRequest
import com.example.ecommerce.search.model.Product

class DocumentService(private val client: ElasticsearchClient) {

    fun indexProduct(indexName: String, product: Product): String {
        val response = client.index { i ->
            i.index(indexName)
                .id(product.id)
                .document(product)
                .refresh(Refresh.True)
        }
        return response.result().jsonValue()
    }

    fun bulkIndexProducts(indexName: String, products: List<Product>): Int {
        val br = BulkRequest.Builder()

        for (product in products) {
            br.operations { op ->
                op.index { idx ->
                    idx.index(indexName)
                        .id(product.id)
                        .document(product)
                }
            }
        }

        br.refresh(Refresh.True)
        val result = client.bulk(br.build())

        if (result.errors()) {
            System.err.println("Bulk indexing had errors!")
            for (item in result.items()) {
                if (item.error() != null) {
                    System.err.printf("Error on doc %s: %s%n", item.id(), item.error()?.reason())
                }
            }
        }

        return result.items().size
    }

    fun getProductById(indexName: String, id: String): Product? {
        val response = client.get({ g ->
            g.index(indexName).id(id)
        }, Product::class.java)

        return if (response.found()) response.source() else null
    }

    fun updateProductPrice(indexName: String, id: String, newPrice: Double, inStock: Boolean?): Boolean {
        val docUpdates = mutableMapOf<String, Any>("price" to newPrice)
        if (inStock != null) {
            docUpdates["inStock"] = inStock
        }

        val response = client.update({ u ->
            u.index(indexName)
                .id(id)
                .doc(docUpdates)
                .refresh(Refresh.True)
        }, Product::class.java)

        return response.result().jsonValue().equals("updated", ignoreCase = true)
    }

    fun deleteProduct(indexName: String, id: String): Boolean {
        val response = client.delete { d ->
            d.index(indexName)
                .id(id)
                .refresh(Refresh.True)
        }
        return response.result().jsonValue().equals("deleted", ignoreCase = true)
    }
}
