package com.devik.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Article {
    private String url;
    private String title;
    private String content;
}
