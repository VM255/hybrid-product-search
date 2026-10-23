package com.example.ecommerce.search.config

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.ElasticsearchTransport
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient

/**
 * Manages the modern Elasticsearch v8+ client in Kotlin.
 * Registers the Jackson Kotlin module for seamless data class serialization.
 */
class ElasticsearchClientConfig(
    host: String = "localhost",
    port: Int = 9200,
    scheme: String = "http"
) : AutoCloseable {

    private val restClient: RestClient = RestClient.builder(HttpHost(host, port, scheme)).build()

    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerKotlinModule()
    }

    private val transport: ElasticsearchTransport = RestClientTransport(restClient, JacksonJsonpMapper(objectMapper))

    val client: ElasticsearchClient = ElasticsearchClient(transport)

    companion object {
        fun createDefault(): ElasticsearchClientConfig = ElasticsearchClientConfig()
    }

    override fun close() {
        transport.close()
        restClient.close()
    }
}
