package com.devik.crawl.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.devik.crawl.Crawler;
import com.devik.model.Article;
import com.devik.util.Constants;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component("ElasticCrawler")
@Slf4j
public class ElasticCrawler implements Crawler {

    private final ElasticsearchClient client;
    private final RestClient restClient;


    public ElasticCrawler(ElasticsearchClient client, RestClient restClient) {
        this.client = client;
        this.restClient = restClient;
    }

    /**
     * @param article New Article which needs to be indexed
     */
    @SneakyThrows
    @Override
    public void add(Article article) {
        IndexResponse response = client.index(i -> i
                .index(Constants.indexName)
                .id(article.getUrl())
                .document(article)
        );

        log.info("Indexed with version " + response.version());
    }

    /**
     * @param textToSearch query
     * @return Top 10 results
     */
    @SneakyThrows
    @Override
    public List<Article> search(String textToSearch) {


        SearchResponse<Article> response = client.search(s -> s
                        .index(Constants.indexName)
                        .query(q -> q
                                .multiMatch(t -> t
                                        .fields(List.of(Constants.titleField, Constants.contentField))
                                        .query(textToSearch)
                                        .fuzziness("1")
                                )
                        ),
                Article.class
        );

        return response.hits().hits()
                .stream()
                .limit(10)
                .map(Hit::source)
                .collect(Collectors.toList());
    }


    @SneakyThrows
    @Override
    @Scheduled(cron = "0/30 * * * * *")
    public void printIndexedItemCount() {
        log.info("Total number of indexed items: {}", client.count());
    }
}
