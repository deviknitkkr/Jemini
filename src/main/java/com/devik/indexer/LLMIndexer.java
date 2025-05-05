package com.devik.indexer;

import com.devik.model.CrawlResult;
import com.devik.model.SearchRequest;
import com.devik.repository.vector.VectorStore;
import com.devik.service.embedder.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LLMIndexer implements IndexerStrategy {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    @Autowired
    public LLMIndexer(EmbeddingService embeddingService, VectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    @Override
    public void indexDocument(CrawlResult result) {

        log.info("Indexing document:{}", result.getUrl());
        // 1. Generate embedding for the document content
        String content = result.getContent().substring(0, Math.min(result.getContent().length(), 1000));
        List<Float> embedding = embeddingService.embed(content);

        // 2. Save embedding and metadata into the vector store
        Map<String, Object> metadata = Map.of(
                "url", result.getUrl(),
                "title", result.getTitle(),
                "content", content,
                "crawledAt", result.getCrawledAt().toString()
        );

        vectorStore.insert(result.getId(), toPrimitiveArray(embedding), metadata);
    }

    @Override
    public List<Map<String, Object>> search(SearchRequest request) {
        List<Float> queryEmbedding = embeddingService.embed(request.getQuery());
        return vectorStore.query(queryEmbedding, request.getLimit());
    }

    private float[] toPrimitiveArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
