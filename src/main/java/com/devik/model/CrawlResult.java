package com.devik.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CrawlResult {
    private String id;
    private String url;
    private String title;
    private String content;
    private List<String> links;
    private LocalDateTime crawledAt;

    public static CrawlResult create(String url, String title, String content, List<String> links) {
        return CrawlResult.builder()
                .id(UUID.randomUUID().toString())
                .url(url)
                .title(title)
                .content(content)
                .links(links)
                .crawledAt(LocalDateTime.now())
                .build();
    }
}