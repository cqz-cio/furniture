package cn.iocoder.yudao.module.seo.service.analysis.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class SeoContentSnapshot {

    private String seoTitle;
    private String h1;
    private String introduction;
    private String metaDescription;
    private String slug;
    private String body;
    private List<String> headings = new ArrayList<>();
    private List<String> paragraphs = new ArrayList<>();
    private Map<String, String> attributes = new LinkedHashMap<>();
    private List<String> imageAlts = new ArrayList<>();

    public Map<String, List<String>> locationTexts() {
        Map<String, List<String>> locations = new LinkedHashMap<>();
        addIfPresent(locations, "SEO_TITLE", seoTitle);
        addIfPresent(locations, "H1", h1);
        addIfPresent(locations, "INTRODUCTION", introduction);
        addIfPresent(locations, "META_DESCRIPTION", metaDescription);
        addIfPresent(locations, "SLUG", slug);
        addAllIfPresent(locations, "HEADING", headings);
        addIfPresent(locations, "BODY", body);
        addAllIfPresent(locations, "PARAGRAPH", paragraphs);
        addAllIfPresent(locations, "ATTRIBUTE", attributes == null ? null : attributes.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue()).toList());
        addAllIfPresent(locations, "IMAGE_ALT", imageAlts);
        return locations;
    }

    public String visibleText() {
        return locationTexts().values().stream()
                .flatMap(List::stream)
                .reduce("", (left, right) -> left + "\n" + right)
                .trim();
    }

    public boolean isEmpty() {
        return visibleText().isBlank();
    }

    private static void addIfPresent(Map<String, List<String>> target, String location, String value) {
        if (value != null && !value.isBlank()) {
            target.put(location, List.of(value));
        }
    }

    private static void addAllIfPresent(Map<String, List<String>> target, String location, List<String> values) {
        if (values == null) {
            return;
        }
        List<String> present = values.stream().filter(value -> value != null && !value.isBlank()).toList();
        if (!present.isEmpty()) {
            target.put(location, present);
        }
    }

}
