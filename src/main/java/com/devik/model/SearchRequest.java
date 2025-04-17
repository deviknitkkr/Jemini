package com.devik.model;

import lombok.Data;

@Data
public class SearchRequest {
    private String query;
    private int limit = 10;
    private int page = 0;
}
