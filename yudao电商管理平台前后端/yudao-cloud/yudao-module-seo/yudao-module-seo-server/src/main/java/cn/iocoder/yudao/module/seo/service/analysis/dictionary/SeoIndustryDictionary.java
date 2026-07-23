package cn.iocoder.yudao.module.seo.service.analysis.dictionary;

import cn.iocoder.yudao.module.seo.service.analysis.lexical.SeoTextNormalizer;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SeoIndustryDictionary {

    @Getter
    private final String version = "furniture-dictionary-v1";

    private final SeoTextNormalizer normalizer;
    private final List<Set<String>> synonymGroups = new ArrayList<>();
    private final Map<String, List<Set<String>>> facets = new LinkedHashMap<>();

    public SeoIndustryDictionary(SeoTextNormalizer normalizer) {
        this.normalizer = normalizer;
        Set<String> solidWood = group("实木", "原木", "solid wood", "hardwood");
        Set<String> diningTable = group("餐桌", "饭桌", "dining table");
        Set<String> sofa = group("沙发", "sofa", "couch");
        Set<String> chair = group("餐椅", "椅子", "chair", "dining chair");
        Set<String> oak = group("橡木", "oak");
        Set<String> walnut = group("胡桃木", "walnut");
        Set<String> modern = group("现代", "modern", "contemporary");
        Set<String> nordic = group("北欧", "scandinavian", "nordic");
        Set<String> livingRoom = group("客厅", "living room", "lounge");
        Set<String> bedroom = group("卧室", "bedroom");
        Set<String> custom = group("定制", "custom", "bespoke", "made to order");
        Set<String> delivery = group("配送", "送货", "delivery", "shipping");
        Set<String> care = group("保养", "护理", "care", "maintenance");

        synonymGroups.addAll(List.of(solidWood, diningTable, sofa, chair, oak, walnut,
                modern, nordic, livingRoom, bedroom, custom, delivery, care));
        facets.put("CATEGORY", List.of(diningTable, sofa, chair));
        facets.put("MATERIAL", List.of(solidWood, oak, walnut));
        facets.put("STYLE", List.of(modern, nordic));
        facets.put("ROOM", List.of(livingRoom, bedroom));
        facets.put("CUSTOMIZATION", List.of(custom));
        facets.put("DELIVERY", List.of(delivery));
        facets.put("CARE", List.of(care));
    }

    public Set<String> variants(String keyword) {
        String normalizedKeyword = normalizer.normalize(keyword);
        Set<String> variants = new LinkedHashSet<>();
        for (Set<String> group : synonymGroups) {
            if (group.stream().anyMatch(term -> normalizer.containsPhrase(normalizedKeyword, term))) {
                group.stream()
                        .map(normalizer::normalize)
                        .filter(term -> !normalizer.containsPhrase(normalizedKeyword, term))
                        .forEach(variants::add);
            }
        }
        return variants;
    }

    public Map<String, Set<String>> expectedFacets(String keyword) {
        String normalizedKeyword = normalizer.normalize(keyword);
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        facets.forEach((facet, groups) -> {
            Set<String> terms = new LinkedHashSet<>();
            groups.stream()
                    .filter(group -> group.stream().anyMatch(term -> normalizer.containsPhrase(normalizedKeyword, term)))
                    .forEach(terms::addAll);
            if (!terms.isEmpty()) {
                expected.put(facet, terms);
            }
        });
        return expected;
    }

    private Set<String> group(String... values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(normalizer.normalize(value));
        }
        return result;
    }

}
