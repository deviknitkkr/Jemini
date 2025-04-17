package com.devik.service;

import com.devik.indexer.ElasticSearchIndexer;
import com.devik.indexer.IndexerStrategy;
import com.devik.indexer.LLMIndexer;
import com.devik.model.CrawlRequest;
import com.devik.model.CrawlResult;
import com.devik.model.SearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CrawlerService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String queueName;
    private final IndexerStrategy indexerStrategy;

    @Autowired
    public CrawlerService(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${crawler.queue.name}") String queueName,
            @Value("${crawler.index.strategy}") String indexStrategy,
            ElasticSearchIndexer elasticSearchIndexer,
            LLMIndexer llmIndexer
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.queueName = queueName;

        // Select indexer strategy based on configuration
        this.indexerStrategy = "elasticsearch".equals(indexStrategy) ?
                elasticSearchIndexer : llmIndexer;
    }

    @Async
    public void submitCrawlTask(CrawlRequest request) {
        try {
            String message = objectMapper.writeValueAsString(request);
            rabbitTemplate.convertAndSend(queueName, message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to submit crawl task", e);
        }
    }

    @RabbitListener(queues = "${crawler.queue.name}")
    public void processCrawlTask(String message) {
        try {
            CrawlRequest request = objectMapper.readValue(message, CrawlRequest.class);
            crawlUrl(request.getUrl(), request.getDepth());
        } catch (Exception e) {
            log.error("Failed to process URL:", e);
        }
    }

    private void crawlUrl(String url, int depth) {
        if (depth < 0) return;

        try {
            // Fetch and parse the web page
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Connection", "keep-alive")
                    .referrer("https://www.google.com/")
                    .timeout(10000)
                    .followRedirects(true)
                    .ignoreHttpErrors(false)  // Change to true if you want to process pages even with HTTP errors
                    .get();

            String title = doc.title();
            String content = doc.body().text();

            // Filter links more carefully
            List<String> links = doc.select("a[href]").stream()
                    .map(element -> element.attr("abs:href"))
                    .filter(href -> href.startsWith("http") || href.startsWith("https"))
                    .filter(href -> !href.contains("#")) // Remove anchor links
                    .filter(href -> !href.contains("login") && !href.contains("sign") && !href.contains("auth"))  // Avoid login pages
                    .filter(href -> {
                        // Check file extensions to avoid downloading binary files
                        String lowerHref = href.toLowerCase();
                        return !lowerHref.endsWith(".pdf") && !lowerHref.endsWith(".jpg") &&
                                !lowerHref.endsWith(".png") && !lowerHref.endsWith(".zip");
                    })
                    .distinct()
                    .collect(Collectors.toList());

            // Create and index the crawl result
            CrawlResult result = CrawlResult.create(url, title, content, links);
            indexerStrategy.indexDocument(result);
            log.debug("Indexing done for:{}", url);

            // Submit child links for crawling if depth > 0
            if (depth > 0) {
                for (String link : links) {
                    CrawlRequest childRequest = new CrawlRequest();
                    childRequest.setUrl(link);
                    childRequest.setDepth(depth - 1);
                    submitCrawlTask(childRequest);
                }
            }
        } catch (IOException e) {
            // Log and continue - don't fail the entire crawl process for one page
            System.err.println("Failed to crawl URL: " + url + " - " + e.getMessage());
        }
    }

    public List<Map<String, Object>> search(SearchRequest request) {
        return indexerStrategy.search(request);
    }
}
