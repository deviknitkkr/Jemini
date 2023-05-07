package com.devik.scraper;

import com.devik.model.ScrapedModel;

import java.util.Optional;

public interface Scrapper {
    public Optional<ScrapedModel> scrap(String url);
}
