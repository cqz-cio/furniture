package cn.iocoder.yudao.module.system.enums.tenant;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * 租户网站商品字段枚举。
 *
 * 字段编码同时作为 ERP 租户配置、公开商品接口 displayPolicy 和家具 2B 网站的稳定契约。
 * 商品名称、图片及接口路由所需的内部 ID 属于基础协议字段，不在此处配置。
 */
@Getter
@RequiredArgsConstructor
public enum TenantProductFieldEnum implements ArrayValuable<String> {

    CATEGORY("category"),
    BADGES("badges"),
    INTRODUCTION("introduction"),
    PRICE("price"),
    MARKET_PRICE("marketPrice"),
    INVENTORY("inventory"),
    PRODUCT_ID("productId"),
    SKU_CODE("skuCode"),
    COLLECTION("collection"),
    HERO_NOTE("heroNote"),
    FABRIC_SELECTOR("fabricSelector"),
    OPTION_GROUPS("optionGroups"),
    HIGHLIGHTS("highlights"),
    DESCRIPTION("description"),
    MATERIAL("material"),
    FINISH("finish"),
    DIMENSION("dimension"),
    PACKING("packing"),
    ACCORDIONS("accordions"),
    SKU_PROPERTIES("skuProperties"),
    SKU_MEASUREMENTS("skuMeasurements"),
    RELATED_PRODUCTS("relatedProducts"),
    RELATED_LINKS("relatedLinks"),
    SALES_COUNT("salesCount");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(TenantProductFieldEnum::getCode)
            .toArray(String[]::new);

    private static final List<String> DEFAULT_B2B_FIELDS = List.of(
            CATEGORY.code,
            BADGES.code,
            INTRODUCTION.code,
            SKU_CODE.code,
            COLLECTION.code,
            HERO_NOTE.code,
            FABRIC_SELECTOR.code,
            OPTION_GROUPS.code,
            HIGHLIGHTS.code,
            DESCRIPTION.code,
            MATERIAL.code,
            FINISH.code,
            DIMENSION.code,
            PACKING.code,
            ACCORDIONS.code,
            SKU_PROPERTIES.code,
            RELATED_PRODUCTS.code,
            RELATED_LINKS.code);

    /**
     * 网站与公开接口共享的字段编码。
     */
    private final String code;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static boolean contains(String code) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(code));
    }

    public static List<String> getDefaultB2BFields() {
        return List.copyOf(DEFAULT_B2B_FIELDS);
    }

    /**
     * 解析租户最终生效的公开商品字段。
     *
     * B2C 保持完整公开接口兼容；B2B 使用显式配置，旧数据缺失配置时采用安全的询盘型默认值。
     */
    public static List<String> resolve(String businessMode, List<String> configuredFields) {
        if (!TenantBusinessModeEnum.B2B.getCode().equals(businessMode)) {
            return List.of(ARRAYS);
        }
        if (configuredFields == null) {
            return getDefaultB2BFields();
        }
        return configuredFields.stream()
                .filter(TenantProductFieldEnum::contains)
                .distinct()
                .toList();
    }

}
