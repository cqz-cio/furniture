package cn.iocoder.yudao.module.seo.config;

import cn.iocoder.yudao.module.seo.service.analysis.bm25.DisabledSeoBm25Provider;
import cn.iocoder.yudao.module.seo.service.analysis.bm25.LuceneSeoBm25Provider;
import cn.iocoder.yudao.module.seo.service.analysis.bm25.SeoBm25Provider;
import cn.iocoder.yudao.module.seo.service.analysis.semantic.BgeM3SeoSemanticSimilarityProvider;
import cn.iocoder.yudao.module.seo.service.analysis.semantic.DisabledSeoSemanticSimilarityProvider;
import cn.iocoder.yudao.module.seo.service.analysis.semantic.SeoSemanticSimilarityProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SeoAnalysisProperties.class)
public class SeoAnalysisConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "yudao.seo.analysis.bm25", name = "enabled", havingValue = "true")
    public SeoBm25Provider luceneSeoBm25Provider(SeoAnalysisProperties properties) {
        return new LuceneSeoBm25Provider(properties.getBm25());
    }

    @Bean
    @ConditionalOnMissingBean(SeoBm25Provider.class)
    public SeoBm25Provider disabledSeoBm25Provider() {
        return new DisabledSeoBm25Provider();
    }

    @Bean
    @ConditionalOnProperty(prefix = "yudao.seo.analysis.semantic", name = "enabled", havingValue = "true")
    public SeoSemanticSimilarityProvider bgeM3SeoSemanticSimilarityProvider(
            SeoAnalysisProperties properties) {
        return new BgeM3SeoSemanticSimilarityProvider(properties.getSemantic(), RestClient.builder());
    }

    @Bean
    @ConditionalOnMissingBean(SeoSemanticSimilarityProvider.class)
    public SeoSemanticSimilarityProvider disabledSeoSemanticSimilarityProvider() {
        return new DisabledSeoSemanticSimilarityProvider();
    }

}
