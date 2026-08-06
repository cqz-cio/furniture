package cn.iocoder.yudao.module.seo.enums.blog;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum WebsiteBlogStatusEnum {

    DRAFT("DRAFT"),
    PUBLISHED("PUBLISHED"),
    OFFLINE("OFFLINE");

    private final String code;

    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(code));
    }

}
