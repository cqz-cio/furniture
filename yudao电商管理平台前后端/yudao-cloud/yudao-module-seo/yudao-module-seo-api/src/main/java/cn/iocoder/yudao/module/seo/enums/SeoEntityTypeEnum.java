package cn.iocoder.yudao.module.seo.enums;

/**
 * SEO metadata entity types exposed through the public API.
 */
public enum SeoEntityTypeEnum {

    PRODUCT("PRODUCT"),
    CATEGORY("CATEGORY"),
    ARTICLE("ARTICLE"),
    PAGE("PAGE");

    private final String code;

    SeoEntityTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        for (SeoEntityTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

}
