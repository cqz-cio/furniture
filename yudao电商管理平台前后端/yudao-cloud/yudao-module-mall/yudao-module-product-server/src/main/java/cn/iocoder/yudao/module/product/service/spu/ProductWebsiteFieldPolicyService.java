package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductFieldPolicyRespVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuDetailRespVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuRespVO;
import cn.iocoder.yudao.module.system.api.tenant.TenantApi;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantRespDTO;
import cn.iocoder.yudao.module.system.enums.tenant.TenantBusinessModeEnum;
import cn.iocoder.yudao.module.system.enums.tenant.TenantProductFieldEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 网站公开商品字段策略。
 *
 * ERP 租户配置是唯一来源：接口先清除未开放值，再返回同一份 displayPolicy 供网站移除对应 UI。
 */
@Service
@RequiredArgsConstructor
public class ProductWebsiteFieldPolicyService {

    private static final Map<String, String> DETAIL_CONFIG_POLICY_FIELDS = Map.ofEntries(
            Map.entry("collection", TenantProductFieldEnum.COLLECTION.getCode()),
            Map.entry("heroNote", TenantProductFieldEnum.HERO_NOTE.getCode()),
            Map.entry("fabricSelector", TenantProductFieldEnum.FABRIC_SELECTOR.getCode()),
            Map.entry("optionGroups", TenantProductFieldEnum.OPTION_GROUPS.getCode()),
            Map.entry("highlights", TenantProductFieldEnum.HIGHLIGHTS.getCode()),
            Map.entry("itemNo", TenantProductFieldEnum.ITEM_NO.getCode()),
            Map.entry("material", TenantProductFieldEnum.MATERIAL.getCode()),
            Map.entry("color", TenantProductFieldEnum.COLOR.getCode()),
            Map.entry("finish", TenantProductFieldEnum.FINISH.getCode()),
            Map.entry("dimension", TenantProductFieldEnum.DIMENSION.getCode()),
            Map.entry("service", TenantProductFieldEnum.SERVICE.getCode()),
            Map.entry("sample", TenantProductFieldEnum.SAMPLE.getCode()),
            Map.entry("packing", TenantProductFieldEnum.PACKING.getCode()),
            Map.entry("accordions", TenantProductFieldEnum.ACCORDIONS.getCode()),
            Map.entry("relatedLinks", TenantProductFieldEnum.RELATED_LINKS.getCode()));

    private final TenantApi tenantApi;

