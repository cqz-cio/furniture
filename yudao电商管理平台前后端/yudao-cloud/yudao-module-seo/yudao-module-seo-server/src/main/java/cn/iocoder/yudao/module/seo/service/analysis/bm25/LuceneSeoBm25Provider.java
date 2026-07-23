package cn.iocoder.yudao.module.seo.service.analysis.bm25;

import cn.iocoder.yudao.module.seo.config.SeoAnalysisProperties;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoAnalysisContext;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoProviderScore;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class LuceneSeoBm25Provider implements SeoBm25Provider, AutoCloseable {

    public static final String VERSION = "lucene-bm25-8.11.1";

    private static final String DOCUMENT_KEY_FIELD = "document_key";
    private static final String CONTENT_FIELD = "content";

    private final Path indexRoot;
    private final int minimumCorpusSize;
    private final int maxHits;
    private final Analyzer analyzer = new CJKAnalyzer();
    private final Map<Path, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    public LuceneSeoBm25Provider(SeoAnalysisProperties.Bm25 properties) {
        this.indexRoot = Path.of(properties.getIndexPath()).toAbsolutePath().normalize();
        this.minimumCorpusSize = Math.max(1, properties.getMinimumCorpusSize());
        this.maxHits = Math.max(1, properties.getMaxHits());
    }

    @Override
    public void index(SeoAnalysisContext context, SeoContentSnapshot snapshot) {
        if (context == null || !context.isIndexable() || snapshot == null || snapshot.isEmpty()) {
            return;
        }
        Path partition = partitionPath(context);
        ReentrantReadWriteLock.WriteLock lock = lockFor(partition).writeLock();
        lock.lock();
        try {
            Files.createDirectories(partition);
            try (Directory directory = FSDirectory.open(partition);
                 IndexWriter writer = new IndexWriter(directory, writerConfig())) {
                Document document = new Document();
                document.add(new StringField(DOCUMENT_KEY_FIELD, context.documentKey(), Field.Store.YES));
                document.add(new TextField(CONTENT_FIELD, snapshot.visibleText(), Field.Store.NO));
                writer.updateDocument(new Term(DOCUMENT_KEY_FIELD, context.documentKey()), document);
                writer.commit();
            }
        } catch (IOException ex) {
            log.warn("[index][partition({}) documentKey({}) BM25 索引更新失败]",
                    partition, context.documentKey(), ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SeoProviderScore calculate(String keyword, SeoAnalysisContext context,
                                      SeoContentSnapshot snapshot) {
        if (context == null || !context.isIndexable()) {
            return unavailable("缺少租户、站点、语言或实体标识，无法选择隔离的 BM25 索引分区",
                    Map.of());
        }
        Path partition = partitionPath(context);
        if (!Files.isDirectory(partition)) {
            return unavailable("当前站点和语言尚未建立 BM25 索引", Map.of("corpusSize", 0));
        }
        ReentrantReadWriteLock.ReadLock lock = lockFor(partition).readLock();
        lock.lock();
        try (Directory directory = FSDirectory.open(partition)) {
            if (!DirectoryReader.indexExists(directory)) {
                return unavailable("当前站点和语言尚未建立 BM25 索引", Map.of("corpusSize", 0));
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                int corpusSize = reader.numDocs();
                if (corpusSize < minimumCorpusSize) {
                    return unavailable("BM25 语料数量不足，达到最低数量后会自动参与评分",
                            Map.of("corpusSize", corpusSize, "minimumCorpusSize", minimumCorpusSize));
                }
                return calculateFromReader(keyword, context, reader, corpusSize);
            }
        } catch (Exception ex) {
            log.warn("[calculate][partition({}) keyword({}) BM25 评分失败]", partition, keyword, ex);
            return unavailable("BM25 索引暂时不可用，本次按其他词法分项归一化",
                    Map.of("errorType", ex.getClass().getSimpleName()));
        } finally {
            lock.unlock();
        }
    }

    private SeoProviderScore calculateFromReader(String keyword, SeoAnalysisContext context,
                                                 DirectoryReader reader, int corpusSize) throws Exception {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());
        Query keywordQuery = new QueryParser(CONTENT_FIELD, analyzer)
                .parse(QueryParser.escape(keyword));
        TopDocs currentDocument = searcher.search(
                new TermQuery(new Term(DOCUMENT_KEY_FIELD, context.documentKey())), 1);
        if (currentDocument.scoreDocs.length == 0) {
            return unavailable("当前内容尚未进入 BM25 索引",
                    Map.of("corpusSize", corpusSize));
        }
        int currentDocumentId = currentDocument.scoreDocs[0].doc;
        Explanation explanation = searcher.explain(keywordQuery, currentDocumentId);
        float currentScore = explanation.isMatch() ? explanation.getValue().floatValue() : 0F;
        TopDocs ranked = searcher.search(keywordQuery, Math.min(maxHits, corpusSize));
        float topScore = ranked.scoreDocs.length == 0 ? 0F : ranked.scoreDocs[0].score;
        int rank = rankOf(ranked.scoreDocs, currentDocumentId);
        int percent = topScore <= 0F ? 0 : clamp(Math.round(currentScore * 100F / topScore));

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("provider", "LUCENE");
        evidence.put("corpusSize", corpusSize);
        evidence.put("minimumCorpusSize", minimumCorpusSize);
        evidence.put("currentRawScore", decimal(currentScore));
        evidence.put("topRawScore", decimal(topScore));
        evidence.put("rank", rank == 0 ? null : rank);
        return SeoProviderScore.available(percent, VERSION,
                currentScore > 0F ? "已按当前租户、站点和语言的站内语料完成 BM25 排名评分"
                        : "当前内容在站内语料中未命中该关键词",
                evidence);
    }

    private IndexWriterConfig writerConfig() {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        config.setSimilarity(new BM25Similarity());
        return config;
    }

    private Path partitionPath(SeoAnalysisContext context) {
        String locale = context.getLocale().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_");
        return indexRoot.resolve("tenant-" + context.getTenantId())
                .resolve("site-" + context.getSiteId())
                .resolve(locale)
                .normalize();
    }

    private ReentrantReadWriteLock lockFor(Path path) {
        return locks.computeIfAbsent(path, ignored -> new ReentrantReadWriteLock());
    }

    private static int rankOf(ScoreDoc[] scoreDocs, int documentId) {
        for (int index = 0; index < scoreDocs.length; index++) {
            if (scoreDocs[index].doc == documentId) {
                return index + 1;
            }
        }
        return 0;
    }

    private static BigDecimal decimal(float value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private SeoProviderScore unavailable(String reason, Map<String, Object> evidence) {
        return SeoProviderScore.unavailable(VERSION, reason, evidence);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public void close() {
        analyzer.close();
    }

}
