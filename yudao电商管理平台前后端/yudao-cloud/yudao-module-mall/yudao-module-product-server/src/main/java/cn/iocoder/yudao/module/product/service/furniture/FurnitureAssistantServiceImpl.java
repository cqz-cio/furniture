package cn.iocoder.yudao.module.product.service.furniture;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatReqVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FurnitureAssistantServiceImpl implements FurnitureAssistantService {

    private static final int RECOMMENDATION_LIMIT = 3;
    private static final int SEARCH_LIMIT = 10;
    private static final List<String> PRODUCT_KEYWORDS = Arrays.asList(
            "sofa", "bed", "table", "chair", "lighting", "lamp", "cabinet", "desk", "dresser");
    private static final Pattern MAX_PRICE_PATTERN = Pattern.compile(
            "(?:under|below|less than|<=|<)\\s*\\$?([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);

    private final ProductSpuService productSpuService;
    private final FurnitureAssistantKnowledgeService knowledgeService;

    public FurnitureAssistantServiceImpl(ProductSpuService productSpuService,
                                         FurnitureAssistantKnowledgeService knowledgeService) {
        this.productSpuService = productSpuService;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public FurnitureAssistantChatRespVO chat(FurnitureAssistantChatReqVO reqVO) {
        String message = reqVO.getMessage().trim();
        List<FurnitureAssistantKnowledgeMatch> knowledgeMatches = knowledgeService.search(message);
        String keyword = extractKeyword(message);
        BigDecimal maxPrice = extractMaxPrice(message).orElse(null);
        boolean shouldSearchProducts = shouldSearchProducts(message, keyword, knowledgeMatches);

        List<ProductSpuDO> spus = shouldSearchProducts ? searchSpus(keyword) : Collections.emptyList();
        List<ProductSpuDO> recommendedSpus = spus.stream()
                .filter(spu -> maxPrice == null || toYuan(spu.getPrice()).compareTo(maxPrice) <= 0)
                .limit(RECOMMENDATION_LIMIT)
                .collect(Collectors.toList());

        FurnitureAssistantChatRespVO respVO = new FurnitureAssistantChatRespVO();
        respVO.setAnswer(buildAnswer(message, recommendedSpus.size(), knowledgeMatches));
        respVO.setProducts(recommendedSpus.stream()
                .map(spu -> toProduct(spu, message))
                .collect(Collectors.toList()));
        respVO.setSources(buildSources(shouldSearchProducts, knowledgeMatches));
        return respVO;
    }

    private List<ProductSpuDO> searchSpus(String keyword) {
        AppProductSpuPageReqVO pageReqVO = new AppProductSpuPageReqVO();
        pageReqVO.setKeyword(keyword);
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(SEARCH_LIMIT);
        PageResult<ProductSpuDO> pageResult = productSpuService.getSpuPage(pageReqVO);
        return pageResult == null || pageResult.getList() == null ? Collections.emptyList() : pageResult.getList();
    }

    private boolean shouldSearchProducts(String message, String keyword, List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        return PRODUCT_KEYWORDS.contains(keyword) || knowledgeMatches.isEmpty() || extractMaxPrice(message).isPresent();
    }

    private String extractKeyword(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return PRODUCT_KEYWORDS.stream()
                .filter(normalized::contains)
                .findFirst()
                .orElse(message);
    }

    private Optional<BigDecimal> extractMaxPrice(String message) {
        Matcher matcher = MAX_PRICE_PATTERN.matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(matcher.group(1)));
    }

    private String buildAnswer(String message, int count, List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        String knowledgeSummary = buildKnowledgeSummary(knowledgeMatches);
        if (count == 0) {
            if (!knowledgeSummary.isEmpty()) {
                return knowledgeSummary;
            }
            return "I could not find matching live products for \"" + message + "\" yet. Try a broader room, style or category.";
        }
        String productAnswer = "I found " + Math.min(count, RECOMMENDATION_LIMIT)
                + " live furniture products for \"" + message + "\" from the current catalog.";
        return knowledgeSummary.isEmpty() ? productAnswer : productAnswer + " " + knowledgeSummary;
    }

    private String buildKnowledgeSummary(List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        return knowledgeMatches.stream()
                .map(FurnitureAssistantKnowledgeMatch::getContent)
                .collect(Collectors.joining(" "));
    }

    private FurnitureAssistantChatRespVO.Product toProduct(ProductSpuDO spu, String message) {
        FurnitureAssistantChatRespVO.Product product = new FurnitureAssistantChatRespVO.Product();
        product.setId(spu.getId());
        product.setSkuId(spu.getId());
        product.setName(spu.getName());
        product.setSubtitle(StrUtil.blankToDefault(spu.getIntroduction(), ""));
        product.setPrice(toYuan(spu.getPrice()));
        product.setMarketPrice(toYuan(spu.getMarketPrice()));
        product.setStock(spu.getStock());
        product.setCover(spu.getPicUrl());
        product.setReason("Matched against your request: " + message + ".");
        product.setDetailUrl("/sofa-pdp?id=" + spu.getId());
        return product;
    }

    private BigDecimal toYuan(Integer fen) {
        if (fen == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(fen).movePointLeft(2).stripTrailingZeros();
    }

    private FurnitureAssistantChatRespVO.Source source(String type, String name) {
        FurnitureAssistantChatRespVO.Source source = new FurnitureAssistantChatRespVO.Source();
        source.setType(type);
        source.setName(name);
        return source;
    }

    private List<FurnitureAssistantChatRespVO.Source> buildSources(boolean includeProductSource,
                                                                   List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        List<FurnitureAssistantChatRespVO.Source> sources = new ArrayList<>();
        if (includeProductSource) {
            sources.add(source("product-api", "Yudao Product SPU"));
        }
        knowledgeMatches.stream()
                .map(match -> source(match.getType(), match.getName()))
                .forEach(sources::add);
        return sources;
    }

}
