package com.devik.service;

import com.devik.crawl.Crawler;
import com.devik.model.Article;
import com.devik.queue.MessagePublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private final MessagePublisher publisher;
    private final Crawler crawler;

    public SearchService(MessagePublisher publisher, Crawler crawler) {
        this.publisher = publisher;
        this.crawler = crawler;
    }

    @Async
    public void submitCrawlRequest(List<String> urls) {
        urls.forEach(publisher::publish);
    }

    public List<Article> search(String query) {
        return crawler.search(query);
    }
}
