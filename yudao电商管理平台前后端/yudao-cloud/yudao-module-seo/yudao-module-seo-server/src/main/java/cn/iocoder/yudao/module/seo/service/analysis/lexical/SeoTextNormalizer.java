package cn.iocoder.yudao.module.seo.service.analysis.lexical;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SeoTextNormalizer {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern CJK = Pattern.compile("[\\p{IsHan}]");

    public String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        normalized = HTML_TAG.matcher(normalized).replaceAll(" ").toLowerCase(Locale.ROOT);
        normalized = NON_WORD.matcher(normalized).replaceAll(" ");
        return WHITESPACE.matcher(normalized).replaceAll(" ").trim();
    }

    public boolean containsPhrase(String text, String keyword) {
        String normalizedText = normalize(text);
        String normalizedKeyword = normalize(keyword);
        if (normalizedText.isEmpty() || normalizedKeyword.isEmpty()) {
            return false;
        }
        if (containsCjk(normalizedKeyword)) {
            return compact(normalizedText).contains(compact(normalizedKeyword));
        }
        return (" " + normalizedText + " ").contains(" " + normalizedKeyword + " ");
    }

    public int countPhrase(String text, String keyword) {
        String normalizedText = normalize(text);
        String normalizedKeyword = normalize(keyword);
        if (normalizedText.isEmpty() || normalizedKeyword.isEmpty()) {
            return 0;
        }
        String haystack = containsCjk(normalizedKeyword) ? compact(normalizedText) : " " + normalizedText + " ";
        String needle = containsCjk(normalizedKeyword) ? compact(normalizedKeyword) : " " + normalizedKeyword + " ";
        int count = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) >= 0) {
            count++;
            from += Math.max(needle.length(), 1);
        }
        return count;
    }

    public List<String> tokens(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.isBlank()) {
                continue;
            }
            tokens.add(token);
            if (containsCjk(token) && token.length() > 2) {
                for (int i = 0; i < token.length() - 1; i++) {
                    tokens.add(token.substring(i, i + 2));
                }
            }
        }
        return new ArrayList<>(tokens);
    }

    public String compact(String value) {
        return normalize(value).replace(" ", "");
    }

    private static boolean containsCjk(String value) {
        return CJK.matcher(value).find();
    }

}
