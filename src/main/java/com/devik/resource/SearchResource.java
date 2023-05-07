package com.devik.resource;

import com.devik.model.Article;
import com.devik.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public record SearchResource(SearchService service) {

    @PostMapping("/crawl")
    public void crawl(@RequestBody List<String> urls) {
        service.submitCrawlRequest(urls);
    }

    @GetMapping("/search")
    public List<Article> search(@RequestParam("q") String query) {
        return service.search(query);
    }
}
