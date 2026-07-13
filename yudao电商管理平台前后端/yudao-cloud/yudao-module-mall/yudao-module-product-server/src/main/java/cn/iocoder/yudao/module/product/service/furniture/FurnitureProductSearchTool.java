package cn.iocoder.yudao.module.product.service.furniture;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.erp.api.integration.MallErpProductApi;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockRequestDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.furniture.FurnitureSkuSearchDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.product.service.furniture.catalog.FurnitureSkuSearchService;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantRequirements;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureRequirementNormalizer;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureCandidateMatch;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureProductCandidate;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureProductMatcher;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FurnitureProductSearchTool {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 10;
    private static final Pattern BUDGET_INTENT = Pattern.compile(
            "(?:under|below|less than|budget|<=|<|\u4ee5\u5185|\u4ee5\u4e0b|\u9884\u7b97|\u4e0d\u8d85\u8fc7)",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> PRODUCT_TERMS = Collections.unmodifiableList(Arrays.asList(
            "sofa", "chair", "table", "bed", "desk", "wardrobe", "cabinet", "rug", "lamp", "lighting",
            "\u6c99\u53d1", "\u6905", "\u9910\u684c", "\u8336\u51e0", "\u5e8a", "\u4e66\u684c", "\u8863\u67dc", "\u67dc", "\u5730\u6bef", "\u706f"));

    private final FurnitureSkuSearchService projectionService;
    private final ProductSkuService productSkuService;
    private final ProductSpuService productSpuService;
    private final MallErpProductApi mallErpProductApi;
    private final FurnitureRequirementNormalizer requirementNormalizer;
    private final FurnitureProductMatcher matcher = new FurnitureProductMatcher();

    public FurnitureProductSearchTool(FurnitureSkuSearchService projectionService,
                                      ProductSkuService productSkuService,
                                      ProductSpuService productSpuService,
                                      MallErpProductApi mallErpProductApi,
                                      FurnitureRequirementNormalizer requirementNormalizer) {
        this.projectionService = projectionService;
        this.productSkuService = productSkuService;
        this.productSpuService = productSpuService;
        this.mallErpProductApi = mallErpProductApi;
        this.requirementNormalizer = requirementNormalizer;
    }

    /** Compatibility bridge until Task 5 supplies normalized conversation requirements directly. */
    public FurnitureProductSearchResult searchForAssistant(String message) {
        FurnitureAssistantRequirements requirements = new FurnitureAssistantRequirements();
        requirementNormalizer.normalize(message).applyTo(requirements);
        return searchProducts(FurnitureProductSearchRequest.from(message, requirements, DEFAULT_LIMIT));
    }

    /** Compatibility bridge for the pre-Task-5 assistant service. */
    public boolean shouldSearchProducts(String message, List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        return shouldSearchProducts(message, null, knowledgeMatches);
    }

    public boolean shouldSearchProducts(String message, FurnitureAssistantRequirements requirements,
                                        List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        if (requirements != null && StrUtil.isNotBlank(requirements.getCategory())) {
            return true;
        }
        String normalized = StrUtil.blankToDefault(message, "").toLowerCase(Locale.ROOT);
        return PRODUCT_TERMS.stream().anyMatch(normalized::contains) || BUDGET_INTENT.matcher(normalized).find();
    }

    public FurnitureProductSearchResult searchProducts(FurnitureProductSearchRequest request) {
        FurnitureAssistantRequirements requirements = request == null ? null : request.getRequirements();
        if (requirements == null || StrUtil.isBlank(requirements.getCategory())) {
            return FurnitureProductSearchResult.none();
        }
        int limit = normalizeLimit(request.getLimit());
        List<FurnitureSkuSearchDO> projections = safeList(
                projectionService.getByCategory(requirements.getCategory()));
        if (projections.isEmpty()) {
            return FurnitureProductSearchResult.none();
        }

        List<Long> skuIds = distinctNonNull(projections.stream()
                .map(FurnitureSkuSearchDO::getSkuId).collect(Collectors.toList()));
        Map<Long, ProductSkuDO> skus = convertMap(productSkuService.getSkuList(skuIds), ProductSkuDO::getId);
        List<Long> spuIds = distinctNonNull(skus.values().stream()
                .map(ProductSkuDO::getSpuId).collect(Collectors.toList()));
        Map<Long, ProductSpuDO> spus = convertMap(productSpuService.getSpuList(spuIds), ProductSpuDO::getId);
        CommonResult<List<MallErpStockDTO>> stockResponse = validateStock(skuIds);
        List<FurnitureProductCandidate> candidates = buildCandidates(projections, skus, spus, stockResponse);
        if (candidates.isEmpty()) {
            return FurnitureProductSearchResult.none();
        }

        List<FurnitureCandidateMatch> matches = matcher.match(requirements, candidates,
                request.isIncludeAllVariants() ? 1 : limit);
        if (matches.isEmpty()) {
            return FurnitureProductSearchResult.none();
        }
        if (request.isIncludeAllVariants()) {
            FurnitureCandidateMatch winningMatch = matches.get(0);
            return FurnitureProductSearchResult.fromWinningMatch(
                    winningMatch, getSellableSiblings(candidates, winningMatch));
        }
        return FurnitureProductSearchResult.fromMatches(matches);
    }

    private CommonResult<List<MallErpStockDTO>> validateStock(List<Long> skuIds) {
        List<MallErpStockRequestDTO> requests = skuIds.stream()
                .map(id -> new MallErpStockRequestDTO().setMallSkuId(id).setCount(BigDecimal.ONE))
                .collect(Collectors.toList());
        try {
            return mallErpProductApi.validateSellableStock(requests);
        } catch (RuntimeException ex) {
            log.warn("Furniture search ERP validation failed reason=ERP_UNAVAILABLE skuIds={}", skuIds, ex);
            return null;
        }
    }

    private List<FurnitureProductCandidate> buildCandidates(List<FurnitureSkuSearchDO> projections,
                                                             Map<Long, ProductSkuDO> skus,
                                                             Map<Long, ProductSpuDO> spus,
                                                             CommonResult<List<MallErpStockDTO>> stockResponse) {
        boolean responseAvailable = stockResponse != null && stockResponse.isSuccess()
                && stockResponse.getData() != null;
        StockIndex stockIndex = responseAvailable
                ? indexStocks(stockResponse.getData()) : StockIndex.empty();
        List<FurnitureProductCandidate> candidates = new ArrayList<>();
        for (FurnitureSkuSearchDO projection : projections) {
            Long skuId = projection.getSkuId();
            ProductSkuDO sku = skus.get(skuId);
            if (sku == null || !Objects.equals(projection.getSpuId(), sku.getSpuId())) {
                exclude("SKU_MISSING", projection);
                continue;
            }
            ProductSpuDO spu = spus.get(sku.getSpuId());
            if (spu == null || !ProductSpuStatusEnum.isEnable(spu.getStatus())) {
                exclude("SPU_DISABLED", projection);
                continue;
            }
            if (!responseAvailable) {
                exclude("ERP_UNAVAILABLE", projection);
                continue;
            }
            if (stockIndex.duplicateSkuIds.contains(skuId)) {
                exclude("ERP_UNAVAILABLE", projection);
                continue;
            }
            MallErpStockDTO stock = stockIndex.stocks.get(skuId);
            if (stock == null || stock.getErpProductId() == null) {
                exclude("ERP_UNMAPPED", projection);
                continue;
            }
            if (stock.getSellableStock() == null || stock.getSellableStock().compareTo(BigDecimal.ZERO) <= 0) {
                exclude("ERP_ZERO_STOCK", projection);
                continue;
            }
            if (!Boolean.TRUE.equals(stock.getAvailable())) {
                exclude("ERP_UNAVAILABLE", projection);
                continue;
            }
            candidates.add(new FurnitureProductCandidate(projection, sku, spu,
                    stock.getSellableStock(), true));
        }
        return candidates;
    }

    private List<FurnitureProductCandidate> getSellableSiblings(List<FurnitureProductCandidate> candidates,
                                                                FurnitureCandidateMatch winningMatch) {
        Long selectedSpuId = winningMatch.getCandidate().getSpu().getId();
        Long winningSkuId = winningMatch.getCandidate().getSku().getId();
        return candidates.stream()
                .filter(candidate -> selectedSpuId.equals(candidate.getSpu().getId()))
                .sorted(java.util.Comparator
                        .comparing((FurnitureProductCandidate candidate) ->
                                !winningSkuId.equals(candidate.getSku().getId()))
                        .thenComparing(candidate -> candidate.getSku().getId()))
                .collect(Collectors.toList());
    }

    private StockIndex indexStocks(List<MallErpStockDTO> values) {
        Map<Long, MallErpStockDTO> stocks = new LinkedHashMap<>();
        Set<Long> duplicateSkuIds = new LinkedHashSet<>();
        for (MallErpStockDTO value : values) {
            if (value == null || value.getMallSkuId() == null) {
                continue;
            }
            if (stocks.putIfAbsent(value.getMallSkuId(), value) != null) {
                duplicateSkuIds.add(value.getMallSkuId());
            }
        }
        return new StockIndex(stocks, duplicateSkuIds);
    }

    private void exclude(String reason, FurnitureSkuSearchDO projection) {
        log.info("Furniture search candidate excluded reason={} skuId={} spuId={}",
                reason, projection.getSkuId(), projection.getSpuId());
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    private static <T> Map<Long, T> convertMap(Collection<T> values, Function<T, Long> keyFunction) {
        Map<Long, T> result = new LinkedHashMap<>();
        if (values == null) {
            return result;
        }
        for (T value : values) {
            if (value != null) {
                Long key = keyFunction.apply(value);
                if (key != null) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    private static List<Long> distinctNonNull(List<Long> values) {
        Set<Long> result = new LinkedHashSet<>();
        for (Long value : values) {
            if (value != null) {
                result.add(value);
            }
        }
        return new ArrayList<>(result);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private static final class StockIndex {
        private final Map<Long, MallErpStockDTO> stocks;
        private final Set<Long> duplicateSkuIds;

        private StockIndex(Map<Long, MallErpStockDTO> stocks, Set<Long> duplicateSkuIds) {
            this.stocks = stocks;
            this.duplicateSkuIds = duplicateSkuIds;
        }

        private static StockIndex empty() {
            return new StockIndex(Collections.emptyMap(), Collections.emptySet());
        }
    }
}
