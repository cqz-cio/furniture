package cn.iocoder.yudao.module.seo.service.analysis.engine;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SeoKeywordScorer {

    public static final Map<String, Integer> DIMENSION_WEIGHTS = Map.of(
            "KEY_POSITION", 25,
            "LEXICAL", 20,
            "SEMANTIC", 25,
            "DISTRIBUTION", 15,
            "INTENT", 15);

    public int weightedPercent(Map<String, Integer> dimensionPercents) {
        BigDecimal weighted = BigDecimal.ZERO;
        int availableWeight = 0;
        for (Map.Entry<String, Integer> dimension : dimensionPercents.entrySet()) {
            if (dimension.getValue() == null) {
                continue;
            }
            int weight = DIMENSION_WEIGHTS.getOrDefault(dimension.getKey(), 0);
            weighted = weighted.add(BigDecimal.valueOf(dimension.getValue()).multiply(BigDecimal.valueOf(weight)));
            availableWeight += weight;
        }
        if (availableWeight == 0) {
            return 0;
        }
        return weighted.divide(BigDecimal.valueOf(availableWeight), 0, RoundingMode.HALF_UP).intValue();
    }

    public int availableWeight(Map<String, Integer> dimensionPercents) {
        return dimensionPercents.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .mapToInt(entry -> DIMENSION_WEIGHTS.getOrDefault(entry.getKey(), 0))
                .sum();
    }

    public Map<String, Integer> dimensions(Integer keyPosition, Integer lexical, Integer semantic,
                                            Integer distribution, Integer intent) {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("KEY_POSITION", keyPosition);
        dimensions.put("LEXICAL", lexical);
        dimensions.put("SEMANTIC", semantic);
        dimensions.put("DISTRIBUTION", distribution);
        dimensions.put("INTENT", intent);
        return dimensions;
    }

}
