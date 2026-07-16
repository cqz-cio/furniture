package cn.iocoder.yudao.module.seo.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeoEnumTest {

    @Test
    void shouldExposeStableEntityTypeCodes() {
        assertThat(SeoEntityTypeEnum.values())
                .extracting(SeoEntityTypeEnum::getCode)
                .containsExactly("PRODUCT", "CATEGORY", "ARTICLE", "PAGE");
    }

    @Test
    void shouldValidateEntityTypeCaseSensitivelyAndNullSafely() {
        assertThat(SeoEntityTypeEnum.isValid("PRODUCT")).isTrue();
        assertThat(SeoEntityTypeEnum.isValid("product")).isFalse();
        assertThat(SeoEntityTypeEnum.isValid(null)).isFalse();
    }

    @Test
    void shouldExposeStablePublishStatusCodes() {
        assertThat(SeoPublishStatusEnum.values())
                .extracting(SeoPublishStatusEnum::getCode)
                .containsExactly("DRAFT", "PUBLISHED");
    }

    @Test
    void shouldValidatePublishStatusCaseSensitivelyAndNullSafely() {
        assertThat(SeoPublishStatusEnum.isValid("PUBLISHED")).isTrue();
        assertThat(SeoPublishStatusEnum.isValid("published")).isFalse();
        assertThat(SeoPublishStatusEnum.isValid(null)).isFalse();
    }

    @Test
    void shouldExposeReservedErrorCodeContracts() {
        assertThat(ErrorCodeConstants.SITE_CONFIG_NOT_EXISTS.getCode()).isEqualTo(1_070_001_000);
        assertThat(ErrorCodeConstants.SITE_CONFIG_NOT_EXISTS.getMsg()).isEqualTo("SEO 站点配置不存在");
        assertThat(ErrorCodeConstants.METADATA_NOT_EXISTS.getCode()).isEqualTo(1_070_002_000);
        assertThat(ErrorCodeConstants.METADATA_NOT_EXISTS.getMsg()).isEqualTo("SEO 元数据不存在");
        assertThat(ErrorCodeConstants.METADATA_DUPLICATE.getCode()).isEqualTo(1_070_002_001);
        assertThat(ErrorCodeConstants.METADATA_DUPLICATE.getMsg()).isEqualTo("该实体和语言的 SEO 元数据已存在");
        assertThat(ErrorCodeConstants.ENTITY_TYPE_INVALID.getCode()).isEqualTo(1_070_002_002);
        assertThat(ErrorCodeConstants.ENTITY_TYPE_INVALID.getMsg()).isEqualTo("SEO 实体类型不支持");
        assertThat(ErrorCodeConstants.METADATA_VERSION_CONFLICT.getCode()).isEqualTo(1_070_002_003);
        assertThat(ErrorCodeConstants.METADATA_VERSION_CONFLICT.getMsg())
                .isEqualTo("SEO 元数据已被其他用户修改，请刷新后重试");
        assertThat(ErrorCodeConstants.METADATA_IDENTITY_IMMUTABLE.getCode()).isEqualTo(1_070_002_004);
        assertThat(ErrorCodeConstants.METADATA_IDENTITY_IMMUTABLE.getMsg())
                .isEqualTo("SEO 元数据的站点、实体和语言不可修改");
        assertThat(ErrorCodeConstants.METADATA_CANONICAL_URL_INVALID.getCode()).isEqualTo(1_070_002_005);
        assertThat(ErrorCodeConstants.METADATA_CANONICAL_URL_INVALID.getMsg())
                .isEqualTo("Canonical URL 必须是有效的 HTTP(S) 绝对地址");
    }

}
