package com.devik.model;

import lombok.Data;

@Data
public class CrawlRequest {
    private String url;
    private int depth;
}
