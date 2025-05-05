package com.devik.service.embedder;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LocalEmbedderService implements EmbeddingService {

    private final RestTemplate restTemplate;
    ObjectMapper objectMapper = new ObjectMapper();
    private final String embedderUrl = System.getenv().getOrDefault("EMBEDDER_URL", "http://localhost:8081");

    public LocalEmbedderService() {
        this.restTemplate = new RestTemplate();
    }

    public List<Float> embed(String text) {
        String url = embedderUrl + "/embed";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of("inputs", text);

        String jsonPayload = null;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request payload", e);
        }
        HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

        // Use exchange to handle the response properly
        ResponseEntity<List<List<Float>>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<List<List<Float>>>() {
                }
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody().get(0);
        } else {
            throw new RuntimeException("Failed to embed text: " + response.getStatusCode());
        }
    }
}