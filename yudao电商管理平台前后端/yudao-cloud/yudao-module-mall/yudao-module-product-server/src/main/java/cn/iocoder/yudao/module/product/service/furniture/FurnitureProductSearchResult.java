package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureCandidateMatch;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureMatchType;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureProductCandidate;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class FurnitureProductSearchResult {

    private FurnitureMatchType matchType;
    private List<String> matchedConstraints;
    private List<String> unmetConstraints;
    private List<FurnitureAssistantChatRespVO.Product> products;

    public static FurnitureProductSearchResult empty() {
        return none();
    }

    public static FurnitureProductSearchResult of(List<FurnitureAssistantChatRespVO.Product> products) {
        List<FurnitureAssistantChatRespVO.Product> safeProducts = products == null
                ? Collections.emptyList() : products;
        return of(safeProducts.isEmpty() ? FurnitureMatchType.NONE : FurnitureMatchType.EXACT,
                Collections.emptyList(), Collections.emptyList(), safeProducts);
    }

    public static FurnitureProductSearchResult of(FurnitureMatchType matchType, List<String> matchedConstraints,
                                                  List<String> unmetConstraints,
                                                  List<FurnitureAssistantChatRespVO.Product> products) {
        FurnitureProductSearchResult result = new FurnitureProductSearchResult();
        result.setMatchType(matchType == null ? FurnitureMatchType.NONE : matchType);
        result.setMatchedConstraints(immutableCopy(matchedConstraints));
        result.setUnmetConstraints(immutableCopy(unmetConstraints));
        result.setProducts(products == null
                ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(products)));
        return result;
    }

    public static FurnitureProductSearchResult fromMatches(List<FurnitureCandidateMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return none();
        }
        FurnitureCandidateMatch winningMatch = matches.get(0);
        List<FurnitureProductCandidate> candidates = matches.stream()
                .map(FurnitureCandidateMatch::getCandidate).collect(Collectors.toList());
        return fromWinningMatch(winningMatch, candidates);
    }

    /**
     * Builds a result whose top-level match metadata always describes the winning match. Concrete candidates may
     * include sellable sibling variants which do not independently have the winner's exact/partial classification.
     */
    public static FurnitureProductSearchResult fromWinningMatch(
            FurnitureCandidateMatch winningMatch,
            List<FurnitureProductCandidate> candidates) {
        if (winningMatch == null || candidates == null || candidates.isEmpty()) {
            return none();
        }
        List<FurnitureAssistantChatRespVO.Product> products = candidates.stream()
                .map(candidate -> toProduct(candidate, winningMatch))
                .collect(Collectors.toList());
        return of(winningMatch.getMatchType(), winningMatch.getMatchedConstraints(),
                winningMatch.getUnmetConstraints(), products);
    }

    public static FurnitureProductSearchResult none() {
        return of(FurnitureMatchType.NONE, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList());
    }

    private static FurnitureAssistantChatRespVO.Product toProduct(
            FurnitureProductCandidate candidate,
            FurnitureCandidateMatch winningMatch) {
        FurnitureAssistantChatRespVO.Product product = new FurnitureAssistantChatRespVO.Product();
        product.setId(candidate.getSpu().getId());
        product.setSkuId(candidate.getSku().getId());
        product.setSkuProperties(toSkuProperties(candidate.getSku().getProperties()));
        product.setName(candidate.getSpu().getName());
        product.setSubtitle(candidate.getSpu().getIntroduction() == null ? "" : candidate.getSpu().getIntroduction());
        product.setPrice(toYuan(candidate.getSku().getPrice()));
        product.setMarketPrice(toYuan(candidate.getSku().getMarketPrice()));
        product.setStock(candidate.getSellableStock().intValue());
        product.setCover(candidate.getSku().getPicUrl() == null
                ? candidate.getSpu().getPicUrl() : candidate.getSku().getPicUrl());
        product.setReason(toReason(winningMatch));
        product.setDetailUrl("/sofa-pdp?id=" + candidate.getSpu().getId());
        return product;
    }

    private static List<String> toSkuProperties(
            List<cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO.Property> properties) {
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyList();
        }
        return properties.stream().map(property -> {
                    String name = property.getPropertyName();
                    String value = property.getValueName();
                    if (name == null || name.trim().isEmpty()) return value;
                    if (value == null || value.trim().isEmpty()) return name;
                    return name + ": " + value;
                })
                .filter(value -> value != null && !value.trim().isEmpty())
                .collect(Collectors.toList());
    }

    private static String toReason(FurnitureCandidateMatch match) {
        if (match.getMatchType() == FurnitureMatchType.PARTIAL) {
            return "Partial match; unmet constraints: " + String.join(", ", match.getUnmetConstraints());
        }
        return match.getMatchedConstraints().isEmpty()
                ? "Furniture recommendation"
                : "Matched constraints: " + String.join(", ", match.getMatchedConstraints());
    }

    private static BigDecimal toYuan(Integer fen) {
        return fen == null ? BigDecimal.ZERO : BigDecimal.valueOf(fen).movePointLeft(2).stripTrailingZeros();
    }

    private static List<String> immutableCopy(List<String> values) {
        return values == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

}
