package cn.iocoder.yudao.module.seo.service.analysis.lexical;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeoTextNormalizerTest {

    private final SeoTextNormalizer normalizer = new SeoTextNormalizer();

    @Test
    void normalize_shouldApplyNfkcCaseHtmlPunctuationAndWhitespaceRules() {
        assertThat(normalizer.normalize(" <b>ＯＡＫ</b>　Dining-Table!  "))
                .isEqualTo("oak dining table");
    }

    @Test
    void containsPhrase_shouldMatchChineseWithoutSpacesAndEnglishByWordBoundary() {
        assertThat(normalizer.containsPhrase("北欧实木 餐桌 1.8m", "实木餐桌")).isTrue();
        assertThat(normalizer.containsPhrase("Premium solid wood dining table", "solid wood")).isTrue();
        assertThat(normalizer.containsPhrase("The consolidated woodland", "solid wood")).isFalse();
    }

    @Test
    void tokens_shouldProtectFullValueAndExposeChineseBigrams() {
        assertThat(normalizer.tokens("实木餐桌 1.8m"))
                .contains("实木餐桌", "实木", "餐桌", "1", "8m");
    }

}
