package cn.iocoder.yudao.module.seo.service.analysis.semantic;

import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;

@Component
public class DisabledSeoSemanticSimilarityProvider implements SeoSemanticSimilarityProvider {

    @Override
    public OptionalInt calculatePercent(String keyword, SeoContentSnapshot snapshot) {
        return OptionalInt.empty();
    }

    @Override
    public String getModelVersion() {
        return null;
    }

    @Override
    public String getUnavailableReason() {
        return "语义模型尚未配置，当前结果按可用的确定性分项归一化计算";
    }

}
