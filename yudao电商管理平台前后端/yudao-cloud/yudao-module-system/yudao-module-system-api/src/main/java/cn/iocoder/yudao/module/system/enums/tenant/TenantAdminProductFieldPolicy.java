package cn.iocoder.yudao.module.system.enums.tenant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 租户 ERP 商品管理字段策略。
 *
 * <p>网站公开字段决定 WEBSITE 状态；未公开字段再按业务模式划分为 ERP INTERNAL
 * 或当前模式 NOT_APPLICABLE。管理端只消费这里返回的状态，不再自行推断 B2B/B2C 字段。</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TenantAdminProductFieldPolicy {

    public static final String WEBSITE = "WEBSITE";
    public static final String INTERNAL = "INTERNAL";
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    public static final String COST_PRICE = "costPrice";
    public static final String STOCK = "stock";
    public static final String DELIVERY = "delivery";
    public static final String BROKERAGE = "brokerage";
    public static final String GIVE_INTEGRAL = "giveIntegral";
    public static final String VIRTUAL_SALES_COUNT = "virtualSalesCount";

    public static Map<String, String> resolve(String businessMode, List<String> websiteProductFields) {
        Set<String> websiteFields = new LinkedHashSet<>(
                websiteProductFields != null ? websiteProductFields : List.of());
        boolean b2b = TenantBusinessModeEnum.B2B.getCode().equals(businessMode);
        Map<String, String> states = new LinkedHashMap<>();

        for (TenantProductFieldEnum field : TenantProductFieldEnum.values()) {
            states.put(field.getCode(), websiteFields.contains(field.getCode()) ? WEBSITE : INTERNAL);
        }
        states.put(COST_PRICE, INTERNAL);
        states.put(STOCK, states.get(TenantProductFieldEnum.INVENTORY.getCode()));

        if (b2b) {
            markNotApplicableWhenPrivate(states, TenantProductFieldEnum.MARKET_PRICE.getCode());
            markNotApplicableWhenPrivate(states, TenantProductFieldEnum.SALES_COUNT.getCode());
            states.put(DELIVERY, NOT_APPLICABLE);
            states.put(BROKERAGE, NOT_APPLICABLE);
            states.put(GIVE_INTEGRAL, NOT_APPLICABLE);
            states.put(VIRTUAL_SALES_COUNT, NOT_APPLICABLE);
        } else {
            states.put(DELIVERY, WEBSITE);
            states.put(BROKERAGE, WEBSITE);
            states.put(GIVE_INTEGRAL, WEBSITE);
            states.put(VIRTUAL_SALES_COUNT, WEBSITE);
        }
        return Map.copyOf(states);
    }

    private static void markNotApplicableWhenPrivate(Map<String, String> states, String field) {
        if (!WEBSITE.equals(states.get(field))) {
            states.put(field, NOT_APPLICABLE);
        }
    }

}
