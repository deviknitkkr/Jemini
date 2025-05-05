package com.devik.service;

import com.devik.indexer.ElasticSearchIndexer;
import com.devik.indexer.IndexerStrategy;
import com.devik.indexer.LLMIndexer;
import com.devik.model.CrawlRequest;
import com.devik.model.CrawlResult;
import com.devik.model.SearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CrawlerService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String queueName;
    private final IndexerStrategy indexerStrategy;
    private final FilteringService filteringService;
    private final RedisTemplate<String, String> redisTemplate;

    // Common user agents for rotation
    private final List<String> userAgents = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/117.0"
    );

    // Redis key prefixes
    @Value("${crawler.redis.processing-prefix:processing:}")
    private String processingKeyPrefix;

    @Value("${crawler.redis.visited-prefix:visited:}")
    private String visitedKeyPrefix;

    @Value("${crawler.redis.domain-headers-prefix:domainheaders:}")
    private String domainHeadersPrefix;

    @Value("${crawler.redis.ttl.processing:600}")
    private int processingTtlSeconds;

    @Value("${crawler.redis.ttl.visited:86400}")
    private int visitedTtlSeconds;

    @Value("${crawler.redis.ttl.domain-headers:1800}")
    private int domainHeadersTtlSeconds;

    @Value("${crawler.connection.timeout:10000}")
    private int connectionTimeout;

    @Value("${crawler.max.links.per.page:25}")
    private int maxLinksPerPage;

    @Value("${crawler.robots.respect:true}")
    private boolean respectRobotsTxt;

    @Autowired
    public CrawlerService(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            RedisTemplate<String, String> redisTemplate,
            @Value("${crawler.queue.name}") String queueName,
            @Value("${crawler.index.strategy}") String indexStrategy,
            ElasticSearchIndexer elasticSearchIndexer,
            LLMIndexer llmIndexer,
            FilteringService filteringService
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.queueName = queueName;
        this.redisTemplate = redisTemplate;

        // Select indexer strategy based on configuration
        this.indexerStrategy = "elasticsearch".equals(indexStrategy) ?
                elasticSearchIndexer : llmIndexer;
        this.filteringService = filteringService;
    }

    @Async
    public void submitCrawlTask(CrawlRequest request) {
        try {
            // First normalize the URL using the FilteringService's method
            String normalizedUrl = filteringService.normalizeUrl(request.getUrl());
            request.setUrl(normalizedUrl);

            // Check Redis to see if URL is already visited
            String visitedKey = visitedKeyPrefix + normalizedUrl;
            Boolean isVisited = redisTemplate.hasKey(visitedKey);

            if (Boolean.TRUE.equals(isVisited)) {
                log.debug("Skipping already visited URL: {}", normalizedUrl);
                return;
            }

            // Check Redis to see if URL is already being processed elsewhere
            String processingKey = processingKeyPrefix + normalizedUrl;
            Boolean isProcessing = redisTemplate.hasKey(processingKey);

            if (Boolean.TRUE.equals(isProcessing)) {
                log.debug("Skipping URL already being processed: {}", normalizedUrl);
                return;
            }

            // Check if we should crawl this URL according to filtering rules
            if (!filteringService.shouldCrawl(normalizedUrl)) {
                log.debug("Skipping filtered URL: {}", normalizedUrl);
                return;
            }

            // Mark as processing in Redis with TTL
            redisTemplate.opsForValue().set(processingKey, "1", Duration.ofSeconds(processingTtlSeconds));

            String message = objectMapper.writeValueAsString(request);
            rabbitTemplate.convertAndSend(queueName, message);
            log.debug("Submitted for crawling: {}", normalizedUrl);
        } catch (Exception e) {
            log.error("Failed to submit crawl task for {}: {}", request.getUrl(), e.getMessage());
            // Clean up processing status
            redisTemplate.delete(processingKeyPrefix + request.getUrl());
        }
    }

    @RabbitListener(queues = "${crawler.queue.name}")
    public void processCrawlTask(String message) {
        try {
            CrawlRequest request = objectMapper.readValue(message, CrawlRequest.class);
            crawlUrl(request.getUrl(), request.getDepth());
        } catch (Exception e) {
            log.error("Failed to process message from queue: {}", e.getMessage());
        }
    }

    /**
     * Extract domain from URL
     */
    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String domain = uri.getHost().toLowerCase();

            // Remove www. prefix if present
            if (domain.startsWith("www.")) {
                domain = domain.substring(4);
            }

            return domain;
        } catch (URISyntaxException e) {
            // Fallback to simple parsing
            String domain = url.toLowerCase();
            if (domain.startsWith("http://")) domain = domain.substring(7);
            if (domain.startsWith("https://")) domain = domain.substring(8);
            if (domain.startsWith("www.")) domain = domain.substring(4);

            int slashIndex = domain.indexOf('/');
            if (slashIndex > 0) {
                domain = domain.substring(0, slashIndex);
            }

            return domain;
        }
    }

    /**
     * Get domain headers from Redis
     */
    private Map<String, String> getDomainHeaders(String domain) {
        String headerKey = domainHeadersPrefix + domain;
        Map<String, String> headers = new HashMap<>();

        Map<Object, Object> redisMap = redisTemplate.opsForHash().entries(headerKey);
        if (redisMap != null && !redisMap.isEmpty()) {
            redisMap.forEach((k, v) -> headers.put(k.toString(), v.toString()));
        }

        return headers;
    }

    /**
     * Save domain headers to Redis
     */
    private void saveDomainHeaders(String domain, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return;

        String headerKey = domainHeadersPrefix + domain;

        // Convert to Map<Object, Object> for Redis hash operations
        Map<String, String> filteredHeaders = headers.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .filter(e -> !e.getKey().equalsIgnoreCase("content-length"))
                .filter(e -> !e.getKey().equalsIgnoreCase("connection"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        redisTemplate.opsForHash().putAll(headerKey, new HashMap<>(filteredHeaders));
        redisTemplate.expire(headerKey, Duration.ofSeconds(domainHeadersTtlSeconds));
    }

    private void crawlUrl(String url, int depth) {
        if (depth <= 0) return;

        // Normalize the URL again to ensure consistency
        String normalizedUrl = filteringService.normalizeUrl(url);
        String processingKey = processingKeyPrefix + normalizedUrl;
        String visitedKey = visitedKeyPrefix + normalizedUrl;

        try {
            // Double-check we haven't already visited this URL (race condition)
            Boolean isVisited = redisTemplate.hasKey(visitedKey);
            if (Boolean.TRUE.equals(isVisited)) {
                log.debug("Skipping already visited URL (race condition check): {}", normalizedUrl);
                redisTemplate.delete(processingKey); // Clean up processing marker
                return;
            }

            // Mark URL as visited with TTL
            redisTemplate.opsForValue().set(visitedKey, "1", Duration.ofSeconds(visitedTtlSeconds));

            // Get domain for domain-specific headers
            String domain = extractDomain(normalizedUrl);

            // Get headers map for this domain from Redis
            Map<String, String> headers = getDomainHeaders(domain);

            // Select a random user agent for this request
            String userAgent = userAgents.get(new Random().nextInt(userAgents.size()));

            // Set up the connection with appropriate headers
            Connection connection = Jsoup.connect(normalizedUrl)
                    .userAgent(userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .referrer("https://www.google.com/search?q=" + domain.replace('.', '+'))
                    .timeout(connectionTimeout)
                    .followRedirects(true)
                    .ignoreHttpErrors(true);

            // Add any domain-specific headers we've collected from Redis
            headers.forEach(connection::header);

            // Execute the request
            Connection.Response response = connection.execute();

            // Store response headers for future requests to this domain
            // This helps with cookies and session management
            Map<String, String> responseHeaders = response.headers();
            headers.putAll(responseHeaders);
            saveDomainHeaders(domain, headers);

            // Check if we got a successful response
            if (response.statusCode() != 200) {
                log.warn("Non-200 status code ({}) for URL: {}", response.statusCode(), normalizedUrl);

                // For certain status codes, we might want to retry or handle differently
                if (response.statusCode() == 429 || response.statusCode() == 503) {
                    log.warn("Rate limiting detected for domain: {}", domain);
                    // We don't delete the processing key to prevent immediate retry
                    return;
                }

                // For redirects
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    String location = response.header("Location");
                    if (location != null && !location.isEmpty()) {
                        log.info("Found redirect from {} to {}", normalizedUrl, location);
                        // Consider adding the redirect target to the crawl queue
                        CrawlRequest redirectRequest = new CrawlRequest();
                        redirectRequest.setUrl(location);
                        redirectRequest.setDepth(depth);
                        submitCrawlTask(redirectRequest);
                    }
                }

                // Delete processing key to allow retry later for some errors
                if (response.statusCode() >= 500) {
                    redisTemplate.delete(processingKey);
                }

                return;
            }

            // Parse the document
            Document doc = response.parse();
            String title = doc.title();
            String content = doc.body().text();

            // Extract all links from the page
            List<String> allLinks = doc.select("a[href]").stream()
                    .map(element -> element.attr("abs:href"))
                    .filter(href -> href.startsWith("http") || href.startsWith("https"))
                    .map(filteringService::normalizeUrl)  // Normalize all links
                    .filter(href -> !href.equals(normalizedUrl))  // Exclude self-links
                    .distinct()  // Remove duplicates
                    .collect(Collectors.toList());

            // Log the total number of links found
            log.debug("Found {} links on page: {}", allLinks.size(), normalizedUrl);

            // Create and index the crawl result with all links (for completeness)
            CrawlResult result = CrawlResult.create(normalizedUrl, title, content, allLinks);
            indexerStrategy.indexDocument(result);
            log.info("Successfully indexed: {}", normalizedUrl);

            // If we should continue crawling deeper
            if (depth > 1) {
                // Randomize the order of links to avoid predictable patterns
                Collections.shuffle(allLinks);

                // Process each link (Redis will handle deduplication)
                List<String> linksToProcess = allLinks.stream()
                        .limit(maxLinksPerPage)
                        .collect(Collectors.toList());

                log.debug("Queuing {} links for further crawling from {}", linksToProcess.size(), normalizedUrl);

                // Submit each link for crawling
                for (String link : linksToProcess) {
                    CrawlRequest childRequest = new CrawlRequest();
                    childRequest.setUrl(link);
                    childRequest.setDepth(depth - 1);
                    submitCrawlTask(childRequest);
                }
            }

            // Clean up processing key after successful processing
            redisTemplate.delete(processingKey);

        } catch (IOException e) {
            log.error("Error crawling {}: {}", normalizedUrl, e.getMessage());
            // Remove the URL from the "processing" set in Redis to allow retry later
            redisTemplate.delete(processingKey);
        }
    }

    public List<Map<String, Object>> search(SearchRequest request) {
        return indexerStrategy.search(request);
    }

    /**
     * Get statistics about crawling
     */
    public Map<String, Object> getCrawlStats() {
        Map<String, Object> stats = new HashMap<>();

        // Get counts from Redis
        int processingCount = redisTemplate.keys(processingKeyPrefix + "*").size();
        int visitedCount = redisTemplate.keys(visitedKeyPrefix + "*").size();
        int domainsTracked = redisTemplate.keys(domainHeadersPrefix + "*").size();

        stats.put("processingUrls", processingCount);
        stats.put("visitedUrls", visitedCount);
        stats.put("trackedDomains", domainsTracked);

        return stats;
    }

    /**
     * Reset crawl data
     */
    public void resetCrawlData() {
        // Clean up Redis keys
        Set<String> processingKeys = redisTemplate.keys(processingKeyPrefix + "*");
        Set<String> visitedKeys = redisTemplate.keys(visitedKeyPrefix + "*");
        Set<String> domainKeys = redisTemplate.keys(domainHeadersPrefix + "*");

        if (processingKeys != null && !processingKeys.isEmpty()) {
            redisTemplate.delete(processingKeys);
        }

        if (visitedKeys != null && !visitedKeys.isEmpty()) {
            redisTemplate.delete(visitedKeys);
        }

        if (domainKeys != null && !domainKeys.isEmpty()) {
            redisTemplate.delete(domainKeys);
        }

        log.info("Crawl data reset complete");
    }
}