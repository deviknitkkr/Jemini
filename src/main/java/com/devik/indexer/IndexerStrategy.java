package com.devik.indexer;

import com.devik.model.CrawlResult;
import com.devik.model.SearchRequest;

import java.util.List;
import java.util.Map;

public interface IndexerStrategy {
    void indexDocument(CrawlResult result);
    List<Map<String, Object>> search(SearchRequest request);
}

