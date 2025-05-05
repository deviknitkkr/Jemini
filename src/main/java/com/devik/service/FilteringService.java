package com.devik.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FilteringService {

    private final RedisTemplate redisTemplate;

    @Value("${crawler.redis.visited-prefix:visited:}")
    private String visitedKeyPrefix;

    @Value("${spring.redis.enabled:true:}")
    private Boolean isRedisEnabled;

    @Value("${crawler.filter.max-url-length:100}")
    private int maxUrlLength;

    private static final Duration TTL = Duration.ofHours(1);

    // Expanded list of blocked file extensions
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            // Documents
            ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx", ".odt", ".ods", ".odp",
            // Archives
            ".zip", ".tar", ".gz", ".rar", ".7z",
            // Media files
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".svg", ".webp", ".tiff",
            ".mp3", ".mp4", ".avi", ".mov", ".wmv", ".flv", ".wav", ".ogg", ".webm",
            // Executables and binaries
            ".exe", ".bin", ".dll", ".so", ".dmg", ".pkg", ".deb", ".rpm",
            // Other
            ".css", ".js", ".xml", ".rss", ".atom", ".json", ".csv", ".tsv"
    );

    // Common paths that usually don't contain valuable indexable content
    private static final Set<String> BLOCKED_PATHS = Set.of(
            "/wp-admin/", "/wp-includes/", "/wp-content/plugins/",
            "/admin/", "/login/", "/logout/", "/signin/", "/signout/",
            "/cart/", "/checkout/", "/account/", "/profile/",
            "/tags/", "/categories/", "/search/", "/print/", "/feed/",
            "/cgi-bin/", "/cdn-cgi/", "/.git/", "/node_modules/"
    );

    // Patterns for URLs that typically don't need to be crawled
    private static final Pattern[] BLOCKED_PATTERNS = {
            // URLs with too many query parameters (probably dynamic pages)
            Pattern.compile(".*\\?.*&.*&.*&.*&.*"),
            // Calendar and date-based archives
            Pattern.compile(".*/(?:19|20)\\d{2}/(?:0[1-9]|1[0-2])/.*"),
            // Session IDs in URLs
            Pattern.compile(".*(?:jsessionid|sessionid|sid|session_id)=.*"),
            // Common social media share parameters
            Pattern.compile(".*(?:utm_source|utm_medium|utm_campaign|fbclid|gclid)=.*"),
            // URLs with long numbers (likely product IDs or other dynamic content)
            Pattern.compile(".*/\\d{10,}.*"),
            // URLs with repetitive patterns (pagination, etc.)
            Pattern.compile(".*/page/\\d+/.*"),
            // Comment sections
            Pattern.compile(".*/comments/.*")
    };

    // Common domains that are typically not worth crawling
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
            "facebook.com", "twitter.com", "instagram.com", "youtube.com",
            "linkedin.com", "pinterest.com", "reddit.com", "tiktok.com",
            "doubleclick.net", "googleadservices.com", "analytics.google.com",
            "googleapis.com", "googlesyndication.com"
    );

    public boolean shouldCrawl(String url) {
        if (url == null || url.isBlank()) return false;

        // Filter out excessively long URLs
        if (url.length() > maxUrlLength) {
            return false;
        }

        String lowerUrl = url.toLowerCase();

        // Check blocked file extensions
        for (String ext : BLOCKED_EXTENSIONS) {
            if (lowerUrl.endsWith(ext)) {
                return false;
            }
        }

        // Check blocked path segments
        for (String path : BLOCKED_PATHS) {
            if (lowerUrl.contains(path.toLowerCase())) {
                return false;
            }
        }

        // Check blocked domains
        for (String domain : BLOCKED_DOMAINS) {
            // Match domain patterns like "www.domain.com", "domain.com", or "subdomain.domain.com"
            if (lowerUrl.matches("https?://(?:www\\.)?" + domain + ".*") ||
                    lowerUrl.matches("https?://.*\\." + domain + ".*")) {
                return false;
            }
        }

        // Check regex patterns
        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(url).matches()) {
                return false;
            }
        }

        // Filter fragment identifiers (they point to same content)
        if (lowerUrl.contains("#")) {
            // Only crawl the base URL without fragment
            String baseUrl = url.substring(0, url.indexOf('#'));
            // If we've already visited the base URL, don't crawl this fragment
            if (!shouldCrawlWithRedis(baseUrl)) {
                return false;
            }
        }

        // Check Redis for already visited URLs
        return shouldCrawlWithRedis(url);
    }

    private boolean shouldCrawlWithRedis(String url) {
        if (!isRedisEnabled) return true;

        // URL encoding for safe Redis key
        String redisKey = visitedKeyPrefix + URLEncoder.encode(url, StandardCharsets.UTF_8);

        // Check if URL was already visited
        Boolean alreadyVisited = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(alreadyVisited)) {
            return false;
        }

        // Mark as visited for 1 hour
        redisTemplate.opsForValue().set(redisKey, "1", TTL);

        return true;
    }

    /**
     * Normalizes a URL by removing common tracking parameters and standardizing the format
     * @param url The URL to normalize
     * @return The normalized URL
     */
    public String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return url;

        // Remove tracking parameters
        String normalized = url;

        // Remove trailing slashes
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Remove common tracking parameters
        if (normalized.contains("?")) {
            String base = normalized.substring(0, normalized.indexOf('?'));
            String queryString = normalized.substring(normalized.indexOf('?') + 1);

            // Split query parameters
            String[] params = queryString.split("&");
            StringBuilder newQuery = new StringBuilder();

            for (String param : params) {
                // Skip tracking parameters
                if (!param.startsWith("utm_") &&
                        !param.startsWith("fbclid=") &&
                        !param.startsWith("gclid=") &&
                        !param.startsWith("ref=") &&
                        !param.startsWith("source=")) {

                    if (newQuery.length() > 0) {
                        newQuery.append("&");
                    }
                    newQuery.append(param);
                }
            }

            if (newQuery.length() > 0) {
                normalized = base + "?" + newQuery;
            } else {
                normalized = base;
            }
        }

        return normalized;
    }
}