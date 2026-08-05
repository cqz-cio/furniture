package cn.iocoder.yudao.module.seo.enums.navigation;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WebsiteNavigationItemTypeEnum {

    PAGE("PAGE"),
    CATEGORY("CATEGORY"),
    DIRECTORY("DIRECTORY"),
    ROUTE("ROUTE"),
    FILTER("FILTER");

    private final String code;

}
