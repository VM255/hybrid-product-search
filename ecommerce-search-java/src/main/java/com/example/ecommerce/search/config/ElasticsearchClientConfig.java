package com.example.ecommerce.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

/**
 * Modern Java 21 Elasticsearch Client Configuration.
 */
public final class ElasticsearchClientConfig implements AutoCloseable {

    private final RestClient restClient;
    private final ElasticsearchTransport transport;
    private final ElasticsearchClient client;

    public ElasticsearchClientConfig(String host, int port, String scheme) {
        this.restClient = RestClient.builder(new HttpHost(host, port, scheme)).build();

        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        this.transport = new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
        this.client = new ElasticsearchClient(transport);
    }

    public static ElasticsearchClientConfig createDefault() {
        return new ElasticsearchClientConfig("localhost", 9200, "http");
    }

    public ElasticsearchClient client() {
        return client;
    }

    @Override
    public void close() throws Exception {
        transport.close();
        restClient.close();
    }
}
