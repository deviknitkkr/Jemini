package com.devik.scraper;

import com.devik.model.Article;
import com.devik.model.ScrapedModel;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JsoupScraper implements Scrapper {

    @Override
    public Optional<ScrapedModel> scrap(String url) {

        try {
            Document doc = Jsoup.connect(url).get();

            final String content = doc.select("p").stream()
                    .map(Element::text)
                    .filter( x -> !x.isEmpty())
                    .limit(1)
                    .collect(Collectors.joining("\n"));

            final List<String> links = doc.select("a[href]").stream()
                    .map(link -> link.attr("abs:href"))
                    .toList();

            return Optional.of(
                    ScrapedModel.builder()
                            .article(Article.builder()
                                    .url(url)
                                    .title(doc.title())
                                    .content(content)
                                    .build())
                            .links(links)
                            .build()
            );

        } catch (Exception ex) {
            log.error("Exception while scraping {}:{}", url, ex);
        }
        return Optional.empty();
    }
}
