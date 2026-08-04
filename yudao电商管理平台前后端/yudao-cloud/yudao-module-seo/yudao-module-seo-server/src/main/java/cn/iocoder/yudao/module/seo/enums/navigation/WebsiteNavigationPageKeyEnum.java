package cn.iocoder.yudao.module.seo.enums.navigation;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum WebsiteNavigationPageKeyEnum {

    HOME("HOME", "Home", "/", 10),
    PRODUCTS("PRODUCTS", "Products", "/products", 20),
    ABOUT_US("ABOUT_US", "About Us", "/about-us", 30),
    WORKSHOP("WORKSHOP", "Workshop", "/workshop", 40),
    BLOG("BLOG", "Blog", "/blog", 50),
    CONTACT("CONTACT", "Contact", "/contact", 60);

    private final String code;
    private final String defaultLabel;
    private final String href;
    private final Integer defaultSort;

    public String itemKey() {
        return "PAGE_" + code;
    }

    public static WebsiteNavigationPageKeyEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElse(null);
    }

}
