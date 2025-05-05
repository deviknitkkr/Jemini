package com.devik.repository.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

@Slf4j
@Component
public class QdrantVectorStore implements VectorStore {

    private static final String COLLECTION_NAME = "WEB_PAGES";
    private final QdrantClient client;

    public QdrantVectorStore() throws ExecutionException, InterruptedException {
        this.client = new QdrantClient(
                QdrantGrpcClient.newBuilder("qdrant", 6334, false)
                        .build()
        );
        createCollectionIfNotExists();
    }

    private void createCollectionIfNotExists() throws ExecutionException, InterruptedException {

        Boolean isCollectionPresent = client.collectionExistsAsync(COLLECTION_NAME).get();
        if (!isCollectionPresent) {
            Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                    .setDistance(Collections.Distance.Cosine)
                    .setSize(384)
                    .build();

            client.createCollectionAsync(COLLECTION_NAME, vectorParams).get();
        }
    }

    @Override
    public void insert(UUID id, float[] embedding, Map<String, Object> metadata) {

        Points.PointStruct point = Points.PointStruct.newBuilder()
                .setId(
                        Points.PointId.newBuilder()
                                .setUuid(id.toString())
                                .build()
                )
                .putAllPayload(
                        Map.of(
                                "title", value(metadata.getOrDefault("title", "").toString()),
                                "content", value(metadata.getOrDefault("content", "").toString()),
                                "url", value(metadata.getOrDefault("url", "").toString()))
                )
                .setVectors(vectors(embedding))
                .build();

        try {
            log.info("Inserting record {}", point);
            client.upsertAsync(COLLECTION_NAME, List.of(point)).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error while inserting vector", e);
        }
    }

    @Override
    public List<Map<String, Object>> query(List<Float> embedding, int topK) {
        try {
            List<Points.ScoredPoint> scoredPoints = client.searchAsync(
                            Points.SearchPoints.newBuilder()
                                    .setCollectionName(COLLECTION_NAME)
                                    .addAllVector(embedding)
                                    .setLimit(topK)
                                    .setWithPayload(
                                            Points.WithPayloadSelector.newBuilder()
                                                    .setEnable(true)
                                                    .build())
                                    .build())
                    .get();

            log.info("Found {} results", scoredPoints);

            return scoredPoints.stream()
                    .filter(x -> x.getScore() > 0)
                    .map(Points.ScoredPoint::getPayloadMap)
                    .map(payload -> {
                        Map<String, Object> objectObjectHashMap = new HashMap<>();
                        objectObjectHashMap.put("title", payload.get("title").getStringValue());
                        objectObjectHashMap.put("content", payload.get("content").getStringValue());
                        objectObjectHashMap.put("url", payload.get("url").getStringValue());
                        return objectObjectHashMap;
                    })
                    .toList();

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
