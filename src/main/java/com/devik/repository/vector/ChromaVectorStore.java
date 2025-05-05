package com.devik.repository.vector;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

//@Component
public class ChromaVectorStore implements VectorStore {

    private final static String COLLECTION_NAME = "WEB_PAGES";
    private final RestTemplate restTemplate;
    private final String chromaDbUrl = System.getenv().getOrDefault("VECTOR_DB_URL", "http://localhost:8000");

    public ChromaVectorStore() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void insert(UUID id, float[] embedding, Map<String, Object> metadata) {
        String url = chromaDbUrl + "/api/v1/collections/" + COLLECTION_NAME + "/upsert";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
                "ids", List.of(id),
                "embeddings", List.of(embedding),
                "metadatas", List.of(Map.of("text", metadata))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to upsert embedding: " + response.getStatusCode());
        }
    }


    @Override
    public List<Map<String, Object>> query(List<Float> embedding, int topK) {
        String url = chromaDbUrl + "/api/v1/collections/" + COLLECTION_NAME + "/query";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
                "query_embeddings", List.of(embedding),
                "n_results", topK
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,   // <--- Just pass the HttpEntity directly
                Map.class
        );


        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            List<List<String>> idsList = (List<List<String>>) response.getBody().get("ids");
//            return idsList.get(0);
            return null;
        } else {
            throw new RuntimeException("Failed to query vector store: " + response.getStatusCode());
        }
    }

}

