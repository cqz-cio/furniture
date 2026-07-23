package cn.iocoder.yudao.module.seo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SeoAnalysisSourceTypeEnum {

    ENTITY("ENTITY"),
    MANUAL("MANUAL"),
    DOCUMENT("DOCUMENT");

    private final String code;

    public static boolean isValid(String code) {
        for (SeoAnalysisSourceTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

}
