package com.devik.repository.vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface VectorStore {
    void insert(UUID id, float[] embedding, Map<String, Object> metadata);

    List<Map<String, Object>> query(List<Float> embedding, int topK);
}

