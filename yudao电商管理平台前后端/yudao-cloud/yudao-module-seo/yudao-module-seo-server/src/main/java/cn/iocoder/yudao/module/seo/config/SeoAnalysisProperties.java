package cn.iocoder.yudao.module.seo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "yudao.seo.analysis")
@Data
public class SeoAnalysisProperties {

    private Bm25 bm25 = new Bm25();
    private Semantic semantic = new Semantic();
    private Document document = new Document();

    @Data
    public static class Bm25 {

        private boolean enabled;
        private String indexPath = System.getProperty("user.home") + "/.oakved/seo-bm25";
        private int minimumCorpusSize = 20;
        private int maxHits = 1000;

    }

    @Data
    public static class Semantic {

        private boolean enabled;
        private String baseUrl = "http://127.0.0.1:8000";
        private String apiKey = "";
        private String model = "BAAI/bge-m3";
        private String endpointPath = "/v1/embeddings";
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(8);
        private double minimumSimilarity = 0.25D;
        private double maximumSimilarity = 0.85D;
        private int maxContentChars = 12000;

    }

    @Data
    public static class Document {

        private DataSize maxFileSize = DataSize.ofMegabytes(16);
        private int maxExtractedChars = 200000;
        private Set<String> allowedExtensions = new LinkedHashSet<>(Set.of("docx", "pdf", "xlsx"));

    }

}
