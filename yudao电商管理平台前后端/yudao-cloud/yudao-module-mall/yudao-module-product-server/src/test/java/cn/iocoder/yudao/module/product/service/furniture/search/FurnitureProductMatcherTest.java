package cn.iocoder.yudao.module.product.service.furniture.search;

import cn.iocoder.yudao.module.product.dal.dataobject.furniture.FurnitureSkuSearchDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantRequirements;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnitureProductMatcherTest {

    private final FurnitureProductMatcher matcher = new FurnitureProductMatcher();

    @Test
    void match_shouldReturnExactBeforePartial() {
        FurnitureAssistantRequirements request = sofaRequest(8000, 2200, 3, "fabric");
        FurnitureProductCandidate exact = candidate(1001L, 2001L, 699900, 12,
                "sofa", "light-gray", "fabric", 3, 2180, true);
        FurnitureProductCandidate tooWide = candidate(1002L, 2002L, 659900, 20,
                "sofa", "light-gray", "fabric", 3, 2400, true);

        List<FurnitureCandidateMatch> result = matcher.match(request, Arrays.asList(tooWide, exact), 3);

        assertEquals(1, result.size());
        assertEquals(FurnitureMatchType.EXACT, result.get(0).getMatchType());
        assertEquals(Long.valueOf(2001L), result.get(0).getCandidate().getSku().getId());
        assertTrue(result.get(0).getUnmetConstraints().isEmpty());
        assertEquals(Arrays.asList("category", "budgetMax", "materials", "maxWidthMm", "seatCount"),
                result.get(0).getMatchedConstraints());
    }

    @Test
    void match_shouldDiscloseRelaxedWidth() {
        FurnitureAssistantRequirements request = sofaRequest(8000, 2200, 3, "fabric");
        FurnitureProductCandidate tooWide = candidate(1002L, 2002L, 659900, 20,
                "sofa", "light-gray", "fabric", 3, 2400, true);

        FurnitureCandidateMatch result = matcher.match(request, Collections.singletonList(tooWide), 3).get(0);

        assertEquals(FurnitureMatchType.PARTIAL, result.getMatchType());
        assertEquals(Collections.singletonList("maxWidthMm"), result.getUnmetConstraints());
    }

    @Test
    void match_shouldRejectUnsafeOrNonSellableCandidates() {
        FurnitureAssistantRequirements request = sofaRequest(8000, 2200, 3, "fabric");
        request.setExcludedMaterials(Collections.singletonList("leather"));
        request.getHardConstraints().add("excludedMaterials");
        request.getNonRelaxableConstraints().addAll(Arrays.asList("seatCount", "excludedMaterials"));

        FurnitureProductCandidate wrongCategory = candidate(1001L, 2001L, 600000, 10,
                "bed", "light-gray", "fabric", 3, 2000, true);
        FurnitureProductCandidate leather = candidate(1002L, 2002L, 600000, 10,
                "sofa", "light-gray", "leather", 3, 2000, true);
        FurnitureProductCandidate wrongSeatCount = candidate(1003L, 2003L, 600000, 10,
                "sofa", "light-gray", "fabric", 2, 2000, true);
        FurnitureProductCandidate zeroStock = candidate(1004L, 2004L, 600000, 0,
                "sofa", "light-gray", "fabric", 3, 2000, true);
        FurnitureProductCandidate unmapped = candidate(1005L, 2005L, 600000, 10,
                "sofa", "light-gray", "fabric", 3, 2000, false);

        assertTrue(matcher.match(request,
                Arrays.asList(wrongCategory, leather, wrongSeatCount, zeroStock, unmapped), 10).isEmpty());
    }

    @Test
    void match_shouldTreatNonRelaxableConstraintAsHardEvenWhenHardSetIsIncomplete() {
        FurnitureAssistantRequirements request = new FurnitureAssistantRequirements();
        request.setCategory("sofa");
        request.setColors(Collections.singletonList("cream"));
        request.setNonRelaxableConstraints(new LinkedHashSet<>(Collections.singletonList("colors")));
        FurnitureProductCandidate gray = candidate(1001L, 2001L, 500000, 10,
                "sofa", "gray", "fabric", 3, 2000, true);

        assertTrue(matcher.match(request, Collections.singletonList(gray), 1).isEmpty());
    }

    @Test
    void match_shouldEvaluateEverySearchableTypedHardConstraint() {
        FurnitureAssistantRequirements request = new FurnitureAssistantRequirements();
        request.setCategory("sofa");
        request.setBudgetMin(new BigDecimal("5000"));
        request.setBudgetMax(new BigDecimal("8000"));
        request.setStyles(Collections.singletonList("modern"));
        request.setColors(Collections.singletonList("cream"));
        request.setMaterials(Collections.singletonList("fabric"));
        request.setExcludedMaterials(Collections.singletonList("leather"));
        request.setRoomTypes(Collections.singletonList("living-room"));
        request.setRoomSize(new BigDecimal("20"));
        request.setRoomWidthMm(3000);
        request.setRoomDepthMm(2500);
        request.setMaxWidthMm(2200);
        request.setMaxDepthMm(900);
        request.setMaxHeightMm(900);
        request.setSeatCount(3);
        request.setHasChildren(true);
        request.setHasPets(true);
        request.setEasyClean(true);
        request.setScratchResistant(true);
        request.setMovable(true);
        request.setRentalFriendly(true);
        request.setPreferredFeatures(Collections.singletonList("rounded-edges"));
        request.setHardConstraints(new LinkedHashSet<>(Arrays.asList(
                "budgetMin", "budgetMax", "styles", "colors", "materials", "excludedMaterials",
                "roomTypes", "maxWidthMm", "maxDepthMm", "maxHeightMm", "seatCount", "hasChildren",
                "hasPets", "easyClean", "scratchResistant", "movable", "rentalFriendly",
                "preferredFeatures")));

        FurnitureSkuSearchDO projection = FurnitureSkuSearchDO.builder()
                .spuId(1001L).skuId(2001L).categoryCode("sofa")
                .styleCodes(Collections.singletonList("modern")).colorCode("cream")
                .materialCodes(Collections.singletonList("fabric")).seatCount(3)
                .widthMm(2180).depthMm(880).heightMm(820)
                .roomTypeCodes(Collections.singletonList("living-room"))
                .featureCodes(Collections.singletonList("rounded-edges"))
                .childFriendly(true).petFriendly(true).easyClean(true).scratchResistant(true)
                .movable(true).rentalFriendly(true).build();
        FurnitureProductCandidate candidate = candidate(projection, 699900, 8, true);

        FurnitureCandidateMatch result = matcher.match(request, Collections.singletonList(candidate), 1).get(0);

        assertEquals(FurnitureMatchType.EXACT, result.getMatchType());
        assertEquals(19, result.getMatchedConstraints().size());
        assertTrue(result.getUnmetConstraints().isEmpty());
    }

    @Test
    void match_shouldIgnoreRoomContextForMatchAndRank() {
        FurnitureAssistantRequirements request = new FurnitureAssistantRequirements();
        request.setCategory("sofa");
        request.setRoomSize(new BigDecimal("4"));
        request.setRoomWidthMm(2500);
        request.setRoomDepthMm(2000);
        request.setHardConstraints(new LinkedHashSet<>(
                Arrays.asList("roomSize", "roomWidthMm", "roomDepthMm")));
        FurnitureProductCandidate small = dimensionedCandidate(1001L, 2001L, 500000, 1, 2000, 1000);
        FurnitureProductCandidate large = dimensionedCandidate(1002L, 2002L, 500000, 100, 4000, 3000);

        List<FurnitureCandidateMatch> result = matcher.match(request, Arrays.asList(small, large), 2);

        assertEquals(Arrays.asList(2002L, 2001L), Arrays.asList(
                result.get(0).getCandidate().getSku().getId(),
                result.get(1).getCandidate().getSku().getId()));
        assertEquals(FurnitureMatchType.EXACT, result.get(0).getMatchType());
        assertEquals(Collections.singletonList("category"), result.get(0).getMatchedConstraints());
        assertEquals(1, result.get(0).getCoverageScore());
    }

    @Test
    void match_shouldRejectUnknownMaterialWhenMaterialIsExcluded() {
        FurnitureAssistantRequirements request = new FurnitureAssistantRequirements();
        request.setCategory("sofa");
        request.setExcludedMaterials(Collections.singletonList("leather"));
        request.getHardConstraints().add("excludedMaterials");
        request.getNonRelaxableConstraints().add("excludedMaterials");
        FurnitureSkuSearchDO projection = FurnitureSkuSearchDO.builder()
                .spuId(1001L).skuId(2001L).categoryCode("sofa").materialCodes(Collections.emptyList()).build();

        assertTrue(matcher.match(request,
                Collections.singletonList(candidate(projection, 500000, 10, true)), 1).isEmpty());
    }

    @Test
    void match_shouldDiscloseUnknownRelaxableHardConstraint() {
        FurnitureAssistantRequirements request = new FurnitureAssistantRequirements();
        request.setCategory("sofa");
        request.getHardConstraints().add("futureConstraint");
        FurnitureProductCandidate candidate = candidate(1001L, 2001L, 500000, 10,
                "sofa", "gray", "fabric", 3, 2000, true);

        FurnitureCandidateMatch result = matcher.match(request, Collections.singletonList(candidate), 1).get(0);

        assertEquals(FurnitureMatchType.PARTIAL, result.getMatchType());
        assertEquals(Collections.singletonList("futureConstraint"), result.getUnmetConstraints());
    }

    @Test
    void match_shouldRejectUnknownNonRelaxableConstraint() {
        FurnitureAssistantRequirements request = new FurnitureAssistantRequirements();
        request.setCategory("sofa");
        request.getNonRelaxableConstraints().add("futureConstraint");
        FurnitureProductCandidate candidate = candidate(1001L, 2001L, 500000, 10,
                "sofa", "gray", "fabric", 3, 2000, true);

        assertTrue(matcher.match(request, Collections.singletonList(candidate), 1).isEmpty());
    }

    @Test
    void match_shouldTreatPreferredFeaturesAsSoftUnlessMetadataMakesThemMandatory() {
        FurnitureAssistantRequirements soft = new FurnitureAssistantRequirements();
        soft.setCategory("sofa");
        soft.setPreferredFeatures(Collections.singletonList("rounded-edges"));
        FurnitureProductCandidate missing = detailedCandidate(1001L, 2001L, 500000, 100,
                "gray", Collections.emptyList(), Collections.emptyList());
        FurnitureProductCandidate matching = detailedCandidate(1002L, 2002L, 500000, 1,
                "gray", Collections.emptyList(), Collections.singletonList("rounded-edges"));

        List<FurnitureCandidateMatch> softResult = matcher.match(soft, Arrays.asList(missing, matching), 2);

        assertEquals(Long.valueOf(2002L), softResult.get(0).getCandidate().getSku().getId());
        assertEquals(FurnitureMatchType.EXACT, softResult.get(1).getMatchType());
        assertTrue(softResult.get(1).getUnmetConstraints().isEmpty());

        FurnitureAssistantRequirements mandatory = new FurnitureAssistantRequirements();
        mandatory.setCategory("sofa");
        mandatory.setPreferredFeatures(Collections.singletonList("rounded-edges"));
        mandatory.getHardConstraints().add("preferredFeatures");
        mandatory.getNonRelaxableConstraints().add("preferredFeatures");

        assertTrue(matcher.match(mandatory, Collections.singletonList(missing), 1).isEmpty());
    }

    @Test
    void match_shouldUsePreferencesThenStockPriceAndSkuIdForDeterministicOrder() {
        FurnitureAssistantRequirements request = new FurnitureAssistantRequirements();
        request.setCategory("sofa");
        request.setColors(Collections.singletonList("cream"));
        request.setStyles(Collections.singletonList("modern"));
        request.setRoomTypes(Collections.singletonList("living-room"));
        request.setPreferredFeatures(Collections.singletonList("rounded-edges"));

        FurnitureProductCandidate fewerPreferences = detailedCandidate(1001L, 2001L, 500000, 100,
                "gray", Collections.singletonList("classic"), Collections.emptyList());
        FurnitureProductCandidate lowStock = detailedCandidate(1002L, 2002L, 700000, 5,
                "cream", Collections.singletonList("modern"), Collections.singletonList("rounded-edges"));
        FurnitureProductCandidate lowerPrice = detailedCandidate(1003L, 2003L, 600000, 10,
                "cream", Collections.singletonList("modern"), Collections.singletonList("rounded-edges"));
        FurnitureProductCandidate lowerSkuId = detailedCandidate(1004L, 2000L, 600000, 10,
                "cream", Collections.singletonList("modern"), Collections.singletonList("rounded-edges"));

        List<FurnitureCandidateMatch> result = matcher.match(request,
                Arrays.asList(fewerPreferences, lowStock, lowerPrice, lowerSkuId), 4);

        assertEquals(Arrays.asList(2000L, 2003L, 2002L, 2001L), Arrays.asList(
                result.get(0).getCandidate().getSku().getId(),
                result.get(1).getCandidate().getSku().getId(),
                result.get(2).getCandidate().getSku().getId(),
                result.get(3).getCandidate().getSku().getId()));
        assertTrue(result.get(0).getCoverageScore() > result.get(3).getCoverageScore());
    }

    private FurnitureAssistantRequirements sofaRequest(int budget, int width, int seats, String material) {
        FurnitureAssistantRequirements value = new FurnitureAssistantRequirements();
        value.setCategory("sofa");
        value.setBudgetMax(BigDecimal.valueOf(budget));
        value.setMaxWidthMm(width);
        value.setSeatCount(seats);
        value.setMaterials(Collections.singletonList(material));
        value.setHardConstraints(new LinkedHashSet<>(
                Arrays.asList("budgetMax", "maxWidthMm", "seatCount", "materials")));
        return value;
    }

    private FurnitureProductCandidate candidate(long spuId, long skuId, int priceFen, int stock,
                                                 String category, String color, String material,
                                                 int seats, int widthMm, boolean mapped) {
        FurnitureSkuSearchDO projection = FurnitureSkuSearchDO.builder()
                .spuId(spuId).skuId(skuId).categoryCode(category).colorCode(color)
                .materialCodes(Collections.singletonList(material)).seatCount(seats)
                .widthMm(widthMm).build();
        return candidate(projection, priceFen, stock, mapped);
    }

    private FurnitureProductCandidate detailedCandidate(long spuId, long skuId, int priceFen, int stock,
                                                        String color, List<String> styles, List<String> features) {
        FurnitureSkuSearchDO projection = FurnitureSkuSearchDO.builder()
                .spuId(spuId).skuId(skuId).categoryCode("sofa").colorCode(color)
                .styleCodes(styles).roomTypeCodes(Collections.singletonList("living-room"))
                .featureCodes(features).build();
        return candidate(projection, priceFen, stock, true);
    }

    private FurnitureProductCandidate dimensionedCandidate(long spuId, long skuId, int priceFen, int stock,
                                                           int widthMm, int depthMm) {
        FurnitureSkuSearchDO projection = FurnitureSkuSearchDO.builder()
                .spuId(spuId).skuId(skuId).categoryCode("sofa")
                .widthMm(widthMm).depthMm(depthMm).build();
        return candidate(projection, priceFen, stock, true);
    }

    private FurnitureProductCandidate candidate(FurnitureSkuSearchDO projection, int priceFen, int stock,
                                                 boolean mapped) {
        ProductSkuDO sku = ProductSkuDO.builder().id(projection.getSkuId()).spuId(projection.getSpuId())
                .price(priceFen).build();
        ProductSpuDO spu = ProductSpuDO.builder().id(projection.getSpuId())
                .name("Candidate " + projection.getSpuId()).build();
        return new FurnitureProductCandidate(projection, sku, spu, BigDecimal.valueOf(stock), mapped);
    }
}
