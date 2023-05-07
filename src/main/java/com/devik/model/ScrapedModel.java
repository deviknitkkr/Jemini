package com.devik.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ScrapedModel {
    private Article article;
    private List<String> links;
}
