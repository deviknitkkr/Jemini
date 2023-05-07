package com.devik.crawl;

import com.devik.model.Article;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class LucenceCrawler implements Crawler {

    private final static String URL = "url";
    private final static String TITLE = "title";
    private final static String CONTENT = "content";
    private final static String INDEX_FILE = "indexes";

    private final Directory directory;
    private final File indexDir;

    public LucenceCrawler() throws IOException {
        this.indexDir = new File(INDEX_FILE);
        if (!indexDir.exists()) {
            indexDir.mkdirs();
        }

        this.directory = FSDirectory.open(indexDir.toPath());
    }

    /**
     * @param article New Article which needs to be indexed
     */
    @SneakyThrows
    @Override
    public void add(Article article) {
        log.info("Crawling:{}",article.getContent());
        IndexWriter indexWriter = new IndexWriter(FSDirectory.open(indexDir.toPath()),
                new IndexWriterConfig(new StandardAnalyzer()));

        Document doc = new Document();
        doc.add(new StringField(URL, article.getUrl(), Field.Store.YES));
        doc.add(new StringField(TITLE, article.getTitle(), Field.Store.YES));
        doc.add(new StringField(CONTENT, article.getContent(), Field.Store.YES));
        indexWriter.addDocument(doc);
        indexWriter.commit();
        indexWriter.close();
    }

    /**
     * @param textToSearch query
     * @return Top 10 results
     */
    @SneakyThrows
    @Override
    public List<Article> search(String textToSearch) {
        FuzzyQuery fuzzyQuery = new FuzzyQuery(new Term(CONTENT, textToSearch), 2);
        List<Article> results = new ArrayList<>();
        IndexSearcher indexSearcher = new IndexSearcher(DirectoryReader.open(directory));
        final ScoreDoc[] scoreDocs = indexSearcher.search(fuzzyQuery, 10).scoreDocs;
        for (ScoreDoc scoreDoc : scoreDocs) {
            final Document doc = indexSearcher.doc((scoreDoc.doc));
            results.add(Article.builder()
                    .url(doc.get(URL))
                    .title(doc.get(TITLE))
                    .content(doc.get(CONTENT))
                    .build());
        }
        return results;
    }

    @Scheduled(cron = "0/30 * * * * *")
    public void printIndexedItemCount() throws Exception {
        IndexReader indexReader = DirectoryReader.open(directory);
        int numDocs = indexReader.numDocs();
        log.info("Total number of indexed items: {}", numDocs);
        indexReader.close();
    }

}
