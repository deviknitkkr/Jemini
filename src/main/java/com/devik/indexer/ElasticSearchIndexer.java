package com.devik.indexer;


import com.devik.model.CrawlResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ElasticSearchIndexer implements IndexerStrategy {

    @Autowired
    private RestHighLevelClient client;
    private static final String INDEX_NAME = "web_pages";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initialize() {
        try {
            // Check if index exists
            GetIndexRequest getIndexRequest = new GetIndexRequest(INDEX_NAME);
            boolean exists = client.indices().exists(getIndexRequest, RequestOptions.DEFAULT);

            // Create index if not exists
            if (!exists) {
                CreateIndexRequest createIndexRequest = new CreateIndexRequest(INDEX_NAME);
                client.indices().create(createIndexRequest, RequestOptions.DEFAULT);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Elasticsearch client", e);
        }
    }

    @Override
    public void indexDocument(CrawlResult result) {
        try {
            Map<String, Object> document = new HashMap<>();
            document.put("url", result.getUrl());
            document.put("title", result.getTitle());
            document.put("content", result.getContent());
            document.put("crawledAt", result.getCrawledAt().toString());

            IndexRequest indexRequest = new IndexRequest(INDEX_NAME)
                    .id(result.getId().toString())
                    .source(document, XContentType.JSON);

            client.index(indexRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Failed to index document", e);
        }
    }

    @Override
    public List<Map<String, Object>> search(com.devik.model.SearchRequest request) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

            // Create multi-match query (searching in title and content)
            MultiMatchQueryBuilder multiMatchQuery = QueryBuilders.multiMatchQuery(
                            request.getQuery(),
                            "title", "content")
                    .field("title", 2.0f); // Boost title field

            searchSourceBuilder.query(multiMatchQuery);
            searchSourceBuilder.from(request.getPage() * request.getLimit());
            searchSourceBuilder.size(request.getLimit());

            searchRequest.source(searchSourceBuilder);

            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

            return prepareResponse(response);
        } catch (IOException e) {
            throw new RuntimeException("Failed to search documents", e);
        }
    }

    private static List<Map<String, Object>> prepareResponse(SearchResponse response) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> result = hit.getSourceAsMap();

            // Truncate 'content' field if it's too long
            Object content = result.get("content");
            if (content instanceof String contentStr) {
                if (contentStr.length() > 100) {
                    result.put("content", contentStr.substring(0, 50) + "...");
                }
            }

            result.put("score", hit.getScore());
            results.add(result);
        }
        return results;
    }


}