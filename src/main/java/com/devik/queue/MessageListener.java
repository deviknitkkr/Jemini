package com.devik.queue;

import com.devik.crawl.Crawler;
import com.devik.model.Article;
import com.devik.model.ScrapedModel;
import com.devik.scraper.Scrapper;
import com.devik.service.SearchService;
import com.devik.util.Constants;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Optional;


@Component
@Slf4j
public record MessageListener(Scrapper scrapper,
                              @Qualifier(value = "ElasticCrawler") Crawler crawler,
                              SearchService searchService) {

    @RabbitListener(queues = Constants.queueName)
    public void handle(String url, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {

        log.info("Processing message:{}, tag:{}", url, tag);
        final Optional<ScrapedModel> optionalScrapedModel = scrapper.scrap(url);

        try {
            if (optionalScrapedModel.isPresent()) {
                final Article article = optionalScrapedModel.get().getArticle();
                if (!article.getContent().isEmpty()) {
                    crawler.add(article);
                }
                searchService.submitCrawlRequest(optionalScrapedModel.get().getLinks());
            }
            log.info("Message processed");
            //channel.basicAck(tag, false);
        } catch (Exception ex) {
            log.error("Error while handling the message:", ex);
        }

    }
}
