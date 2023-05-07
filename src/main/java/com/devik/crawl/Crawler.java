package com.devik.crawl;

import com.devik.model.Article;

import java.util.List;

public interface Crawler {

    /**
     * @param article New Article which needs to be indexed
     */
    public void add(Article article);

    /**
     * @param textToSearch query
     * @return Top 10 results
     */
    public List<Article> search(String textToSearch);

    public void printIndexedItemCount() throws Exception;

}
