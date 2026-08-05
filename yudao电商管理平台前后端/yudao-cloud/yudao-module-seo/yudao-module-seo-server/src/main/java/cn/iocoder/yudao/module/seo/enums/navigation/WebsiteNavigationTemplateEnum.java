package cn.iocoder.yudao.module.seo.enums.navigation;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum WebsiteNavigationTemplateEnum {

    VANZ_B2B("VANZ_B2B"),
    OAKVED_B2C("OAKVED_B2C");

    private final String code;

    public static WebsiteNavigationTemplateEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElse(null);
    }

}
