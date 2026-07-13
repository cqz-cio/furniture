package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureCandidateMatch;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureMatchType;
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
        FurnitureCandidateMatch first = matches.get(0);
        List<FurnitureAssistantChatRespVO.Product> products = matches.stream()
                .map(FurnitureProductSearchResult::toProduct)
                .collect(Collectors.toList());
        return of(first.getMatchType(), first.getMatchedConstraints(), first.getUnmetConstraints(), products);
    }

    public static FurnitureProductSearchResult none() {
        return of(FurnitureMatchType.NONE, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList());
    }

    private static FurnitureAssistantChatRespVO.Product toProduct(FurnitureCandidateMatch match) {
        FurnitureAssistantChatRespVO.Product product = new FurnitureAssistantChatRespVO.Product();
        product.setId(match.getCandidate().getSpu().getId());
        product.setSkuId(match.getCandidate().getSku().getId());
        product.setName(match.getCandidate().getSpu().getName());
        product.setSubtitle(match.getCandidate().getSpu().getIntroduction() == null
                ? "" : match.getCandidate().getSpu().getIntroduction());
        product.setPrice(toYuan(match.getCandidate().getSku().getPrice()));
        product.setMarketPrice(toYuan(match.getCandidate().getSku().getMarketPrice()));
        product.setStock(match.getCandidate().getSellableStock().intValue());
        product.setCover(match.getCandidate().getSku().getPicUrl() == null
                ? match.getCandidate().getSpu().getPicUrl() : match.getCandidate().getSku().getPicUrl());
        product.setReason(toReason(match));
        product.setDetailUrl("/sofa-pdp?id=" + match.getCandidate().getSpu().getId());
        return product;
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
