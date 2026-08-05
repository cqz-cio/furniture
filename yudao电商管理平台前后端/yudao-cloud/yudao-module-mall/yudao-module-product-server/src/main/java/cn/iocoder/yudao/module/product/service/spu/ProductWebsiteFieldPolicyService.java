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
            Map.entry("material", TenantProductFieldEnum.MATERIAL.getCode()),
            Map.entry("finish", TenantProductFieldEnum.FINISH.getCode()),
            Map.entry("dimension", TenantProductFieldEnum.DIMENSION.getCode()),
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
        // 商品级配置不能覆盖租户级公开策略。
        filtered.remove("fieldVisibility");
        filtered.remove("displayPolicy");
        DETAIL_CONFIG_POLICY_FIELDS.forEach((configField, policyField) -> {
            if (!policy.allows(policyField)) {
                filtered.remove(configField);
            }
        });
        return filtered;
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
