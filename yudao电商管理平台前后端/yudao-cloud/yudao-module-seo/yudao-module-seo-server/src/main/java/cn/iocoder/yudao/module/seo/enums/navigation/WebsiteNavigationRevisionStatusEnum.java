package cn.iocoder.yudao.module.seo.enums.navigation;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WebsiteNavigationRevisionStatusEnum {

    DRAFT("DRAFT"),
    PUBLISHED("PUBLISHED"),
    ARCHIVED("ARCHIVED");

    private final String code;

}
