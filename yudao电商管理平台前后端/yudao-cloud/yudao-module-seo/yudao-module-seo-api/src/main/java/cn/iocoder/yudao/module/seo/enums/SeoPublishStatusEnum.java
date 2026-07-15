package cn.iocoder.yudao.module.seo.enums;

/**
 * SEO metadata publication states exposed through the public API.
 */
public enum SeoPublishStatusEnum {

    DRAFT("DRAFT"),
    PUBLISHED("PUBLISHED");

    private final String code;

    SeoPublishStatusEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        for (SeoPublishStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

}
