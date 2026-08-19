package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuDetailRespVO;
import cn.iocoder.yudao.module.product.service.spu.ProductWebsiteFieldPolicyService.ProductWebsiteFieldPolicy;
import cn.iocoder.yudao.module.system.api.tenant.TenantApi;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantRespDTO;
import cn.iocoder.yudao.module.system.enums.tenant.TenantBusinessModeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductWebsiteFieldPolicyServiceTest {

    private final TenantApi tenantApi = mock(TenantApi.class);
    private final ProductWebsiteFieldPolicyService service =
            new ProductWebsiteFieldPolicyService(tenantApi);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldFilterB2BProductResponseAndReturnSameDisplayPolicy() {
        Long tenantId = 162L;
        TenantContextHolder.setTenantId(tenantId);
        TenantRespDTO tenant = new TenantRespDTO()
                .setId(tenantId)
                .setBusinessMode(TenantBusinessModeEnum.B2B.getCode())
                .setWebsiteProductFields(List.of(
                        "category", "skuCode", "description", "itemNo", "material",
                        "color", "dimension", "service", "sample"));
        when(tenantApi.getTenant(tenantId)).thenReturn(CommonResult.success(tenant));

        ProductWebsiteFieldPolicy policy = service.getCurrentPolicy();
        Map<String, Object> detailConfig = new LinkedHashMap<>();
        detailConfig.put("collection", "LUXE");
        detailConfig.put("heroNote", "Shown in ivory linen");
        detailConfig.put("itemNo", "VZC0099");
        detailConfig.put("material", "Solid oak");
        detailConfig.put("color", "As shown or according to the customer's request");
        detailConfig.put("finish", "Natural matte lacquer");
        detailConfig.put("dimension", Map.of(
                "shape", "rectangular", "width", 180, "depth", 90, "height", 75, "unit", "cm"));
        detailConfig.put("packing", Map.of(
                "method", "Knock-down carton", "itemQuantity", 1,
                "itemUnit", "pc", "cartonQuantity", 2));
        detailConfig.put("service", "OEM & ODM");
        detailConfig.put("sample", "Available");
        detailConfig.put("fieldVisibility", Map.of("price", true));
        AppProductSpuDetailRespVO.Sku sku = new AppProductSpuDetailRespVO.Sku()
                .setId(78L)
                .setSkuCode("VANZ-162-78")
                .setPrice(10000)
                .setMarketPrice(12000)
                .setVipPrice(9000)
                .setStock(8)
                .setWeight(12D)
                .setVolume(0.4D);
        AppProductSpuDetailRespVO product = new AppProductSpuDetailRespVO()
                .setId(77L)
                .setCategoryId(30L)
                .setCategoryCode("nightstand")
                .setCategoryParentId(3L)
                .setCategoryName("Bedroom")
                .setDescription("Public description")
                .setPrice(10000)
                .setMarketPrice(12000)
                .setStock(8)
                .setSalesCount(20)
                .setDetailConfig(detailConfig)
                .setSkus(List.of(sku));

        service.applyPolicy(product, policy);

        assertEquals("Bedroom", product.getCategoryName());
        assertEquals("nightstand", product.getCategoryCode());
        assertEquals(3L, product.getCategoryParentId());
        assertEquals("Public description", product.getDescription());
        assertNull(product.getPrice());
        assertNull(product.getMarketPrice());
        assertNull(product.getStock());
        assertNull(product.getSalesCount());
        assertEquals("VANZ-162-78", product.getSkus().get(0).getSkuCode());
        assertNull(product.getSkus().get(0).getPrice());
        assertNull(product.getSkus().get(0).getVipPrice());
        assertNull(product.getSkus().get(0).getWeight());
        assertFalse(product.getDetailConfig().containsKey("collection"));
        assertEquals("VZC0099", product.getDetailConfig().get("itemNo"));
        assertEquals("Solid oak", product.getDetailConfig().get("material"));
        assertEquals("As shown or according to the customer's request",
                product.getDetailConfig().get("color"));
        assertTrue(product.getDetailConfig().containsKey("dimension"));
        assertEquals("OEM & ODM", product.getDetailConfig().get("service"));
        assertEquals("Available", product.getDetailConfig().get("sample"));
        assertFalse(product.getDetailConfig().containsKey("finish"));
        assertFalse(product.getDetailConfig().containsKey("packing"));
        assertFalse(product.getDetailConfig().containsKey("fieldVisibility"));
        assertEquals("erp-tenant", product.getDisplayPolicy().getSource());
        assertTrue(product.getDisplayPolicy().getFields().get("skuCode"));
        assertFalse(product.getDisplayPolicy().getFields().get("price"));
    }

    @Test
    void shouldUseSafeB2BDefaultsForLegacyTenantWithoutConfiguration() {
        Long tenantId = 163L;
        TenantContextHolder.setTenantId(tenantId);
        TenantRespDTO tenant = new TenantRespDTO()
                .setId(tenantId)
                .setBusinessMode(TenantBusinessModeEnum.B2B.getCode())
                .setWebsiteProductFields(null);
        when(tenantApi.getTenant(tenantId)).thenReturn(CommonResult.success(tenant));

        ProductWebsiteFieldPolicy policy = service.getCurrentPolicy();

        assertTrue(policy.allows("skuCode"));
        assertTrue(policy.allows("description"));
        assertTrue(policy.allows("itemNo"));
        assertTrue(policy.allows("material"));
        assertTrue(policy.allows("color"));
        assertTrue(policy.allows("finish"));
        assertTrue(policy.allows("dimension"));
        assertTrue(policy.allows("service"));
        assertTrue(policy.allows("sample"));
        assertTrue(policy.allows("packing"));
        assertFalse(policy.allows("price"));
        assertFalse(policy.allows("inventory"));
        assertFalse(policy.allows("productId"));
    }

    @Test
    void shouldKeepBlankFinishInB2BWebsiteResponse() {
        ProductWebsiteFieldPolicy policy = new ProductWebsiteFieldPolicy(true, Set.of("finish"));
        AppProductSpuDetailRespVO product = new AppProductSpuDetailRespVO()
                .setDetailConfig(new LinkedHashMap<>(Map.of("finish", "   ")))
                .setSkus(List.of());

        service.applyPolicy(product, policy);

        assertEquals("", product.getDetailConfig().get("finish"));
    }

    @Test
    void shouldPreserveExplicitFinishInB2BWebsiteResponse() {
        ProductWebsiteFieldPolicy policy = new ProductWebsiteFieldPolicy(true, Set.of("finish"));
        AppProductSpuDetailRespVO product = new AppProductSpuDetailRespVO()
                .setDetailConfig(new LinkedHashMap<>(Map.of(
                        "finish", "  Two-tone weathered finish  ")))
                .setSkus(List.of());

        service.applyPolicy(product, policy);

        assertEquals("Two-tone weathered finish", product.getDetailConfig().get("finish"));
    }

    @Test
    void shouldNormalizeLegacyPackingAndClearEveryCategoryFieldWhenPolicyDisallowsThem() {
        ProductWebsiteFieldPolicy policy = new ProductWebsiteFieldPolicy(true, Set.of("packing"));
        AppProductSpuDetailRespVO product = new AppProductSpuDetailRespVO()
                .setCategoryId(301L)
                .setCategoryCode("bed")
                .setCategoryName("BED & HEADBOARD")
                .setCategoryParentId(30L)
                .setDetailConfig(new LinkedHashMap<>(Map.of(
                        "packing", Map.of(
                                "itemQuantity", 2,
                                "itemUnit", "pc",
                                "cartonQuantity", 1),
                        "packingDisplay", "")))
                .setSkus(List.of());

        service.applyPolicy(product, policy);

        assertNull(product.getCategoryId());
        assertNull(product.getCategoryCode());
        assertNull(product.getCategoryName());
        assertNull(product.getCategoryParentId());
        assertEquals("2 pcs/ctn", product.getDetailConfig().get("packing"));
        assertFalse(product.getDetailConfig().containsKey("packingDisplay"));
    }

    @Test
    void shouldKeepB2CFieldsCompatible() {
        Long tenantId = 1L;
        TenantContextHolder.setTenantId(tenantId);
        TenantRespDTO tenant = new TenantRespDTO()
                .setId(tenantId)
                .setBusinessMode(TenantBusinessModeEnum.B2C.getCode())
                .setWebsiteProductFields(List.of());
        when(tenantApi.getTenant(tenantId)).thenReturn(CommonResult.success(tenant));

        ProductWebsiteFieldPolicy policy = service.getCurrentPolicy();

        assertTrue(policy.allows("price"));
        assertTrue(policy.allows("inventory"));
        assertTrue(policy.allows("salesCount"));
    }

}
