package com.devik.service.embedder;

import java.util.List;

public interface EmbeddingService {
    List<Float> embed(String text);
}
