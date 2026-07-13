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
    private static final Map<String, String> CATEGORY_ALIASES = categoryAliases();

    private final FurnitureSkuSearchService projectionService;
    private final ProductSkuService productSkuService;
    private final ProductSpuService productSpuService;
    private final MallErpProductApi mallErpProductApi;
    private final FurnitureProductMatcher matcher = new FurnitureProductMatcher();

    public FurnitureProductSearchTool(FurnitureSkuSearchService projectionService,
                                      ProductSkuService productSkuService,
                                      ProductSpuService productSpuService,
                                      MallErpProductApi mallErpProductApi) {
        this.projectionService = projectionService;
        this.productSkuService = productSkuService;
        this.productSpuService = productSpuService;
        this.mallErpProductApi = mallErpProductApi;
    }

    /** Compatibility bridge until Task 5 supplies normalized conversation requirements directly. */
    public FurnitureProductSearchResult searchForAssistant(String message) {
        FurnitureAssistantRequirements requirements = new FurnitureAssistantRequirements();
        requirements.setCategory(extractCategory(message));
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

        int matchLimit = request.isIncludeAllVariants() ? candidates.size() : limit;
        List<FurnitureCandidateMatch> matches = matcher.match(requirements, candidates, matchLimit);
        if (request.isIncludeAllVariants()) {
            matches = keepWinningSpu(matches);
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
        Map<Long, MallErpStockDTO> stocks = responseAvailable
                ? convertMap(stockResponse.getData(), MallErpStockDTO::getMallSkuId) : Collections.emptyMap();
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
            MallErpStockDTO stock = stocks.get(skuId);
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

    private List<FurnitureCandidateMatch> keepWinningSpu(List<FurnitureCandidateMatch> matches) {
        if (matches.isEmpty()) {
            return matches;
        }
        Long selectedSpuId = matches.get(0).getCandidate().getSpu().getId();
        return matches.stream()
                .filter(match -> selectedSpuId.equals(match.getCandidate().getSpu().getId()))
                .collect(Collectors.toList());
    }

    private void exclude(String reason, FurnitureSkuSearchDO projection) {
        log.info("Furniture search candidate excluded reason={} skuId={} spuId={}",
                reason, projection.getSkuId(), projection.getSpuId());
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    private String extractCategory(String message) {
        String normalized = StrUtil.blankToDefault(message, "").toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> alias : CATEGORY_ALIASES.entrySet()) {
            if (normalized.contains(alias.getKey())) {
                return alias.getValue();
            }
        }
        return null;
    }

    private static Map<String, String> categoryAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("coffee table", "coffee-table");
        aliases.put("dining table", "dining-table");
        aliases.put("side table", "side-table");
        aliases.put("table lamp", "lighting");
        aliases.put("floor lamp", "lighting");
        aliases.put("nightstand", "side-table");
        aliases.put("sofa", "sofa");
        aliases.put("rug", "rug");
        aliases.put("bed", "bed");
        aliases.put("desk", "desk");
        aliases.put("wardrobe", "wardrobe");
        aliases.put("chair", "single-chair");
        aliases.put("\u5e8a\u5934\u67dc", "side-table");
        aliases.put("\u9910\u684c", "dining-table");
        aliases.put("\u8336\u51e0", "coffee-table");
        aliases.put("\u8fb9\u51e0", "side-table");
        aliases.put("\u6c99\u53d1", "sofa");
        aliases.put("\u5730\u6bef", "rug");
        aliases.put("\u8863\u67dc", "wardrobe");
        aliases.put("\u4e66\u684c", "desk");
        aliases.put("\u706f", "lighting");
        aliases.put("\u5e8a", "bed");
        aliases.put("\u6905", "single-chair");
        return Collections.unmodifiableMap(aliases);
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
}
