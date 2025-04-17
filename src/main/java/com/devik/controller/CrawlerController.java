package com.devik.controller;

import com.devik.model.CrawlRequest;
import com.devik.model.SearchRequest;
import com.devik.service.CrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class CrawlerController {

    private final CrawlerService crawlerService;

    @Autowired
    public CrawlerController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @PostMapping("/crawl")
    public ResponseEntity<?> submitCrawlRequest(@RequestBody CrawlRequest request) {
        log.info("Received crawl query:{}", request);
        crawlerService.submitCrawlTask(request);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Crawl request accepted",
                "url", request.getUrl(),
                "depth", request.getDepth()
        ));
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody SearchRequest request) {
        log.info("Received search query:{}", request);
        var results = crawlerService.search(request);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/ping")
    public String ping(){
        return "pong";
    }
}