    public ProductWebsiteFieldPolicy getCurrentPolicy() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        TenantRespDTO tenant = tenantApi.getTenant(tenantId).getCheckedData();
        if (tenant == null) {
            throw new IllegalStateException("租户不存在：" + tenantId);
        }
        List<String> fields = TenantProductFieldEnum.resolve(
                tenant.getBusinessMode(), tenant.getWebsiteProductFields());
        return new ProductWebsiteFieldPolicy(
                TenantBusinessModeEnum.B2B.getCode().equals(tenant.getBusinessMode()),
                new LinkedHashSet<>(fields));
    }

    public void applyPolicy(AppProductSpuRespVO spu, ProductWebsiteFieldPolicy policy) {
        spu.setDisplayPolicy(toRespVO(policy));
        if (!policy.allows(TenantProductFieldEnum.CATEGORY)) {
            spu.setCategoryId(null);
            spu.setCategoryCode(null);
            spu.setCategoryParentId(null);
            spu.setCategoryName(null);
        }
        if (!policy.allows(TenantProductFieldEnum.BADGES)) {
            spu.setRecommendNew(null);
            spu.setRecommendBest(null);
        }
        if (!policy.allows(TenantProductFieldEnum.INTRODUCTION)) {
            spu.setIntroduction(null);
        }
        if (!policy.allows(TenantProductFieldEnum.PRICE)) {
            spu.setPrice(null);
        }
        if (!policy.allows(TenantProductFieldEnum.MARKET_PRICE)) {
            spu.setMarketPrice(null);
        }
        if (!policy.allows(TenantProductFieldEnum.INVENTORY)) {
            spu.setStock(null);
        }
        if (!policy.allows(TenantProductFieldEnum.SALES_COUNT)) {
            spu.setSalesCount(null);
        }
        if (policy.isB2b()) {
            spu.setDeliveryTypes(null);
        }
        spu.setDetailConfig(filterDetailConfig(spu.getDetailConfig(), policy));
    }

    public void applyPolicy(AppProductSpuDetailRespVO spu, ProductWebsiteFieldPolicy policy) {
        spu.setDisplayPolicy(toRespVO(policy));
        if (!policy.allows(TenantProductFieldEnum.CATEGORY)) {
            spu.setCategoryId(null);
            spu.setCategoryCode(null);
            spu.setCategoryParentId(null);
            spu.setCategoryName(null);
        }
        if (!policy.allows(TenantProductFieldEnum.BADGES)) {
            spu.setRecommendNew(null);
            spu.setRecommendBest(null);
        }
        if (!policy.allows(TenantProductFieldEnum.INTRODUCTION)) {
            spu.setIntroduction(null);
        }
        if (!policy.allows(TenantProductFieldEnum.DESCRIPTION)) {
            spu.setDescription(null);
        }
        if (!policy.allows(TenantProductFieldEnum.PRICE)) {
            spu.setPrice(null);
        }
        if (!policy.allows(TenantProductFieldEnum.MARKET_PRICE)) {
            spu.setMarketPrice(null);
        }
        if (!policy.allows(TenantProductFieldEnum.INVENTORY)) {
            spu.setStock(null);
        }
        if (!policy.allows(TenantProductFieldEnum.SALES_COUNT)) {
            spu.setSalesCount(null);
        }
        spu.setDetailConfig(filterDetailConfig(spu.getDetailConfig(), policy));
        if (spu.getSkus() == null) {
            return;
        }
        spu.getSkus().forEach(sku -> applySkuPolicy(sku, policy));
    }

    private void applySkuPolicy(AppProductSpuDetailRespVO.Sku sku, ProductWebsiteFieldPolicy policy) {
        if (!policy.allows(TenantProductFieldEnum.SKU_CODE)) {
            sku.setSkuCode(null);
        }
        if (!policy.allows(TenantProductFieldEnum.SKU_PROPERTIES)) {
            sku.setProperties(null);
        }
        if (!policy.allows(TenantProductFieldEnum.PRICE)) {
            sku.setPrice(null);
            sku.setVipPrice(null);
        } else if (policy.isB2b()) {
            // B2B 网站不公开会员价；需要公开销售价时仍只返回基础销售价。
            sku.setVipPrice(null);
        }
        if (!policy.allows(TenantProductFieldEnum.MARKET_PRICE)) {
            sku.setMarketPrice(null);
        }
        if (!policy.allows(TenantProductFieldEnum.INVENTORY)) {
            sku.setStock(null);
        }
        if (!policy.allows(TenantProductFieldEnum.SKU_MEASUREMENTS)) {
            sku.setWeight(null);
            sku.setVolume(null);
        }
    }

    private Map<String, Object> filterDetailConfig(
            Map<String, Object> detailConfig, ProductWebsiteFieldPolicy policy) {
        if (detailConfig == null) {
            return null;
        }
        Map<String, Object> filtered = new LinkedHashMap<>(detailConfig);
        normalizePacking(filtered);
        // 商品级配置不能覆盖租户级公开策略。
        filtered.remove("fieldVisibility");
        filtered.remove("displayPolicy");
        filtered.remove("productType");
        DETAIL_CONFIG_POLICY_FIELDS.forEach((configField, policyField) -> {
            if (!policy.allows(policyField)) {
                filtered.remove(configField);
            }
        });
        if (policy.allows(TenantProductFieldEnum.FINISH) && filtered.containsKey("finish")) {
            Object rawFinish = filtered.get("finish");
            String finish = rawFinish instanceof String ? ((String) rawFinish).trim() : "";
            filtered.put("finish", finish);
        }
        return filtered;
    }

    private void normalizePacking(Map<String, Object> detailConfig) {
        Object packing = detailConfig.get("packing");
        String canonical = packing instanceof String ? ((String) packing).trim() : "";
        Object legacyDisplay = detailConfig.get("packingDisplay");
        String display = legacyDisplay instanceof String ? ((String) legacyDisplay).trim() : "";
        String normalized = !canonical.isEmpty()
                ? canonical
                : !display.isEmpty() ? display : formatLegacyPacking(packing);
        if (detailConfig.containsKey("packing") || detailConfig.containsKey("packingDisplay")) {
            detailConfig.put("packing", normalized);
        }
        detailConfig.remove("packingDisplay");
    }

    private String formatLegacyPacking(Object value) {
        if (!(value instanceof Map<?, ?> packing)) {
            return "";
        }
        String method = packing.get("method") instanceof String
                ? ((String) packing.get("method")).trim() : "";
        Integer itemQuantity = positiveInteger(packing.get("itemQuantity"));
        Integer cartonQuantity = positiveInteger(packing.get("cartonQuantity"));
        if (itemQuantity == null || cartonQuantity == null) {
            return method;
        }
        String singularUnit = "set".equals(packing.get("itemUnit")) ? "set" : "pc";
        String unit = itemQuantity == 1 ? singularUnit
                : "set".equals(singularUnit) ? "sets" : "pcs";
        if ("pc".equals(singularUnit) && itemQuantity == 1 && cartonQuantity > 1) {
            return "Ships in " + cartonQuantity + " cartons";
        }
        if (cartonQuantity == 1) {
            return itemQuantity + " " + unit + "/ctn";
        }
        return itemQuantity + " " + unit + " / " + cartonQuantity + " cartons";
    }

    private Integer positiveInteger(Object value) {
        if (value instanceof Number number) {
            int integer = number.intValue();
            return integer > 0 && number.doubleValue() == integer ? integer : null;
        }
        try {
            int integer = Integer.parseInt(String.valueOf(value));
            return integer > 0 ? integer : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private AppProductFieldPolicyRespVO toRespVO(ProductWebsiteFieldPolicy policy) {
        Map<String, Boolean> fields = new LinkedHashMap<>();
        for (TenantProductFieldEnum field : TenantProductFieldEnum.values()) {
            fields.put(field.getCode(), policy.allows(field));
        }
        return new AppProductFieldPolicyRespVO()
                .setSource("erp-tenant")
                .setFields(fields);
    }

    @Getter
    @RequiredArgsConstructor
    public static class ProductWebsiteFieldPolicy {

        private final boolean b2b;
        private final Set<String> allowedFields;

        public boolean allows(TenantProductFieldEnum field) {
            return allows(field.getCode());
        }

        public boolean allows(String field) {
            return allowedFields.contains(field);
        }

    }

}
