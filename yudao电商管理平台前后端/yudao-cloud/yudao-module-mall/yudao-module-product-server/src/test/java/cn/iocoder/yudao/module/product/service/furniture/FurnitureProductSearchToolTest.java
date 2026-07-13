package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.erp.api.integration.MallErpProductApi;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockRequestDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.furniture.FurnitureSkuSearchDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.furniture.catalog.FurnitureSkuSearchService;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantRequirements;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureRequirementNormalizer;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureCandidateMatch;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureMatchType;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureProductCandidate;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FurnitureProductSearchToolTest extends BaseMockitoUnitTest {

    private FurnitureProductSearchTool tool;

    @Mock
    private FurnitureSkuSearchService projectionService;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private MallErpProductApi mallErpProductApi;

    @BeforeEach
    void setUp() {
        tool = new FurnitureProductSearchTool(projectionService, productSkuService, productSpuService,
                mallErpProductApi, new FurnitureRequirementNormalizer());
    }

    @Test
    void shouldSearchProducts_shouldUseNormalizedCategoryOrExplicitProductIntent() {
        FurnitureAssistantRequirements requirements = requirements("sofa");

        assertTrue(tool.shouldSearchProducts("show me options", requirements, Collections.emptyList()));
        assertTrue(tool.shouldSearchProducts("sofa under 8000", new FurnitureAssistantRequirements(),
                Collections.emptyList()));
        assertFalse(tool.shouldSearchProducts("please reply in Chinese", new FurnitureAssistantRequirements(),
                Collections.emptyList()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchProducts_shouldReturnRealSkuPriceAndErpStockInOneBatchPipeline() {
        FurnitureSkuSearchDO projection = projection(1001L, 2001L, "light-gray", "fabric", 3, 2180);
        when(projectionService.getByCategory("sofa")).thenReturn(Collections.singletonList(projection));
        ProductSkuDO sku = sku(2001L, 1001L, 699900, "Color", "Light Gray");
        when(productSkuService.getSkuList(Collections.singletonList(2001L)))
                .thenReturn(Collections.singletonList(sku));
        when(productSpuService.getSpuList(Collections.singletonList(1001L)))
                .thenReturn(Collections.singletonList(spu(1001L, "Shallow Gray Fabric Sofa", 1)));
        when(mallErpProductApi.validateSellableStock(anyList())).thenReturn(stockResponse(
                stock(2001L, "12", true)));

        FurnitureAssistantRequirements requirements = requirements("sofa");
        requirements.setBudgetMax(new BigDecimal("8000"));
        requirements.setMaxWidthMm(2200);
        requirements.setSeatCount(3);
        requirements.setMaterials(Collections.singletonList("fabric"));
        requirements.setColors(Collections.singletonList("light-gray"));
        requirements.setHardConstraints(new HashSet<>(
                Arrays.asList("budgetMax", "maxWidthMm", "seatCount", "materials")));

        FurnitureProductSearchResult result = tool.searchProducts(
                FurnitureProductSearchRequest.from("浅灰色三人布艺沙发", requirements, 3));

        assertEquals(FurnitureMatchType.EXACT, result.getMatchType());
        assertEquals(Long.valueOf(1001L), result.getProducts().get(0).getId());
        assertEquals(Long.valueOf(2001L), result.getProducts().get(0).getSkuId());
        assertEquals(new BigDecimal("6999"), result.getProducts().get(0).getPrice());
        assertEquals(Integer.valueOf(12), result.getProducts().get(0).getStock());
        assertEquals("Mall description", result.getProducts().get(0).getSubtitle());
        verify(projectionService, times(1)).getByCategory("sofa");
        verify(productSkuService, times(1)).getSkuList(Collections.singletonList(2001L));
        verify(productSpuService, times(1)).getSpuList(Collections.singletonList(1001L));
        ArgumentCaptor<List<MallErpStockRequestDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(mallErpProductApi, times(1)).validateSellableStock(captor.capture());
        assertEquals(Long.valueOf(2001L), captor.getValue().get(0).getMallSkuId());
        assertEquals(BigDecimal.ONE, captor.getValue().get(0).getCount());
    }

    @Test
    void searchForAssistant_shouldPreserveNormalizedUnderBudgetConstraintUntilTypedCallerMigration() {
        List<FurnitureSkuSearchDO> projections = Arrays.asList(
                projection(1001L, 2001L, "gray", "fabric", 3, 2100),
                projection(1002L, 2002L, "gray", "fabric", 3, 2100));
        prepareMall(projections, Arrays.asList(
                sku(2001L, 1001L, 699900, "Color", "Gray"),
                sku(2002L, 1002L, 899900, "Color", "Gray")), Arrays.asList(
                spu(1001L, "Under Budget", 1), spu(1002L, "Over Budget", 1)));
        when(mallErpProductApi.validateSellableStock(anyList())).thenReturn(stockResponse(
                stock(2001L, "5", true), stock(2002L, "5", true)));

        FurnitureProductSearchResult result = tool.searchForAssistant("sofa under 8000");

        assertEquals(1, result.getProducts().size());
        assertEquals(Long.valueOf(2001L), result.getProducts().get(0).getSkuId());
        assertTrue(result.getMatchedConstraints().contains("budgetMax"));
    }

    @Test
    void searchProducts_shouldExcludeMissingSkuAndDisabledSpu() {
        List<FurnitureSkuSearchDO> projections = Arrays.asList(
                projection(1001L, 2001L, "gray", "fabric", 3, 2100),
                projection(1002L, 2002L, "gray", "fabric", 3, 2100));
        when(projectionService.getByCategory("sofa")).thenReturn(projections);
        when(productSkuService.getSkuList(Arrays.asList(2001L, 2002L)))
                .thenReturn(Collections.singletonList(sku(2002L, 1002L, 500000, "Color", "Gray")));
        when(productSpuService.getSpuList(Collections.singletonList(1002L)))
                .thenReturn(Collections.singletonList(spu(1002L, "Disabled Sofa", 0)));
        when(mallErpProductApi.validateSellableStock(anyList())).thenReturn(stockResponse(
                stock(2001L, "5", true), stock(2002L, "5", true)));

        FurnitureProductSearchResult result = tool.searchProducts(request("sofa", 3));

        assertEquals(FurnitureMatchType.NONE, result.getMatchType());
        assertTrue(result.getProducts().isEmpty());
    }

    @Test
    void searchProducts_shouldFailClosedForErpErrorMissingUnavailableAndZeroStockRows() {
        List<FurnitureSkuSearchDO> projections = Arrays.asList(
                projection(1001L, 2001L, "gray", "fabric", 3, 2100),
                projection(1002L, 2002L, "gray", "fabric", 3, 2100),
                projection(1003L, 2003L, "gray", "fabric", 3, 2100));
        prepareMall(projections, Arrays.asList(
                sku(2001L, 1001L, 500000, "Color", "Gray"),
                sku(2002L, 1002L, 500000, "Color", "Gray"),
                sku(2003L, 1003L, 500000, "Color", "Gray")), Arrays.asList(
                spu(1001L, "Missing Mapping", 1), spu(1002L, "Unavailable", 1), spu(1003L, "No Stock", 1)));
        when(mallErpProductApi.validateSellableStock(anyList())).thenReturn(stockResponse(
                stock(2002L, "9", false), stock(2003L, "0", true)));

        FurnitureProductSearchResult result = tool.searchProducts(request("sofa", 3));

        assertEquals(FurnitureMatchType.NONE, result.getMatchType());
        when(mallErpProductApi.validateSellableStock(anyList())).thenReturn(CommonResult.error(500, "ERP down"));
        assertEquals(FurnitureMatchType.NONE, tool.searchProducts(request("sofa", 3)).getMatchType());
    }

    @Test
    void searchProducts_shouldFailClosedForConflictingDuplicateErpStockRows() {
        FurnitureSkuSearchDO projection = projection(1001L, 2001L, "gray", "fabric", 3, 2100);
        prepareMall(Collections.singletonList(projection),
                Collections.singletonList(sku(2001L, 1001L, 500000, "Color", "Gray")),
                Collections.singletonList(spu(1001L, "Ambiguous Stock", 1)));
        when(mallErpProductApi.validateSellableStock(anyList())).thenReturn(stockResponse(
                stock(2001L, "8", true), stock(2001L, "2", true)));

        FurnitureProductSearchResult result = tool.searchProducts(request("sofa", 3));

        assertEquals(FurnitureMatchType.NONE, result.getMatchType());
        assertTrue(result.getProducts().isEmpty());
    }

    @Test
    void searchProducts_shouldDisclosePartialMatchAndKeepDeterministicOrder() {
        List<FurnitureSkuSearchDO> projections = Arrays.asList(
                projection(1002L, 2002L, "gray", "fabric", 4, 2100),
                projection(1001L, 2001L, "gray", "fabric", 4, 2100));
        prepareMall(projections, Arrays.asList(
                sku(2002L, 1002L, 600000, "Seats", "Four"),
                sku(2001L, 1001L, 500000, "Seats", "Four")), Arrays.asList(
                spu(1002L, "Second", 1), spu(1001L, "First", 1)));
        when(mallErpProductApi.validateSellableStock(anyList())).thenReturn(stockResponse(
                stock(2002L, "7", true), stock(2001L, "7", true)));
        FurnitureAssistantRequirements requirements = requirements("sofa");
        requirements.setSeatCount(6);
        requirements.setHardConstraints(Collections.singleton("seatCount"));

        FurnitureProductSearchResult result = tool.searchProducts(
                FurnitureProductSearchRequest.from("six seat sofa", requirements, 3));

        assertEquals(FurnitureMatchType.PARTIAL, result.getMatchType());
        assertEquals(Collections.singletonList("seatCount"), result.getUnmetConstraints());
        assertEquals(Arrays.asList(2001L, 2002L), Arrays.asList(
                result.getProducts().get(0).getSkuId(), result.getProducts().get(1).getSkuId()));
    }

    @Test
    void searchProducts_shouldGroupAllVariantsOnlyFromSelectedSpu() {
        List<FurnitureSkuSearchDO> projections = Arrays.asList(
                projection(1001L, 2001L, "gray", "fabric", 6, 2100),
                projection(1001L, 2002L, "cream", "fabric", 4, 2100),
                projection(1002L, 3001L, "black", "fabric", 6, 2100));
        prepareMall(projections, Arrays.asList(
                sku(2001L, 1001L, 500000, "Color", "Gray"),
                sku(2002L, 1001L, 550000, "Color", "Cream"),
                sku(3001L, 1002L, 450000, "Color", "Black")), Arrays.asList(
                spu(1001L, "Chosen Family", 1), spu(1002L, "Other Family", 1)));
        when(mallErpProductApi.validateSellableStock(anyList())).thenReturn(stockResponse(
                stock(2001L, "10", true), stock(2002L, "9", true), stock(3001L, "1", true)));
        FurnitureAssistantRequirements requirements = requirements("sofa");
        requirements.setSeatCount(6);
        requirements.setHardConstraints(Collections.singleton("seatCount"));
        FurnitureProductSearchRequest request = FurnitureProductSearchRequest.from("six seat sofa", requirements, 1);
        request.setIncludeAllVariants(true);

        FurnitureProductSearchResult result = tool.searchProducts(request);

        assertEquals(2, result.getProducts().size());
        assertEquals(Arrays.asList(1001L, 1001L), Arrays.asList(
                result.getProducts().get(0).getId(), result.getProducts().get(1).getId()));
        assertEquals(Arrays.asList(2001L, 2002L), Arrays.asList(
                result.getProducts().get(0).getSkuId(), result.getProducts().get(1).getSkuId()));
        assertEquals(FurnitureMatchType.EXACT, result.getMatchType());
        assertTrue(result.getUnmetConstraints().isEmpty());
        assertTrue(result.getMatchedConstraints().contains("seatCount"));
    }

    @Test
    void fromWinningMatch_shouldKeepWinningMetadataWhenConcreteSiblingsDiffer() {
        FurnitureProductCandidate winner = candidate(1001L, 2001L, 6, 500000, "10");
        FurnitureProductCandidate partialSibling = candidate(1001L, 2002L, 4, 550000, "9");
        FurnitureCandidateMatch winningMatch = new FurnitureCandidateMatch(winner, FurnitureMatchType.EXACT,
                Arrays.asList("category", "seatCount"), Collections.emptyList(), 2);

        FurnitureProductSearchResult result = FurnitureProductSearchResult.fromWinningMatch(
                winningMatch, Arrays.asList(winner, partialSibling));

        assertEquals(FurnitureMatchType.EXACT, result.getMatchType());
        assertEquals(Arrays.asList("category", "seatCount"), result.getMatchedConstraints());
        assertTrue(result.getUnmetConstraints().isEmpty());
        assertEquals(Arrays.asList(2001L, 2002L), Arrays.asList(
                result.getProducts().get(0).getSkuId(), result.getProducts().get(1).getSkuId()));
    }

    private void prepareMall(List<FurnitureSkuSearchDO> projections, List<ProductSkuDO> skus,
                             List<ProductSpuDO> spus) {
        when(projectionService.getByCategory("sofa")).thenReturn(projections);
        when(productSkuService.getSkuList(anyList())).thenReturn(skus);
        when(productSpuService.getSpuList(anyList())).thenReturn(spus);
    }

    private static FurnitureProductSearchRequest request(String category, int limit) {
        return FurnitureProductSearchRequest.from(category, requirements(category), limit);
    }

    private static FurnitureAssistantRequirements requirements(String category) {
        FurnitureAssistantRequirements requirements = new FurnitureAssistantRequirements();
        requirements.setCategory(category);
        return requirements;
    }

    private static FurnitureSkuSearchDO projection(Long spuId, Long skuId, String color, String material,
                                                    Integer seats, Integer width) {
        return FurnitureSkuSearchDO.builder().spuId(spuId).skuId(skuId).categoryCode("sofa")
                .colorCode(color).materialCodes(Collections.singletonList(material))
                .seatCount(seats).widthMm(width).build();
    }

    private static ProductSkuDO sku(Long id, Long spuId, Integer price, String property, String value) {
        return ProductSkuDO.builder().id(id).spuId(spuId).price(price).marketPrice(price + 10000)
                .properties(Collections.singletonList(new ProductSkuDO.Property(1L, property, 2L, value))).build();
    }

    private static ProductSpuDO spu(Long id, String name, Integer status) {
        return ProductSpuDO.builder().id(id).name(name).introduction("Mall description")
                .picUrl("/images/" + id + ".jpg").status(status).build();
    }

    private static MallErpStockDTO stock(Long skuId, String sellable, boolean available) {
        return new MallErpStockDTO().setMallSkuId(skuId).setErpProductId(skuId + 10000)
                .setRequestedCount(BigDecimal.ONE).setSellableStock(new BigDecimal(sellable))
                .setAvailable(available);
    }

    private static FurnitureProductCandidate candidate(Long spuId, Long skuId, Integer seats,
                                                       Integer price, String sellableStock) {
        return new FurnitureProductCandidate(
                projection(spuId, skuId, "gray", "fabric", seats, 2100),
                sku(skuId, spuId, price, "Seats", String.valueOf(seats)),
                spu(spuId, "Variant Family", 1), new BigDecimal(sellableStock), true);
    }

    private static CommonResult<List<MallErpStockDTO>> stockResponse(MallErpStockDTO... stock) {
        return CommonResult.success(Arrays.asList(stock));
    }
}
